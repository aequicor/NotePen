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
    var zoom: Float by mutableFloatStateOf(initialZoom.coerceIn(PdfViewerMath.MIN_ZOOM, PdfViewerMath.MAX_ZOOM))
        private set

    /** Текущий сдвиг документа во вьюпорте. */
    var pan: Offset by mutableStateOf(Offset(initialPanX, initialPanY))
        private set

    /** Размер вьюпорта в физических пикселях. Устанавливается из layout-pass. */
    var viewportSize: IntSize by mutableStateOf(IntSize.Zero)
        internal set

    /** Текущий список страниц документа. Устанавливается извне. */
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
        pan = newPan
    }

    actual fun zoomBy(factor: Float, focus: Offset) {
        zoomTo(zoom * factor, focus)
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

    /** Зум "по ширине" — страница занимает всю ширину вьюпорта. */
    fun fitToWidth(pageIndex: Int = firstVisiblePageIndex) {
        if (viewportSize.width <= 0 || pages.isEmpty()) return
        val newZoom = PdfViewerMath.fitToWidthZoom(layout, viewportSize.width.toFloat())
        zoom = newZoom
        pan = PdfViewerMath.panForPageTop(layout, pageIndex, newZoom, viewportSize.width.toFloat())
            .let(::clamped)
    }

    /** Зум "по странице" — текущая страница помещается целиком во вьюпорт. */
    fun fitToPage(pageIndex: Int = firstVisiblePageIndex) {
        if (viewportSize.width <= 0 || viewportSize.height <= 0 || pages.isEmpty()) return
        val vp = FloatSize(viewportSize.width.toFloat(), viewportSize.height.toFloat())
        val newZoom = PdfViewerMath.fitToPageZoom(layout, pageIndex, vp)
        zoom = newZoom
        pan = PdfViewerMath.panForPageTop(layout, pageIndex, newZoom, vp.width).let(::clamped)
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
