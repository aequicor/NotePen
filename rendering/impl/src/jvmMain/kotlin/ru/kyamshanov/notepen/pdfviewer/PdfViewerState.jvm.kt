package ru.kyamshanov.notepen.pdfviewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.roundToInt
import ru.kyamshanov.notepen.annotation.domain.model.PageExtent
import ru.kyamshanov.notepen.pdf.domain.model.PdfPageInfo

/**
 * Максимальное визуальное overscroll-смещение контента (px). И «перелёт» пальца
 * при drag, и накопление от колеса демпфируются к этому пределу — сколько ни
 * тяни/крути в край, контент не уедет дальше. Им же насыщается краевая тень.
 */
private const val OVERSCROLL_MAX_OFFSET_PX = 90f

/**
 * Жёсткость пружины overscroll (ω₀² = stiffness, ω₀ ≈ 10 рад/с).
 * При критическом демпфировании [OVERSCROLL_SPRING_DAMPING] = 2·ω₀ = 20
 * время оседания ≈ 4/ω₀ ≈ 0.4с — быстрый возврат без перелёта, как в Safari.
 */
private const val OVERSCROLL_SPRING_STIFFNESS = 100f

/**
 * Демпфирование пружины (2·ζ·ω₀, ζ = 1.0 → критически демпфированная).
 * Нет осцилляций — пружина возвращается к нулю за один проход.
 */
private const val OVERSCROLL_SPRING_DAMPING = 20f

/**
 * Коэффициент преобразования «непогашенного» scroll-delta (px) в скорость пружины
 * (px/с). Каждый scroll-тик у края даёт импульс скорости, а не скачок позиции:
 * скорость интегрируется плавно между кадрами → нет saw-tooth при нестабильном
 * потоке событий (главный источник дёргания). Значение подобрано так, чтобы при
 * среднем wheel-скролле (delta.y ≈ 1, WHEEL_SCROLL_PX = 60) равновесное смещение
 * было ≈ 40–60 px.
 */
private const val OVERSCROLL_IMPULSE_GAIN = 5f

/** Порог (px), ниже которого overscroll-смещение считается нулевым. */
private const val OVERSCROLL_EPS_PX = 0.5f

/** Порог скорости (px/с), ниже которого пружина считается остановившейся. */
private const val OVERSCROLL_VEL_EPS = 5f

/**
 * Desktop-реализация [PdfViewerState]: один `zoom: Float`, [pan] —
 * единая модель координат документа во вьюпорте (см. [PdfPagesLayout]
 * для описания координатных пространств).
 *
 * Аналог [androidx.compose.foundation.lazy.LazyListState], но с
 * поддержкой произвольного float-зума вокруг точки и без LazyColumn'а
 * под капотом — виртуализация делается через `SubcomposeLayout` в
 * `PdfPagesViewer.jvm.kt`.
 */
