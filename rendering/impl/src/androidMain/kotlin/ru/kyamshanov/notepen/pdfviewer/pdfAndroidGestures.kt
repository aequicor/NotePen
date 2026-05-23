package ru.kyamshanov.notepen.pdfviewer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Pointer-input Android-вьювера: два-пальцевый pinch с anchor в centroid.
 *
 * - Перехват на [PointerEventPass.Initial] — иначе `Modifier.scrollable`
 *   на том же контейнере или per-page stylus-handler внутри
 *   [DrawablePdfPage] поглотят жест раньше нас.
 * - Жест применяется только при `pressed.size >= 2`. Чистый
 *   одно-пальцевый drag (без пинча в анамнезе) проходит дальше —
 *   попадает в `Modifier.scrollable` (нативный fling) или в
 *   stylus-handler рисования.
 * - Centroid + ratio спанов даёт zoom-to-pinch-centre через
 *   [PdfViewerMath.zoomAroundFocus] (внутри [PdfViewerState.zoomBy]).
 * - Параллельный pan центроида в [PdfViewerState.panBy] — палец/центр
 *   жеста двигается вместе со страницей.
 *
 * Особый случай — **хвост пинча**: пока хоть один из пальцев пинча ещё
 * прижат, событие нужно потреблять. Иначе `Modifier.scrollable` увидит
 * скачок position от centroid'а к позиции оставшегося пальца и сдвинет
 * страницу — это даёт визуальное "съезжание после отпускания".
 *
 * Никакого временного `graphicsLayer` или промежуточного `gestureScale`:
 * [PdfViewerState] перерисовывает страницы сразу после изменения [zoom],
 * битмап показывается растянутым из предыдущего масштаба до прихода
 * нового рендера. Это и устраняет "скачок" при пинче.
 *
 * Pinch применяется через [PdfViewerState.pinchGestureUpdate] — обновляет
 * только transient `gestureScale` / `gestureTranslation`, которые
 * накладываются через `Modifier.graphicsLayer` на корень SubcomposeLayout.
 * SubcomposeLayout НЕ пересчитывает layout, PDF-битмапы НЕ ре-стрейчатся,
 * ink-кэш НЕ ре-растеризуется — это и устраняло лаги при резком пинче.
 *
 * Bake в `zoom` / `pan` происходит [PdfViewerState.commitPinchGesture] в
 * момент, когда пинч завершается (хвост, осталось <2 пальцев). Compose
 * батчит все snapshot-write'ы в один кадр, поэтому identity-layer
 * приходит ровно тогда же, когда layout пересчитывается на новом `zoom` —
 * без визуального скачка.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.pdfAndroidPointerInput(state: PdfViewerState): Modifier =
    this.pointerInput(state) {
        awaitPointerEventScope {
            var prevCentroid: Offset? = null
            var prevSpan = 0f
            var pinchActive = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed: List<PointerInputChange> = event.changes.filter { it.pressed }
                when {
                    pressed.size >= 2 -> {
                        pinchActive = true
                        val p0 = pressed[0].position
                        val p1 = pressed[1].position
                        val centroid = Offset((p0.x + p1.x) * 0.5f, (p0.y + p1.y) * 0.5f)
                        val dx = p1.x - p0.x
                        val dy = p1.y - p0.y
                        val span = sqrt(dx * dx + dy * dy)
                        val prevC = prevCentroid
                        if (prevC != null && prevSpan > 0f) {
                            val factor = if (span > 0f) span / prevSpan else 1f
                            state.pinchGestureUpdate(prevC, centroid, factor)
                        }
                        prevCentroid = centroid
                        prevSpan = span
                        event.changes.forEach { it.consume() }
                    }
                    pinchActive -> {
                        // Хвост пинча: 0 или 1 палец остался после
                        // отрыва. Commit transient gesture-state в zoom/pan
                        // (идемпотентно), consume всё, чтобы scrollable не
                        // подхватил и не сдвинул страницу.
                        state.commitPinchGesture()
                        event.changes.forEach { it.consume() }
                        prevCentroid = null
                        prevSpan = 0f
                        if (pressed.isEmpty()) pinchActive = false
                    }
                    else -> {
                        prevCentroid = null
                        prevSpan = 0f
                    }
                }
            }
        }
    }

/** Держит текущую fling-корутину, чтобы новое касание могло её отменить. */
internal class PdfFlingJobHolder {
    var job: Job? = null
}

/** Обнуляет запрещённые текущим [ScrollMode] оси одно-пальцевого скролла. */
private fun Offset.maskByScrollMode(mode: ScrollMode): Offset = when (mode) {
    ScrollMode.BOTH -> this
    ScrollMode.VERTICAL -> Offset(0f, y)
    ScrollMode.NONE -> Offset.Zero
}

