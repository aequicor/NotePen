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
class LoupeSelectionController(
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

    private fun rectFromCorners(
        a: Offset,
        b: Offset,
    ): Rect =
        Rect(
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
        val zoom = viewerState.zoom
        if (layout.pageHeightsPx.isEmpty() || layout.basePageWidthPx <= 0f || zoom <= 0f) return null

        val pan = viewerState.pan
        val docLeft = (vpRect.left - pan.x) / zoom
        val docRight = (vpRect.right - pan.x) / zoom
        val docTop = (vpRect.top - pan.y) / zoom
        val docBottom = (vpRect.bottom - pan.y) / zoom
        return buildLoupeSegmentsForDocRect(
            layout = layout,
            docRect = Rect(docLeft, docTop, docRight, docBottom),
        )
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
        var docLeft = Float.POSITIVE_INFINITY
        var docRight = Float.NEGATIVE_INFINITY
        var docTop = Float.POSITIVE_INFINITY
        var docBottom = Float.NEGATIVE_INFINITY
        for (s in segments) {
            val pageLeft = layout.pageLeftsPx[s.pageIndex]
            val pageTop = layout.pageTopsPx[s.pageIndex]
            val pdfH = layout.pdfHeightsPx[s.pageIndex]
            docLeft = min(docLeft, pageLeft + s.targetOnPage.left * basePageW)
            docRight = max(docRight, pageLeft + s.targetOnPage.right * basePageW)
            docTop = min(docTop, pageTop + s.targetOnPage.top * pdfH)
            docBottom = max(docBottom, pageTop + s.targetOnPage.bottom * pdfH)
        }
        val widthPx = (docRight - docLeft) * zoom
        val heightPx = (docBottom - docTop) * zoom
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

/**
 * Разбивает document-space прямоугольник выделения на page-normalized сегменты.
 *
 * В [ru.kyamshanov.notepen.pdfviewer.SpreadMode.SPREAD] правая страница пары
 * имеет ненулевой [PdfPagesLayout.pageLeftsPx]. Поэтому X нужно считать от
 * левого края конкретной страницы, а не от `0`: иначе выделение на правой
 * колонке превращается в `x > 1` и все штрихи из лупы сохраняются за пределами
 * PDF-страницы.
 */
internal fun buildLoupeSegmentsForDocRect(
    layout: PdfPagesLayout,
    docRect: Rect,
): List<MagnifierPageSegment>? {
    val n = layout.pageHeightsPx.size
    val basePageW = layout.basePageWidthPx
    if (n == 0 || basePageW <= 0f) return null

    val docLeft = min(docRect.left, docRect.right)
    val docRight = max(docRect.left, docRect.right)
    val docTop = min(docRect.top, docRect.bottom)
    val docBottom = max(docRect.top, docRect.bottom)
    val totalDocW = (docRight - docLeft).coerceAtLeast(1f)
    val totalDocH = (docBottom - docTop).coerceAtLeast(1f)

    return (0 until n)
        .mapNotNull { pageIndex ->
            buildLoupeSegmentForPage(
                layout = layout,
                pageIndex = pageIndex,
                basePageW = basePageW,
                docLeft = docLeft,
                docRight = docRight,
                docTop = docTop,
                docBottom = docBottom,
                totalDocW = totalDocW,
                totalDocH = totalDocH,
            )
        }.takeIf { it.isNotEmpty() }
}

private fun buildLoupeSegmentForPage(
    layout: PdfPagesLayout,
    pageIndex: Int,
    basePageW: Float,
    docLeft: Float,
    docRight: Float,
    docTop: Float,
    docBottom: Float,
    totalDocW: Float,
    totalDocH: Float,
): MagnifierPageSegment? {
    val pageLeft = layout.pageLeftsPx[pageIndex]
    val pageRight = pageLeft + basePageW
    val interLeft = max(docLeft, pageLeft)
    val interRight = min(docRight, pageRight)

    val pageTop = layout.pageTopsPx[pageIndex]
    val pdfH = layout.pdfHeightsPx[pageIndex]
    val pageBottom = pageTop + pdfH
    val interTop = max(docTop, pageTop)
    val interBottom = min(docBottom, pageBottom)

    val hasWidth = interRight - interLeft >= MIN_TARGET_DIM * basePageW
    val hasHeight = interBottom - interTop >= MIN_TARGET_DIM * pdfH
    val segment =
        if (hasWidth && hasHeight) {
            val nxLeft = ((interLeft - pageLeft) / basePageW).coerceIn(0f, 1f)
            val nxRight = ((interRight - pageLeft) / basePageW).coerceIn(0f, 1f)
            val nyTop = ((interTop - pageTop) / pdfH).coerceIn(0f, 1f)
            val nyBottom = ((interBottom - pageTop) / pdfH).coerceIn(0f, 1f)
            val hasNormalizedSize = nxRight - nxLeft >= MIN_TARGET_DIM && nyBottom - nyTop >= MIN_TARGET_DIM
            if (hasNormalizedSize) {
                MagnifierPageSegment(
                    pageIndex = pageIndex,
                    targetOnPage = Rect(nxLeft, nyTop, nxRight, nyBottom),
                    panelLeftFrac = ((interLeft - docLeft) / totalDocW).coerceIn(0f, 1f),
                    panelRightFrac = ((interRight - docLeft) / totalDocW).coerceIn(0f, 1f),
                    panelTopFrac = ((interTop - docTop) / totalDocH).coerceIn(0f, 1f),
                    panelBottomFrac = ((interBottom - docTop) / totalDocH).coerceIn(0f, 1f),
                )
            } else {
                null
            }
        } else {
            null
        }

    return segment
}
