package ru.kyamshanov.notepen.magnifier

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
class MagnifierTargetGestureController(
    private val state: MagnifierState,
    private val viewerState: PdfViewerState,
    /**
     * Колбэк, вызываемый после `onUp` в режиме MOVE. Используется
     * `DetailsContent` для обновления pinned-viewport-прямоугольника в режиме
     * [MagnifierAttachment.SCREEN]: пока пользователь зажимает рамку, она
     * визуально отвязана от screen-pin'а; после отпускания нужно зафиксировать
     * новую viewport-позицию как pin-якорь.
     */
    private val onMoveFinished: () -> Unit = {},
) {
    enum class Mode { NONE, MOVE, RESIZE }

    private var mode: Mode by mutableStateOf(Mode.NONE)
    private var startViewport: Offset = Offset.Zero
    private var startPanX: Float = 0f
    private var startPanY: Float = 0f
    private var startLeftXDoc: Float = 0f
    private var startTopYDoc: Float = 0f
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
        val handlePx =
            (layout.basePageWidthPx * zoom * RESIZE_HANDLE_FRAC)
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
        val basePageW = layout.basePageWidthPx
        val pdfH = layout.pdfHeightsPx[seg.pageIndex]
        val pageLeft = layout.pageLeftsPx[seg.pageIndex]
        val pageTop = layout.pageTopsPx[seg.pageIndex]
        val r = seg.targetOnPage
        mode = m
        startViewport = viewportPos
        startPanX = viewerState.pan.x
        startPanY = viewerState.pan.y
        startLeftXDoc = pageLeft + r.left * basePageW
        startTopYDoc = pageTop + r.top * pdfH
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
        val wasMove = mode == Mode.MOVE
        mode = Mode.NONE
        if (wasMove) onMoveFinished()
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

        // Кумулятивный drag в document-space с поправкой на изменение pan'а
        // во время удержания: если viewer проскроллил, тот же viewport-Y
        // соответствует другой document-Y. Без поправки рамка «отставала»
        // бы от пера на величину скролла.
        val pan = viewerState.pan
        val effDragVpX = (viewportPos.x - startViewport.x) - (pan.x - startPanX)
        val effDragVpY = (viewportPos.y - startViewport.y) - (pan.y - startPanY)
        val totalDragDocX = effDragVpX / zoom
        val totalDragDocY = effDragVpY / zoom

        val width = startWidthNorm
        val height = startHeightNorm
        val targetWidthDoc = width * basePageW
        val intendedLeftXDoc = startLeftXDoc + totalDragDocX
        val intendedCenterXDoc = intendedLeftXDoc + targetWidthDoc / 2f
        val intendedTopYDoc = startTopYDoc + totalDragDocY

        // In spread mode both pages in a pair share the same Y row, so selecting
        // by Y alone always lands on one side of the spread. Use document-X to
        // choose the left/right column, then normalize inside that page.
        val tops = layout.pageTopsPx
        val newPageIdx =
            resolvePageForDocSpace(layout, intendedCenterXDoc, intendedTopYDoc)
                .coerceAtMost(pageCount - 1)
        val newPdfH = layout.pdfHeightsPx[newPageIdx]
        val newPageTop = tops[newPageIdx]
        val newPageLeft = layout.pageLeftsPx[newPageIdx]
        val rawNewLeftNorm = (intendedLeftXDoc - newPageLeft) / basePageW
        val newLeftNorm = rawNewLeftNorm.coerceIn(0f, (1f - width).coerceAtLeast(0f))
        val rawNewTopNorm = (intendedTopYDoc - newPageTop) / newPdfH
        val newTopNorm = rawNewTopNorm.coerceIn(0f, (1f - height).coerceAtLeast(0f))

        state.setSingleSegmentTarget(
            pageIndex = newPageIdx,
            targetOnPage =
                Rect(
                    newLeftNorm,
                    newTopNorm,
                    newLeftNorm + width,
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

        val pan = viewerState.pan
        val effDragVpX = (viewportPos.x - startViewport.x) - (pan.x - startPanX)
        val effDragVpY = (viewportPos.y - startViewport.y) - (pan.y - startPanY)
        val totalDragXNorm = effDragVpX / (basePageW * zoom)
        val totalDragYNorm = effDragVpY / (pdfH * zoom)
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
        // В развороте правая страница пары смещена по X на pageLeftsPx[pi]
        // (в SINGLE — всегда 0f). Без него viewport-rect рамки правой
        // половины считался бы на месте левой → жест письма по левой странице
        // ловил бы hit-test рамки, которая на другой стороне листа.
        val pageLeft = layout.pageLeftsPx[pi]
        val pan = viewerState.pan
        val t = seg.targetOnPage
        val left = pan.x + (pageLeft + t.left * basePageW) * zoom
        val right = pan.x + (pageLeft + t.right * basePageW) * zoom
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
