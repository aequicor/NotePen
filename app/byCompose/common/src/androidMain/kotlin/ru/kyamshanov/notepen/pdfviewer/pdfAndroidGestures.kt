package ru.kyamshanov.notepen.pdfviewer

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.sqrt

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
                            val pan = Offset(centroid.x - prevC.x, centroid.y - prevC.y)
                            val zoom = if (span > 0f) span / prevSpan else 1f
                            if (zoom != 1f) state.zoomBy(zoom, centroid)
                            if (pan != Offset.Zero) state.panBy(pan)
                        }
                        prevCentroid = centroid
                        prevSpan = span
                        event.changes.forEach { it.consume() }
                    }
                    pinchActive -> {
                        // Хвост пинча: 0 или 1 палец остался после
                        // отрыва. Consume всё, чтобы scrollable не подхватил
                        // и не сдвинул страницу.
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
