package ru.kyamshanov.notepen.magnifier

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import ru.kyamshanov.notepen.pdfviewer.PdfPagesLayout
import ru.kyamshanov.notepen.pdfviewer.PdfViewerState
import kotlin.math.max
import kotlin.math.min

/**
 * Контроллер жеста «диагональное выделение для лупы».
 *
 * Получает viewport-координаты от Down/Move/Up; пока жест активен,
 * экспонирует текущий прямоугольник через [selectionRect] (viewport-px).
 *
 * Поддерживает мульти-страничное выделение: если диагональ пересекает
 * границы страниц, на Up'е возвращает список [MagnifierPageSegment] — по
 * одному на каждую задетую страницу. Однополосный кейс — список из
 * одного элемента.
 */
internal class LoupeSelectionController(
    private val viewerState: PdfViewerState,
    private val viewportSizeProvider: () -> Size,
    private val onSelected: (
        segments: List<MagnifierPageSegment>,
        viewportSize: Size,
        selectionSizePx: Size,
        panelCenter: Offset,
    ) -> Unit,
) {

    private val _selectionRect: MutableState<Rect?> = mutableStateOf(null)

    /** Текущий прямоугольник выделения в viewport-пикселях, или `null` если жест не активен. */
    val selectionRect: MutableState<Rect?> get() = _selectionRect

    private var startVp: Offset = Offset.Zero
    private var lastVp: Offset = Offset.Zero
    private var active: Boolean = false

    /** `true`, пока жест в активной фазе (после `onDown` и до `onUp`/`onCancel`). */
    val isActive: Boolean get() = active

    fun onDown(viewportPos: Offset) {
        cancelInternal()
        if (!isLayoutReady()) return
        startVp = viewportPos
        lastVp = viewportPos
        active = true
        _selectionRect.value = Rect(viewportPos.x, viewportPos.y, viewportPos.x, viewportPos.y)
    }

    fun onMove(viewportPos: Offset) {
        if (!active) return
        lastVp = viewportPos
        _selectionRect.value = rectFromCorners(startVp, viewportPos)
    }

    fun onUp() {
        if (!active) {
            cancelInternal()
            return
        }
        val rect = rectFromCorners(startVp, lastVp)
        val endVp = lastVp
        cancelInternal()

        if (rect.width < MIN_SELECTION_PX || rect.height < MIN_SELECTION_PX) return

        val segments = buildSegments(rect) ?: return
        val selectionSizePx = computeSelectionSizePx(segments)
        onSelected(segments, viewportSizeProvider(), selectionSizePx, endVp)
    }

    fun onCancel() {
        cancelInternal()
    }

    private fun cancelInternal() {
        active = false
        _selectionRect.value = null
    }

    private fun rectFromCorners(a: Offset, b: Offset): Rect = Rect(
        left = min(a.x, b.x),
        top = min(a.y, b.y),
        right = max(a.x, b.x),
        bottom = max(a.y, b.y),
    )

    /**
     * Разбивает viewport-прямоугольник на сегменты по страницам.
     * Возвращает `null`, если выделение не попадает ни на одну валидную
     * страницу или каждая отдельная полоса слишком узкая.
     */
    private fun buildSegments(vpRect: Rect): List<MagnifierPageSegment>? {
        val layout: PdfPagesLayout = viewerState.layout
        val n = layout.pageHeightsPx.size
        val zoom = viewerState.zoom
        val basePageW = layout.basePageWidthPx
        if (n == 0 || basePageW <= 0f || zoom <= 0f) return null

        // X-составляющая в page-normalized одна и та же для всех страниц,
        // т.к. ширина страниц общая (basePageWidthPx).
        val pan = viewerState.pan
        val docLeft = (vpRect.left - pan.x) / zoom
        val docRight = (vpRect.right - pan.x) / zoom
        val nxLeft = (docLeft / basePageW).coerceIn(0f, 1f)
        val nxRight = (docRight / basePageW).coerceIn(0f, 1f)
        if (nxRight - nxLeft < MIN_TARGET_DIM) return null

        val docTop = (vpRect.top - pan.y) / zoom
        val docBottom = (vpRect.bottom - pan.y) / zoom
        // Полная высота выделения в doc-space; пригодится для расчёта
        // panelTopFrac/panelBottomFrac каждого сегмента (по реальной
        // высоте, которую занимает страница в выделении).
        val totalDocH = (docBottom - docTop).coerceAtLeast(1f)

        val out = mutableListOf<MagnifierPageSegment>()
        for (pageIndex in 0 until n) {
            val pageTop = layout.pageTopsPx[pageIndex]
            val pdfH = layout.pdfHeightsPx[pageIndex]
            val pageBottom = pageTop + pdfH

            // Пересечение [docTop..docBottom] с [pageTop..pageBottom].
            val interTop = max(docTop, pageTop)
            val interBottom = min(docBottom, pageBottom)
            if (interBottom - interTop < MIN_TARGET_DIM * pdfH) continue

            val nyTop = ((interTop - pageTop) / pdfH).coerceIn(0f, 1f)
            val nyBottom = ((interBottom - pageTop) / pdfH).coerceIn(0f, 1f)
            if (nyBottom - nyTop < MIN_TARGET_DIM) continue

            // Доля высоты выделения, попадающая на эту страницу.
            val panelTopFrac = ((interTop - docTop) / totalDocH).coerceIn(0f, 1f)
            val panelBottomFrac = ((interBottom - docTop) / totalDocH).coerceIn(0f, 1f)

            out += MagnifierPageSegment(
                pageIndex = pageIndex,
                targetOnPage = Rect(nxLeft, nyTop, nxRight, nyBottom),
                panelTopFrac = panelTopFrac,
                panelBottomFrac = panelBottomFrac,
            )
        }
        return out.takeIf { it.isNotEmpty() }
    }

    /**
     * Размер выделения в viewport-пикселях для расчёта размера панели:
     * ширина — общая для всех сегментов (фактическая ширина выделения на
     * странице × zoom), высота — сумма по сегментам (в viewport-px).
     */
    private fun computeSelectionSizePx(segments: List<MagnifierPageSegment>): Size {
        val layout = viewerState.layout
        val zoom = viewerState.zoom
        val basePageW = layout.basePageWidthPx
        val first = segments.first()
        val widthPx = first.targetOnPage.width * basePageW * zoom
        var heightPx = 0f
        for (s in segments) {
            heightPx += s.targetOnPage.height * layout.pdfHeightsPx[s.pageIndex] * zoom
        }
        return Size(widthPx.coerceAtLeast(1f), heightPx.coerceAtLeast(1f))
    }

    private fun isLayoutReady(): Boolean {
        val layout = viewerState.layout
        return layout.pageHeightsPx.isNotEmpty() &&
            layout.basePageWidthPx > 0f &&
            viewerState.zoom > 0f
    }

    private companion object {
        /** Минимальная диагональ в viewport-пикселях, ниже которой выделение игнорируется. */
        const val MIN_SELECTION_PX: Float = 12f
    }
}
