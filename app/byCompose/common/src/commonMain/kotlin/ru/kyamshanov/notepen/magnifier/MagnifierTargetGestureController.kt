package ru.kyamshanov.notepen.magnifier

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import ru.kyamshanov.notepen.pdfviewer.PdfViewerState

/**
 * Управление move/resize рамки-цели лупы (single-page) через нативный
 * pen-stream.
 *
 * Дрэг отслеживается **кумулятивно** от точки `onDown` (а не per-frame
 * дельтами от `state.targetOnPage`): иначе clamp в state мог бы «съесть»
 * накопленный drag, и рамка, упёршаяся в край страницы, не переходила бы
 * на соседнюю при продолжении drag'а.
 */
internal class MagnifierTargetGestureController(
    private val state: MagnifierState,
    private val viewerState: PdfViewerState,
) {
    enum class Mode { NONE, MOVE, RESIZE }

    private var mode: Mode = Mode.NONE
    private var startViewport: Offset = Offset.Zero
    private var startTopYDoc: Float = 0f
    private var startLeftXNorm: Float = 0f
    private var startWidthNorm: Float = 0f
    private var startHeightNorm: Float = 0f

    /** Проверяет, попадает ли [viewportPos] в рамку или её resize-хэндл. */
    fun hitTest(viewportPos: Offset): Mode {
        if (state.segments.size != 1) return Mode.NONE
        val seg = state.segments[0]
        val rect = targetRectInViewport(seg) ?: return Mode.NONE
        if (!rect.contains(viewportPos)) return Mode.NONE
        val layout = viewerState.layout
        val zoom = viewerState.zoom
        val handlePx = (layout.basePageWidthPx * zoom * RESIZE_HANDLE_FRAC)
            .coerceAtLeast(MIN_HANDLE_PX)
        val nearRight = viewportPos.x >= rect.right - handlePx
        val nearBottom = viewportPos.y >= rect.bottom - handlePx
        return if (nearRight && nearBottom) Mode.RESIZE else Mode.MOVE
    }

    fun onDown(viewportPos: Offset): Boolean {
        val m = hitTest(viewportPos)
        if (m == Mode.NONE) {
            mode = Mode.NONE
            return false
        }
        val seg = state.segments[0]
        val layout = viewerState.layout
        val pdfH = layout.pdfHeightsPx[seg.pageIndex]
        val pageTop = layout.pageTopsPx[seg.pageIndex]
        val r = seg.targetOnPage
        mode = m
        startViewport = viewportPos
        startTopYDoc = pageTop + r.top * pdfH
        startLeftXNorm = r.left
        startWidthNorm = r.right - r.left
        startHeightNorm = r.bottom - r.top
        return true
    }

    fun onMove(viewportPos: Offset) {
        if (mode == Mode.NONE) return
        if (state.segments.size != 1) {
            mode = Mode.NONE
            return
        }
        when (mode) {
            Mode.MOVE -> applyMove(viewportPos)
            Mode.RESIZE -> applyResize(viewportPos)
            Mode.NONE -> Unit
        }
    }

    fun onUp() {
        mode = Mode.NONE
    }

    fun onCancel() {
        mode = Mode.NONE
    }

    val isActive: Boolean get() = mode != Mode.NONE

    /**
     * Применяет move с учётом возможного перехода на соседнюю страницу.
     * Использует кумулятивный drag от [startViewport] / [startTopYDoc]
     * — это гарантирует, что push'нув рамку до нижнего края страницы и
     * продолжая тянуть, мы пройдём границу.
     */
    private fun applyMove(viewportPos: Offset) {
        val layout = viewerState.layout
        val basePageW = layout.basePageWidthPx
        val zoom = viewerState.zoom
        val pageCount = layout.pageHeightsPx.size
        if (basePageW <= 0f || zoom <= 0f || pageCount == 0) return

        val totalDragVp = viewportPos - startViewport
        val totalDragDocX = totalDragVp.x / zoom
        val totalDragDocY = totalDragVp.y / zoom

        val width = startWidthNorm
        val height = startHeightNorm
        val intendedTopYDoc = startTopYDoc + totalDragDocY
        val intendedLeftNorm = (startLeftXNorm + totalDragDocX / basePageW)
            .coerceIn(0f, (1f - width).coerceAtLeast(0f))

        // Какая страница содержит intendedTopYDoc?
        val tops = layout.pageTopsPx
        val newPageIdx = when {
            intendedTopYDoc < tops[0] -> 0
            else -> {
                var lo = 0
                var hi = pageCount - 1
                while (lo < hi) {
                    val mid = (lo + hi + 1) ushr 1
                    if (tops[mid] <= intendedTopYDoc) lo = mid else hi = mid - 1
                }
                lo
            }
        }
        val newPdfH = layout.pdfHeightsPx[newPageIdx]
        val newPageTop = tops[newPageIdx]
        val rawNewTopNorm = (intendedTopYDoc - newPageTop) / newPdfH
        val newTopNorm = rawNewTopNorm.coerceIn(0f, (1f - height).coerceAtLeast(0f))

        state.setSingleSegmentTarget(
            pageIndex = newPageIdx,
            targetOnPage = Rect(
                intendedLeftNorm,
                newTopNorm,
                intendedLeftNorm + width,
                newTopNorm + height,
            ),
        )
    }

    private fun applyResize(viewportPos: Offset) {
        val layout = viewerState.layout
        val pi = state.segments[0].pageIndex
        if (pi !in 0 until layout.pageHeightsPx.size) return
        val zoom = viewerState.zoom
        val basePageW = layout.basePageWidthPx
        val pdfH = layout.pdfHeightsPx[pi]
        if (zoom <= 0f || basePageW <= 0f || pdfH <= 0f) return

        val totalDragVp = viewportPos - startViewport
        val totalDragXNorm = totalDragVp.x / (basePageW * zoom)
        val totalDragYNorm = totalDragVp.y / (pdfH * zoom)
        state.resizeTarget(
            newWidth = (startWidthNorm + totalDragXNorm).coerceAtLeast(MIN_TARGET_DIM),
            newHeight = (startHeightNorm + totalDragYNorm).coerceAtLeast(MIN_TARGET_DIM),
        )
    }

    /** Прямоугольник рамки в координатах viewport'а для текущего сегмента. */
    private fun targetRectInViewport(seg: MagnifierPageSegment): Rect? {
        val layout = viewerState.layout
        val pi = seg.pageIndex
        if (pi !in 0 until layout.pageHeightsPx.size) return null
        val zoom = viewerState.zoom
        if (zoom <= 0f) return null
        val basePageW = layout.basePageWidthPx
        val pdfH = layout.pdfHeightsPx[pi]
        val pageTop = layout.pageTopsPx[pi]
        val pan = viewerState.pan
        val t = seg.targetOnPage
        val left = pan.x + t.left * basePageW * zoom
        val right = pan.x + t.right * basePageW * zoom
        val top = pan.y + (pageTop + t.top * pdfH) * zoom
        val bottom = pan.y + (pageTop + t.bottom * pdfH) * zoom
        return Rect(left, top, right, bottom)
    }

    private companion object {
        /**
         * Доля ширины страницы, занимаемая resize-хэндлом (совпадает с
         * `RESIZE_HANDLE_FRAC` в [MagnifierTargetOverlay]).
         */
        const val RESIZE_HANDLE_FRAC: Float = 0.04f
        const val MIN_HANDLE_PX: Float = 16f
    }
}
