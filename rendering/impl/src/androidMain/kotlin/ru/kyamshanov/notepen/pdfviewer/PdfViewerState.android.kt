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
import kotlin.math.roundToInt
import ru.kyamshanov.notepen.annotation.domain.model.PageExtent
import ru.kyamshanov.notepen.pdf.domain.model.PdfPageInfo

/**
 * Android-реализация [PdfViewerState] — single-zoom модель, зеркало
 * desktop'a, но эволюционирует независимо. Никакого split-scale
 * (`committedScale` / `gestureScale`) и debounced "bake" — устранение
 * именно этой двухуровневой модели убирает скачки при пинче.
 *
 * При изменении [zoom] страницы сразу получают новый размер; битмапы
 * перерисовываются на текущем масштабе через общий [PdfBitmapCache], а
 * до прихода нового битмапа `Image.fillMaxSize` растягивает предыдущий —
 * никакого `graphicsLayer(scaleX = gestureScale)` сверху не нужно.
 */
@Suppress("TooManyFunctions")
actual class PdfViewerState internal constructor(
    initialZoom: Float = 1f,
    initialPanX: Float = 0f,
    initialPanY: Float = 0f,
    initialPageIndex: Int = 0,
    initialPageOffsetPx: Int = 0,
) {

    /** Текущий зум (1.0 = 100%). */
    actual var zoom: Float by mutableFloatStateOf(initialZoom.coerceIn(PdfViewerMath.MIN_ZOOM, PdfViewerMath.MAX_ZOOM))
        private set

    /** Текущий сдвиг документа во вьюпорте. */
    actual var pan: Offset by mutableStateOf(Offset(initialPanX, initialPanY))
        private set

    /** Размер вьюпорта в физических пикселях. */
    var viewportSize: IntSize by mutableStateOf(IntSize.Zero)
        internal set

    /** Текущий список страниц документа. */
    var pages: List<PdfPageInfo> by mutableStateOf(emptyList())
        internal set

    /**
     * Source of truth по [PageExtent] для страницы. Читается внутри
     * [derivedStateOf] при построении [layout]; изменение underlying
     * snapshot-state (`PdfDrawingState.extent`) триггерит пересчёт layout'а.
     */
    actual var pageExtentProvider: (Int) -> PageExtent by mutableStateOf({ PageExtent.Pdf })

    /**
     * Transient множитель для активного pinch'а: применяется к содержимому
     * `SubcomposeLayout` через `Modifier.graphicsLayer` без пересчёта layout'а
     * и без перерисовки PDF-битмапов. `1f` вне жеста.
     *
     * Зачем: layout-pass + restretch огромных PDF-битмапов + (если есть ink-кэш)
     * ре-растеризация штрихов в новый off-screen битмап на КАЖДЫЙ pinch-тик
     * — это и был источник лагов. Здесь же `zoom` остаётся «закоммиченным»,
     * SubcomposeLayout не пересчитывается, страница просто GPU-трансформируется
     * через render node. На commit жеста (`commitPinchGesture`) множитель и
     * трансляция «впекаются» в `zoom` и `pan` атомарно, gestureScale → 1f.
     */
    actual var gestureScale: Float by mutableFloatStateOf(1f)
        internal set

    /** Transient трансляция layer'а для активного pinch'а; см. [gestureScale]. */
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
            val pageW = (layout.basePageWidthPx * zoom).roundToInt()
            val centerX = (viewportSize.width - pageW) / 2
            pan = Offset(centerX.toFloat(), 0f)
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
     * Cursor-anchored zoom: переводит масштаб в [targetZoom], сохраняя
     * точку под [focus] (centroid пинча) на месте.
     */
    fun zoomTo(targetZoom: Float, focus: Offset) {
        val (newPan, newZoom) = PdfViewerMath.zoomAroundFocus(
            focus = focus,
            panOld = pan,
            zoomOld = zoom,
            zoomTarget = targetZoom,
        )
        zoom = newZoom
        pan = newPan
    }

    actual fun zoomBy(factor: Float, focus: Offset) {
        zoomTo(zoom * factor, focus)
    }

    /**
     * Транзиентное pinch-обновление: меняет `gestureScale` и `gestureTranslation`,
     * НЕ трогая [zoom] / [pan]. SubcomposeLayout не пересчитывается, PDF-битмапы
     * не ре-растеризуются — видимый зум получается через `graphicsLayer` на
     * корне страниц.
     *
     * Сохраняет инвариант cursor-anchor: точка под [prevCentroid] при старом
     * (gestureScale, gestureTranslation) окажется под [newCentroid] при новом.
     * Effective-зум `zoom * gestureScale` кламп'ится в [MIN_ZOOM..MAX_ZOOM],
     * gestureTranslation пересчитывается уже с учётом клампа — точка под
     * пальцем остаётся стабильной даже на упоре в предел.
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
     * Впекает накопленные [gestureScale] / [gestureTranslation] в [zoom] / [pan]
     * и сбрасывает gesture-state в identity. Идемпотентно: повторный вызов
     * при уже identity-состоянии — no-op.
     *
     * Pan НЕ клампится: cursor-anchored математика [pinchGestureUpdate] уже
     * держит точку под пальцем стабильной, и любой edge-clamp в этой точке
     * даст видимый «прыжок» страницы (типично — к левому краю вьюпорта при
     * первом пересечении порога `contentW > viewportW` во время off-center
     * пинча). Edge-clamp применяется только в [panBy] для одно-пальцевого
     * скролла, где clamp ожидаем (контент не должен «улетать» от пальца).
     *
     * Compose батчит все три snapshot-write'а в один кадр — graphicsLayer
     * приходит в identity ровно тогда же, когда layout перемеряется на новом
     * `zoom`, поэтому визуального скачка нет.
     */
    internal fun commitPinchGesture() {
        val s = gestureScale
        val t = gestureTranslation
        if (s == 1f && t == Offset.Zero) return
        val newZoom = (zoom * s).coerceIn(PdfViewerMath.MIN_ZOOM, PdfViewerMath.MAX_ZOOM)
        val newPan = Offset(x = pan.x * s + t.x, y = pan.y * s + t.y)
        zoom = newZoom
        pan = newPan
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

    private fun clamped(p: Offset): Offset = PdfViewerMath.clampPan(
        pan = p,
        layout = layout,
        zoom = zoom,
        viewportSize = FloatSize(viewportSize.width.toFloat(), viewportSize.height.toFloat()),
    )

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
