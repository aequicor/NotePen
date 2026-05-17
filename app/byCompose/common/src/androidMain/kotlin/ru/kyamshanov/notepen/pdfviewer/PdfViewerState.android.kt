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
    var zoom: Float by mutableFloatStateOf(initialZoom.coerceIn(PdfViewerMath.MIN_ZOOM, PdfViewerMath.MAX_ZOOM))
        private set

    /** Текущий сдвиг документа во вьюпорте. */
    var pan: Offset by mutableStateOf(Offset(initialPanX, initialPanY))
        private set

    /** Размер вьюпорта в физических пикселях. */
    var viewportSize: IntSize by mutableStateOf(IntSize.Zero)
        internal set

    /** Текущий список страниц документа. */
    var pages: List<PdfPageInfo> by mutableStateOf(emptyList())
        internal set

    internal val basePageWidthPx: Float
        get() = viewportSize.width * BASE_PAGE_WIDTH_FRACTION

    internal val layout: PdfPagesLayout by derivedStateOf {
        PdfPagesLayout.build(
            pages = pages,
            basePageWidthPx = basePageWidthPx,
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
     * Атомарное pinch-обновление: одновременно меняет `zoom` (на [factor])
     * и переносит anchor с [prevCentroid] на [newCentroid]. Сохраняет
     * инвариант: документ-точка, которая была под `prevCentroid` при старом
     * `zoom`, окажется под `newCentroid` при новом `zoom * factor`.
     *
     * Pan НЕ кламп'ится — иначе при cursor-anchor zoom'е страница «снапит»
     * к границе вьюпорта на каждом пинч-событии, и точка под пальцем
     * визуально сдвигается. Edge-clamp применяется отдельно на single-finger
     * скролле через [panBy] (см. `scrollableState` в `PdfPagesViewer.android.kt`).
     *
     * Эквивалент desktop'ного `state.zoomBy` без сопровождающего `state.panBy`.
     */
    internal fun pinchUpdate(prevCentroid: Offset, newCentroid: Offset, factor: Float) {
        if (zoom <= 0f) return
        val newZoom = (zoom * factor).coerceIn(PdfViewerMath.MIN_ZOOM, PdfViewerMath.MAX_ZOOM)
        val docX = (prevCentroid.x - pan.x) / zoom
        val docY = (prevCentroid.y - pan.y) / zoom
        zoom = newZoom
        pan = Offset(
            x = newCentroid.x - docX * newZoom,
            y = newCentroid.y - docY * newZoom,
        )
    }

    actual fun setScalePercent(percent: Int) {
        val target = percent / 100f
        val center = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
        zoomTo(target, center)
    }

    /** Сдвигает [pan] на [delta] (viewport-пиксели), с клампом. */
    fun panBy(delta: Offset) {
        pan = clamped(pan + delta)
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