@Suppress("TooManyFunctions")
actual class PdfViewerState internal constructor(
    initialZoom: Float = 1f,
    initialPanX: Float = 0f,
    initialPanY: Float = 0f,
    initialPageIndex: Int = 0,
    initialPageOffsetPx: Int = 0,
) {

    /** Текущий зум (1.0 = 100%). Меняется через [zoomTo]. */
    actual var zoom: Float by mutableFloatStateOf(initialZoom.coerceIn(PdfViewerMath.MIN_ZOOM, PdfViewerMath.MAX_ZOOM))
        private set

    /** Текущий сдвиг документа во вьюпорте. */
    actual var pan: Offset by mutableStateOf(Offset(initialPanX, initialPanY))
        private set

    /** Размер вьюпорта в физических пикселях. Устанавливается из layout-pass. */
    var viewportSize: IntSize by mutableStateOf(IntSize.Zero)
        internal set

    /** Текущий список страниц документа. Устанавливается извне. */
    var pages: List<PdfPageInfo> by mutableStateOf(emptyList())
        internal set

    /**
     * Source of truth по [PageExtent] для страницы. Читается внутри
     * [derivedStateOf] при построении [layout], поэтому когда underlying
     * snapshot-state (`PdfDrawingState.extent`) меняется — layout
     * пересчитывается. По умолчанию возвращает [PageExtent.Pdf].
     */
    actual var pageExtentProvider: (Int) -> PageExtent by mutableStateOf({ PageExtent.Pdf })

    actual var scrollMode: ScrollMode by mutableStateOf(ScrollMode.BOTH)

    actual var fitWidthInsetStartPx: Float by mutableFloatStateOf(0f)
    actual var fitWidthInsetTopPx: Float by mutableFloatStateOf(0f)
    actual var fitWidthInsetEndPx: Float by mutableFloatStateOf(0f)

    /**
     * Transient множитель для активного Ctrl+wheel zoom-бёрста: применяется
     * к содержимому `SubcomposeLayout` через `Modifier.graphicsLayer` без
     * пересчёта layout'а и без перерисовки PDF-битмапов. `1f` вне жеста.
     *
     * Зачем: layout-pass + restretch огромных PDF-битмапов + (если есть
     * ink-кэш) ре-растеризация штрихов в новый off-screen битмап на КАЖДЫЙ
     * wheel-тик — это был источник лагов. Теперь `zoom` остаётся
     * «закоммиченным» во время бёрста, страницы GPU-трансформируются через
     * render node. По idle-таймеру в per-frame loop'е (см. `PdfPagesViewer.
     * jvm.kt`) [commitPinchGesture] «впекает» множитель и трансляцию в
     * `zoom` / `pan` атомарно, gestureScale → 1f.
     */
    actual var gestureScale: Float by mutableFloatStateOf(1f)
        internal set

    /** Transient трансляция layer'а для активного zoom-бёрста; см. [gestureScale]. */
    var gestureTranslation: Offset by mutableStateOf(Offset.Zero)
        internal set

    actual val basePageWidthPx: Float
        get() = viewportSize.width * BASE_PAGE_WIDTH_FRACTION

    actual val layout: PdfPagesLayout by derivedStateOf {
        val provider = pageExtentProvider
        PdfPagesLayout.build(
            pages = pages,
            basePageWidthPx = basePageWidthPx,
            extents = pages.indices.map { provider(it) },
            pageSpacingPx = 0f,
        )
    }

    private var pendingInitialPage: Int? = initialPageIndex.takeIf { it > 0 || initialPageOffsetPx > 0 }
    private var pendingInitialOffset: Int = initialPageOffsetPx
    private var pendingInitialScalePercent: Int? = null
    private var hasInitialCentered: Boolean = false

    internal fun applyPendingInitialScrollIfNeeded() {
        pendingInitialScalePercent?.let { sc ->
            if (viewportSize.width > 0) {
                setScalePercent(sc)
                pendingInitialScalePercent = null
            }
        }
        if (!hasInitialCentered && viewportSize.width > 0 && pages.isNotEmpty()) {
            // Центрируем по обеим осям: по X — всегда, по Y — когда документ
            // короче вьюпорта (одностраничный PDF / изображение), иначе верх
            // прижат к кромке.
            pan = centeredAndClamped(Offset.Zero)
            hasInitialCentered = true
        }
        val page = pendingInitialPage ?: return
        if (viewportSize.width <= 0 || pages.isEmpty()) return
        scrollToPage(page.coerceIn(0, pages.lastIndex), pendingInitialOffset)
        pendingInitialPage = null
        pendingInitialOffset = 0
    }

    actual fun applyInitialState(scalePercent: Int, pageIndex: Int, pageOffsetPx: Int) {
        if (viewportSize.width > 0 && pages.isNotEmpty()) {
            setScalePercent(scalePercent)
            scrollToPage(pageIndex, pageOffsetPx)
        } else {
            pendingInitialScalePercent = scalePercent
            pendingInitialPage = pageIndex
            pendingInitialOffset = pageOffsetPx
        }
    }

    actual val firstVisiblePageIndex: Int by derivedStateOf {
        PdfViewerMath.firstVisiblePageIndex(layout, pan.y, zoom)
    }

    actual val firstVisiblePageOffsetPx: Int by derivedStateOf {
        PdfViewerMath.pageScrollOffsetPx(layout, firstVisiblePageIndex, pan.y, zoom)
    }

    actual val scalePercent: Int by derivedStateOf { (zoom * 100f).roundToInt() }

    /**
     * Потолок зума, который «впекается» в размер layout'а (адаптивно к размеру
     * страницы). Зум сверх него идёт через GPU-трансформу — см. [residualScale].
     */
    val layoutCap: Float by derivedStateOf { PdfViewerMath.layoutZoomCap(layout) }

    /** Зум для размера/растеризации страницы (≤ [layoutCap]). */
    val layoutZoom: Float get() = PdfViewerMath.layoutZoom(zoom, layoutCap)

    /** Остаточный зум сверх [layoutZoom], применяемый через `graphicsLayer`. */
    val residualScale: Float get() = PdfViewerMath.residualScale(zoom, layoutCap)

    /**
     * scalePercent, ограниченный [layoutCap]: целевое разрешение растеризации
     * PDF-битмапа не растёт выше cap (выше — GPU-апскейл).
     */
    val renderScalePercent: Int by derivedStateOf { (layoutZoom * 100f).roundToInt() }

    /**
     * Cursor-anchored zoom: переводит масштаб в [targetZoom], сохраняя точку
     * под [focus] на месте. [focus] — viewport-координаты курсора/центра
     * жеста.
     */
    fun zoomTo(targetZoom: Float, focus: Offset) {
        val (newPan, newZoom) = PdfViewerMath.zoomAroundFocus(
            focus = focus,
            panOld = pan,
            zoomOld = zoom,
            zoomTarget = targetZoom,
        )
        zoom = newZoom
        // Если после зума страница помещается во вьюпорт — центрируем её
        // (zoom-out до размера меньше окна → страница встаёт по центру). По
        // переполняющим осям — обычный edge-clamp.
        pan = centeredAndClamped(newPan)
    }

    actual fun zoomBy(factor: Float, focus: Offset) {
        zoomTo(zoom * factor, focus)
    }

    /**
     * Транзиентное Ctrl+wheel-обновление: меняет `gestureScale` и
     * `gestureTranslation`, НЕ трогая [zoom] / [pan]. SubcomposeLayout не
     * пересчитывается, PDF-битмапы не ре-растеризуются — видимый зум
     * получается через `graphicsLayer` на корне страниц.
     *
     * Сохраняет инвариант cursor-anchor: точка под [prevCentroid] при старом
     * (gestureScale, gestureTranslation) окажется под [newCentroid] при
     * новом. Для wheel-зума `prev == new` (фокус — позиция курсора).
     * Effective-зум `zoom * gestureScale` кламп'ится в `MIN_ZOOM..MAX_ZOOM`,
     * gestureTranslation пересчитывается уже с учётом клампа.
     */
    internal fun pinchGestureUpdate(prevCentroid: Offset, newCentroid: Offset, factor: Float) {
        val oldScale = gestureScale
        if (oldScale <= 0f || zoom <= 0f) return
        val oldTrans = gestureTranslation
        val docX = (prevCentroid.x - oldTrans.x) / oldScale
        val docY = (prevCentroid.y - oldTrans.y) / oldScale
        val effectiveNewZoom = (zoom * oldScale * factor)
            .coerceIn(PdfViewerMath.MIN_ZOOM, PdfViewerMath.MAX_ZOOM)
        val newScale = effectiveNewZoom / zoom
        gestureScale = newScale
        gestureTranslation = Offset(
            x = newCentroid.x - docX * newScale,
            y = newCentroid.y - docY * newScale,
        )
    }

    /**
     * Впекает накопленные [gestureScale] / [gestureTranslation] в [zoom] /
     * [pan] и сбрасывает gesture-state в identity. Идемпотентно: повторный
     * вызов при уже identity-состоянии — no-op.
     *
     * Pan НЕ клампится: cursor-anchored математика [pinchGestureUpdate] уже
     * держит точку под пальцем стабильной, и любой edge-clamp в этой точке
     * даст видимый «прыжок» страницы (типично — к левому краю вьюпорта при
     * первом пересечении порога `contentW > viewportW` во время off-center
     * пинча). Edge-clamp применяется только в [panBy] для одно-пальцевого
     * скролла, где clamp ожидаем.
     *
     * Compose батчит snapshot-write'ы в один кадр — identity-layer приходит
     * ровно тогда же, когда layout перемеряется на новом `zoom`, визуального
     * скачка нет.
     */
    internal fun commitPinchGesture() {
        val s = gestureScale
        val t = gestureTranslation
        if (s == 1f && t == Offset.Zero) return
        val newZoom = (zoom * s).coerceIn(PdfViewerMath.MIN_ZOOM, PdfViewerMath.MAX_ZOOM)
        val newPan = Offset(x = pan.x * s + t.x, y = pan.y * s + t.y)
        zoom = newZoom
        // Если после пинча лист PDF умещается по ширине во вьюпорт — центрируем
        // его (zoom-out до размера меньше окна встаёт по центру). Меряем по
        // самому листу (basePageWidthPx), а НЕ по слоту с extent: иначе штрих,
        // заехавший за лист, расширял бы слот и ломал бы это условие — лист
        // переставал бы центрироваться. Пока лист переполняет ширину, pan НЕ
        // трогаем: edge-clamp здесь дал бы «прыжок» к краю при off-center пинче.
        val pdfW = layout.basePageWidthPx * newZoom
        pan = if (pdfW <= viewportSize.width) centeredAndClamped(newPan) else newPan
        gestureScale = 1f
        gestureTranslation = Offset.Zero
    }

    actual fun setScalePercent(percent: Int) {
        val target = percent / 100f
        val center = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
        zoomTo(target, center)
    }

    /**
     * Сдвигает [pan] на [delta] (viewport-пиксели), с клампом ТОЛЬКО по тем
     * осям, на которых действительно было движение. Иначе чисто
     * вертикальный скролл (delta = (0, dy)) триггерил бы X-клампинг и
     * сдвигал страницу к левому краю на каждом тике скролла, если pan.x
     * лежит вне окна clamp'а (например, после off-center пинч-зума).
     */
    fun panBy(delta: Offset) {
        val candidate = pan + delta
        val c = clamped(candidate)
        pan = Offset(
            x = if (delta.x == 0f) pan.x else c.x,
            y = if (delta.y == 0f) pan.y else c.y,
        )
    }

    // ===== Overscroll =====
    // [pan] всегда жёстко кламплен; «перелёт» за край показывается отдельным
    // визуальным смещением контента [overscrollOffset] (GPU-трансляция во
    // вьювере), которое пружинит к нулю в [stepOverscroll]. Тем же вектором
    // рисуется краевая тень. И колесо, и drag дают пружину + тень, не двигая
    // саму позицию скролла — поэтому на обычном скролле нет тряски, а
    // центрирование/кламп не ломаются.

    /**
     * Видимое overscroll-смещение контента (viewport-px). Пружина действует
     * напрямую на это значение — визуальное затухание строго экспоненциально
     * (не зависит от промежуточного raw-накопителя). Знак = направление «перелёта»:
     * `x > 0` тянет вправо (упор в левый край), `y > 0` — вниз (упор в верх).
     * Применяется вьювером как GPU-трансляция и рисуется краевой тенью.
     */
    var overscrollOffset: Offset by mutableStateOf(Offset.Zero)
        private set

    /** Палец активного drag «держит» смещение пока движется. */
    private var overscrollHeld = false

    /** Абсолютное положение «сырого» пальца во время drag для расчёта перелёта. */
    private var dragRawPan = Offset.Zero

    /**
     * Накопитель «непогашенного» у края вклада колеса за текущий кадр. Колесо/
     * тачпад могут слать > 100 событий/сек — больше, чем кадров. [wheelScrollBy]
     * лишь копит сюда, а интеграция в [overscrollOffset] идёт ровно раз в кадр в
     * [stepOverscroll]: иначе число add'ов на кадр плавает с частотой событий →
     * межкадровая пульсация overscroll = подёргивание.
     * Тот же thread (Compose main) пишет и читает — синхронизация не нужна.
     */
    private var pendingWheelOverscroll = Offset.Zero

    /**
     * Скорость пружины overscroll (px/с) по каждой оси. Wheel-события конвертируются
     * в импульс скорости (а не скачок позиции) → скорость сглаживает движение между
     * кадрами независимо от нестабильности потока событий.
     */
    private var springVelX = 0f
    private var springVelY = 0f

    /** Начало drag-to-pan: фиксирует сырое положение пальца и режим удержания. */
    fun beginPanGesture() {
        dragRawPan = pan
        overscrollHeld = true
    }

    /**
     * Drag-сдвиг: [pan] жёстко кламплен, а «перелёт» пальца за окно clamp'а
     * демпфируется [softDampOverscroll] и пишется в [overscrollOffset] — контент
     * тянется за пальцем с резиновым сопротивлением (чем дальше, тем меньше
     * прирост). На отпускании вызови [endPanGesture] — пружина стартует
     * мгновенно от текущего [overscrollOffset].
     */
    fun panGestureBy(delta: Offset) {
        dragRawPan = Offset(
            x = if (delta.x == 0f) dragRawPan.x else dragRawPan.x + delta.x,
            y = if (delta.y == 0f) dragRawPan.y else dragRawPan.y + delta.y,
        )
        val c = clamped(dragRawPan)
        pan = Offset(
            x = if (delta.x == 0f) pan.x else c.x,
            y = if (delta.y == 0f) pan.y else c.y,
        )
        overscrollOffset = Offset(
            x = if (delta.x == 0f) overscrollOffset.x else softDampOverscroll(dragRawPan.x - c.x),
            y = if (delta.y == 0f) overscrollOffset.y else softDampOverscroll(dragRawPan.y - c.y),
        )
    }

    /**
     * Конец drag: снимает удержание. Пружина стартует от текущего [overscrollOffset]
     * с нулевой начальной скоростью — критически демпфированная система вернётся
     * к нулю за ~0.4 с без перелёта.
     */
    fun endPanGesture() {
        overscrollHeld = false
        springVelX = 0f
        springVelY = 0f
    }

    /**
     * Скролл колесом/трекпадом: [pan] жёстко кламплен сразу (скролл отзывчив),
     * «непогашенный» у края остаток копится в [pendingWheelOverscroll].
     * Интеграцию в [overscrollOffset] и пружинный возврат делает [stepOverscroll]
     * один раз за кадр.
     */
    fun wheelScrollBy(delta: Offset) {
        overscrollHeld = false
        val candidate = pan + delta
        val c = clamped(candidate)
        pan = Offset(
            x = if (delta.x == 0f) pan.x else c.x,
            y = if (delta.y == 0f) pan.y else c.y,
        )
        val unX = if (delta.x == 0f) 0f else candidate.x - c.x
        val unY = if (delta.y == 0f) 0f else candidate.y - c.y
        if (unX != 0f || unY != 0f) {
            pendingWheelOverscroll += Offset(unX, unY)
        }
    }

    /**
     * Per-frame шаг overscroll — velocity-based spring, как в Safari.
     *
     * Wheel-события конвертируются в **импульс скорости** (не скачок позиции):
     * `vel += pending * gain * IMPULSE_GAIN`. Скорость интегрируется плавно →
     * никакого saw-tooth от нестабильного потока событий.
     *
     * Пружина (критически демпфированная, ω₀ ≈ 10 рад/с) тянет позицию к 0:
     * `accel = -stiffness * pos - damping * vel`. Время оседания ≈ 0.4 с.
     *
     * При macOS-momentum пружина и импульсы приходят к естественному равновесию
     * ∝ скорости scroll'а; по мере затухания импульсы уменьшаются → пружина
     * плавно возвращает к нулю — без таймеров и mode-switching.
     *
     * Вызывать каждый кадр.
     */
    fun stepOverscroll(dtMillis: Long) {
        val dtSec = dtMillis / 1000f
        val pending = pendingWheelOverscroll
        if (pending != Offset.Zero) {
            val gainX = (1f - abs(overscrollOffset.x) / OVERSCROLL_MAX_OFFSET_PX).coerceIn(0f, 1f)
            val gainY = (1f - abs(overscrollOffset.y) / OVERSCROLL_MAX_OFFSET_PX).coerceIn(0f, 1f)
            springVelX += pending.x * gainX * OVERSCROLL_IMPULSE_GAIN
            springVelY += pending.y * gainY * OVERSCROLL_IMPULSE_GAIN
            pendingWheelOverscroll = Offset.Zero
        }
        if (overscrollHeld) return
        val posX = overscrollOffset.x
        val posY = overscrollOffset.y
        if (posX == 0f && posY == 0f && springVelX == 0f && springVelY == 0f) return
        // Spring force: F = -k*x - c*v
        springVelX += (-OVERSCROLL_SPRING_STIFFNESS * posX - OVERSCROLL_SPRING_DAMPING * springVelX) * dtSec
        springVelY += (-OVERSCROLL_SPRING_STIFFNESS * posY - OVERSCROLL_SPRING_DAMPING * springVelY) * dtSec
        var newX = posX + springVelX * dtSec
        var newY = posY + springVelY * dtSec
        // Hard clamp: spring не может вытолкнуть дальше максимума
        if (newX > OVERSCROLL_MAX_OFFSET_PX) { newX = OVERSCROLL_MAX_OFFSET_PX; springVelX = 0f }
        else if (newX < -OVERSCROLL_MAX_OFFSET_PX) { newX = -OVERSCROLL_MAX_OFFSET_PX; springVelX = 0f }
        if (newY > OVERSCROLL_MAX_OFFSET_PX) { newY = OVERSCROLL_MAX_OFFSET_PX; springVelY = 0f }
        else if (newY < -OVERSCROLL_MAX_OFFSET_PX) { newY = -OVERSCROLL_MAX_OFFSET_PX; springVelY = 0f }
        // Settle: snap to zero when both position and velocity are negligible
        if (abs(newX) < OVERSCROLL_EPS_PX && abs(springVelX) < OVERSCROLL_VEL_EPS) { newX = 0f; springVelX = 0f }
        if (abs(newY) < OVERSCROLL_EPS_PX && abs(springVelY) < OVERSCROLL_VEL_EPS) { newY = 0f; springVelY = 0f }
        overscrollOffset = Offset(newX, newY)
    }

    /** Перелёт пальца [v] (px) → демпфированное смещение: 0 → 0, ±∞ → ±[OVERSCROLL_MAX_OFFSET_PX]. */
    private fun softDampOverscroll(v: Float): Float {
        val m = OVERSCROLL_MAX_OFFSET_PX
        val damped = m * (1f - 1f / (abs(v) / m + 1f))
        return if (v < 0f) -damped else damped
    }

    actual fun scrollToPage(pageIndex: Int, offsetPx: Int) {
        if (pages.isEmpty()) return
        val idx = pageIndex.coerceIn(0, pages.lastIndex)
        val newPan = PdfViewerMath.panForPageScroll(
            layout = layout,
            pageIndex = idx,
            offsetPx = offsetPx,
            zoom = zoom,
            currentPanX = pan.x,
        )
        pan = clamped(newPan)
    }

    /** Зум "по ширине" — страница занимает всю ширину вьюпорта. */
    fun fitToWidth(pageIndex: Int = firstVisiblePageIndex) {
        if (viewportSize.width <= 0 || pages.isEmpty()) return
        val newZoom = PdfViewerMath.fitToWidthZoom(layout, viewportSize.width.toFloat())
        zoom = newZoom
        pan = PdfViewerMath.panForPageTop(layout, pageIndex, newZoom, viewportSize.width.toFloat())
            .let(::clamped)
    }

    actual fun doubleTapZoom(focus: Offset) {
        if (viewportSize.width <= 0 || pages.isEmpty()) return
        val base = layout.basePageWidthPx
        if (base <= 0f || zoom <= 0f) return
        val availableWidth = viewportSize.width - fitWidthInsetStartPx - fitWidthInsetEndPx
        val target = PdfViewerMath.doubleTapTargetZoom(
            currentZoom = zoom,
            basePageWidthPx = base,
            availableWidthPx = availableWidth,
        )
        val fitZoom = (availableWidth.coerceAtLeast(1f) / base)
            .coerceIn(PdfViewerMath.MIN_ZOOM, PdfViewerMath.MAX_ZOOM)
        if (target <= fitZoom * PdfViewerMath.DOUBLE_TAP_FIT_EPSILON) {
            // Fit-width (отдаление): укладываем страницу в свободную область —
            // правее тулрейла и ниже счётчика, а не под ними.
            zoom = target
            pan = clamped(
                PdfViewerMath.panForFitWidth(
                    layout = layout,
                    pageIndex = firstVisiblePageIndex,
                    zoom = target,
                    viewportWidth = viewportSize.width.toFloat(),
                    insetStartPx = fitWidthInsetStartPx,
                    insetTopPx = fitWidthInsetTopPx,
                    insetEndPx = fitWidthInsetEndPx,
                ),
            )
        } else {
            // Приближение — cursor-anchored: точка документа под курсором на месте.
            val docX = (focus.x - pan.x) / zoom
            val docY = (focus.y - pan.y) / zoom
            zoom = target
            pan = clamped(Offset(focus.x - docX * target, focus.y - docY * target))
        }
    }

    /** Зум "по странице" — текущая страница помещается целиком во вьюпорт. */
    fun fitToPage(pageIndex: Int = firstVisiblePageIndex) {
        if (viewportSize.width <= 0 || viewportSize.height <= 0 || pages.isEmpty()) return
        val vp = FloatSize(viewportSize.width.toFloat(), viewportSize.height.toFloat())
        val newZoom = PdfViewerMath.fitToPageZoom(layout, pageIndex, vp)
        zoom = newZoom
        pan = PdfViewerMath.panForPageTop(layout, pageIndex, newZoom, vp.width).let(::clamped)
    }

    /**
     * Перецентровка после изменения размера вьюпорта (открытие/закрытие
     * панели, перетаскивание разделителя, ресайз окна). Лист, помещающийся в
     * новую ширину/высоту, встаёт по центру; зумленный лист, выходящий за
     * вьюпорт, лишь edge-кламп'ится — без рывка к центру. Скролл по
     * переполняющей оси сохраняется.
     */
    fun reCenterAfterResize() {
        if (viewportSize.width <= 0 || pages.isEmpty()) return
        pan = centeredAndClamped(pan)
    }

    /** Сбрасывает на 100% и верх документа. */
    fun resetView() {
        if (viewportSize.width <= 0) return
        zoom = 1f
        pan = PdfViewerMath.panForPageTop(layout, 0, 1f, viewportSize.width.toFloat())
            .let(::clamped)
    }

    private fun clamped(p: Offset): Offset = PdfViewerMath.clampPan(
        pan = p,
        layout = layout,
        zoom = zoom,
        viewportSize = FloatSize(viewportSize.width.toFloat(), viewportSize.height.toFloat()),
    )

    /**
     * Центрирует [p] по тем осям, на которых лист PDF помещается во вьюпорт
     * (через [PdfViewerMath.centeringClamp]); по переполняющим осям — обычный
     * edge-clamp. Нужно для зума: когда после уменьшения масштаба страница стала
     * меньше окна, она автоматически встаёт по центру.
     *
     * Edge-clamp применяется ТОЛЬКО к переполняющим осям. На помещающейся оси
     * центрированное значение оставляем как есть: иначе [PdfViewerMath.clampPan]
     * (он считает границы по слоту с [PageExtent]) загнал бы в экран весь слот,
     * включая штрих за листом, и сдвинул бы лист от центра — лист центрировался
     * бы «по документу со штрихами», а не по самому листу.
     */
    private fun centeredAndClamped(p: Offset): Offset {
        val vp = FloatSize(viewportSize.width.toFloat(), viewportSize.height.toFloat())
        val centered = PdfViewerMath.centeringClamp(p, layout, zoom, vp)
        val clampedPan = PdfViewerMath.clampPan(centered, layout, zoom, vp)
        val pdfFitsWidth = layout.basePageWidthPx * zoom <= vp.width
        val pdfFitsHeight = layout.totalHeightPx * zoom <= vp.height
        return Offset(
            x = if (pdfFitsWidth) centered.x else clampedPan.x,
            y = if (pdfFitsHeight) centered.y else clampedPan.y,
        )
    }

    companion object {

        /** Доля ширины окна, занимаемая страничной колонкой при zoom = 1. */
        internal const val BASE_PAGE_WIDTH_FRACTION: Float = 2f / 3f

        /** Saver для [rememberSaveable]: сохраняет zoom + положение скролла. */
        val Saver: Saver<PdfViewerState, Any> = listSaver(
            save = { s: PdfViewerState ->
                listOf(
                    s.zoom.toDouble(),
                    s.firstVisiblePageIndex,
                    s.firstVisiblePageOffsetPx,
                )
            },
            restore = { saved: List<Any?> ->
                PdfViewerState(
                    initialZoom = (saved[0] as Number).toFloat(),
                    initialPageIndex = (saved[1] as Number).toInt(),
                    initialPageOffsetPx = (saved[2] as Number).toInt(),
                )
            },
        )
    }
}

@Composable
actual fun rememberPdfViewerState(
    initialZoom: Float,
    initialPage: Int,
    initialPageOffsetPx: Int,
): PdfViewerState = rememberSaveable(saver = PdfViewerState.Saver) {
    PdfViewerState(
        initialZoom = initialZoom,
        initialPageIndex = initialPage,
        initialPageOffsetPx = initialPageOffsetPx,
    )
}