/**
 * Одно-пальцевый pan по ОБЕИМ осям (диагональ) + инерционный fling.
 *
 * Заменяет пару ортогональных `Modifier.scrollable`, каждый из которых
 * лочился на свою ось по pointer-slop и не давал двигать страницу по
 * диагонали. Здесь дельта пальца [PointerInputChange.positionChange]
 * применяется к обеим осям сразу через [PdfViewerState.panBy].
 *
 * Обработчик на [PointerEventPass.Main] и стоит ПЕРЕД `gestureModifier` в
 * цепочке (тот — inner, обрабатывает Main-pass раньше). Поэтому рисование /
 * лупа / магнифайер, если они «забрали» жест ([PointerInputChange.isConsumed]),
 * имеют приоритет — pan отступает. Если жест НЕ потреблён — drawing-слой от
 * него отказался: это либо свободный палец (инструмент неактивен / режим
 * стилуса), либо палец ЗА рамкой PDF при активном инструменте (рисуем внутри
 * страницы, скроллим снаружи — см. `MultiPageDrawingController.isInsidePdfPage`).
 * В обоих случаях pan допустим; гейтит только [PdfViewerState.scrollMode].
 *
 * Два пальца → отдаём жест pinch-обработчику [pdfAndroidPointerInput].
 *
 * Двойной тап (касание без смещения за slop, второе в пределах
 * double-tap-таймаута) переключает зум fit-width ↔ приближение под пальцем
 * ([PdfViewerState.doubleTapZoom]). Детекция
 * встроена в этот же обработчик и НЕ потребляет down — отдельный
 * `detectTapGestures` потреблял бы первое касание, и pan начинал бы работать
 * лишь со второго нажатия.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.pdfSingleFingerPanInput(
    state: PdfViewerState,
    flingScope: CoroutineScope,
    flingHolder: PdfFlingJobHolder,
): Modifier =
    this.pointerInput(state) {
        // Нативная для Android физика fling'а (как у системного скролла);
        // PointerInputScope — это Density, поэтому передаём его сюда.
        val decaySpec = splineBasedDecay<Float>(this)
        val slop = viewConfiguration.touchSlop
        val doubleTapTimeoutMs = viewConfiguration.doubleTapTimeoutMillis
        // Сохраняются между жестами в пределах этого pointerInput-блока.
        var lastTapUptime = 0L
        var lastTapPos = Offset.Zero
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
            // Новое касание гасит текущий fling.
            flingHolder.job?.cancel()
            // Если палец «забран» рисованием/лупой (down.isConsumed), отступаем —
            // тогда тем же пальцем рисуют, и ни pan, ни double-tap не нужны.
            // Дойти сюда = drawing-слой отказался от жеста: свободный палец
            // (нет инструмента / режим стилуса) ЛИБО палец за рамкой PDF при
            // активном инструменте. В обоих случаях это «вьюверный» жест —
            // скроллим (гейтит только scrollMode) и ловим double-tap.
            if (down.isConsumed) return@awaitEachGesture
            val mode = state.scrollMode
            val tracker = VelocityTracker()
            // addPointerInputChange (не addPosition) дренит change.historical —
            // суб-кадровые сэмплы, которые Android батчит между кадрами. Без них
            // velocity на отрыве занижается, и инерционный fling гаснет заметно
            // раньше нативного скролла (системный VelocityTracker.addMovement
            // тоже учитывает все исторические сэмплы MotionEvent).
            tracker.addPointerInputChange(down)
            val pointerId = down.id
            var panning = false
            var movedBeyondSlop = false
            var lastChange = down
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                if (event.changes.count { it.pressed } >= 2) return@awaitEachGesture
                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                lastChange = change
                // Каждый сэмпл трекаемого пальца (включая historical и финальный
                // UP) — в трекер, ДО любых ранних выходов, иначе теряется хвост
                // скорости перед отрывом и fling недолетает.
                tracker.addPointerInputChange(change)
                if (!change.pressed) break
                if (change.isConsumed) return@awaitEachGesture
                if (!panning) {
                    // Слоп меряется по суммарному смещению от точки касания,
                    // а не по дельте кадра: иначе медленный drag (где каждая
                    // покадровая дельта < slop) не стартует вовсе — это и есть
                    // "мёртвая зона" мелкого скролла.
                    if ((change.position - down.position).getDistance() < slop) {
                        continue
                    }
                    movedBeyondSlop = true
                    // Скролл выключен — drag не панорамирует (зум и перемещение
                    // щипком остаются), но движение всё равно дисквалифицирует тап.
                    if (mode == ScrollMode.NONE) continue
                    panning = true
                }
                val delta = change.positionChange().maskByScrollMode(mode)
                if (delta != Offset.Zero) {
                    state.panBy(delta)
                    change.consume()
                }
            }
            // Касание без смещения — кандидат на (двойной) тап.
            if (!movedBeyondSlop) {
                val now = lastChange.uptimeMillis
                val pos = lastChange.position
                val isDoubleTap = now - lastTapUptime <= doubleTapTimeoutMs &&
                    (pos - lastTapPos).getDistance() <= slop * 2f
                if (isDoubleTap) {
                    state.doubleTapZoom(pos)
                    lastTapUptime = 0L
                } else {
                    lastTapUptime = now
                    lastTapPos = pos
                }
                return@awaitEachGesture
            }
            if (!panning) return@awaitEachGesture
            val velocity = tracker.calculateVelocity()
            val horizontalFling = mode == ScrollMode.BOTH
            flingHolder.job = flingScope.launch {
                coroutineScope {
                    if (horizontalFling) launch {
                        var last = 0f
                        Animatable(0f).animateDecay(velocity.x, decaySpec) {
                            val d = value - last
                            last = value
                            // На краю листа panBy — no-op; decay быстро затухает.
                            state.panBy(Offset(d, 0f))
                        }
                    }
                    launch {
                        var last = 0f
                        Animatable(0f).animateDecay(velocity.y, decaySpec) {
                            val d = value - last
                            last = value
                            state.panBy(Offset(0f, d))
                        }
                    }
                }
            }
        }
    }
