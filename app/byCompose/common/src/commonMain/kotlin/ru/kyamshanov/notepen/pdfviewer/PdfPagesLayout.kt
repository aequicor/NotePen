package ru.kyamshanov.notepen.pdfviewer

import androidx.compose.ui.geometry.Offset
import ru.kyamshanov.notepen.pdf.domain.model.PdfPageInfo

/**
 * Координатные пространства, с которыми работает viewer:
 *
 * - **Document space** (Y, units = "base px"): вертикальная стопка страниц,
 *   уложенных при единичном масштабе. Y = 0 — верх первой страницы.
 *   X = 0 соответствует левому краю страничной колонки шириной
 *   [Layout.basePageWidthPx]; страница горизонтально центрируется и
 *   занимает X ∈ [0, basePageWidthPx].
 * - **Viewport space**: экранные пиксели внутри Composable.
 * - **Page space** ([0..1]): нормализованные координаты внутри страницы;
 *   используются [DrawablePdfPage] для штрихов.
 *
 * Связь Document → Viewport:
 *
 *     viewport = pan + docPoint * zoom + (viewportCenterX − basePageWidth*zoom/2, 0)
 *
 * Горизонтальное центрирование скрыто в [pageVisualLeft]. Для упрощения
 * математики зум-якорения вокруг курсора удобнее работать в варианте, где
 * `pan` уже включает в себя сдвиг центрирования; в этом случае
 *
 *     viewport.x = pan.x + docPoint.x * zoom
 *     viewport.y = pan.y + docPoint.y * zoom
 *
 * Конвертация и обратные функции — ниже.
 */
internal data class PdfPagesLayout(
    val basePageWidthPx: Float,
    val pageHeightsPx: FloatArray,
    val pageTopsPx: FloatArray,
    val totalHeightPx: Float,
) {
    companion object {

        /**
         * Строит layout для [pages] при базовой ширине [basePageWidthPx].
         * Каждая страница укладывается высотой `basePageWidthPx / aspectRatio`;
         * страницы стекаются вертикально с [pageSpacingPx] между ними.
         */
        fun build(
            pages: List<PdfPageInfo>,
            basePageWidthPx: Float,
            pageSpacingPx: Float = 0f,
        ): PdfPagesLayout {
            val heights = FloatArray(pages.size)
            val tops = FloatArray(pages.size)
            var y = 0f
            for (i in pages.indices) {
                val aspect = pages[i].aspectRatio.takeIf { it > 0f } ?: 1f
                val h = basePageWidthPx / aspect
                heights[i] = h
                tops[i] = y
                y += h + if (i < pages.lastIndex) pageSpacingPx else 0f
            }
            return PdfPagesLayout(
                basePageWidthPx = basePageWidthPx,
                pageHeightsPx = heights,
                pageTopsPx = tops,
                totalHeightPx = y,
            )
        }
    }
}

/**
 * Чистая математика viewer'а — все функции детерминированы и тестируются
 * без Compose.
 */
internal object PdfViewerMath {

    /** Минимальный визуальный зум (25%). */
    const val MIN_ZOOM: Float = 0.25f

    /** Максимальный визуальный зум (800%). */
    const val MAX_ZOOM: Float = 8.0f

    /**
     * Cursor-anchored zoom. Возвращает новый `(pan, zoom)` такой, что
     * пиксель документа под [focus] остаётся под [focus] после смены
     * масштаба с [zoomOld] на `zoomNew`. [zoomNew] клампится в
     * [[MIN_ZOOM], [MAX_ZOOM]].
     *
     * Формула: из `viewport = pan + doc * zoom` имеем
     *     doc  = (focus − pan) / zoomOld
     *     panNew = focus − doc * zoomNew
     */
    fun zoomAroundFocus(
        focus: Offset,
        panOld: Offset,
        zoomOld: Float,
        zoomTarget: Float,
        minZoom: Float = MIN_ZOOM,
        maxZoom: Float = MAX_ZOOM,
    ): Pair<Offset, Float> {
        val zoomNew = zoomTarget.coerceIn(minZoom, maxZoom)
        if (zoomNew == zoomOld || zoomOld <= 0f) return panOld to zoomOld
        val docX = (focus.x - panOld.x) / zoomOld
        val docY = (focus.y - panOld.y) / zoomOld
        val panNew = Offset(
            x = focus.x - docX * zoomNew,
            y = focus.y - docY * zoomNew,
        )
        return panNew to zoomNew
    }

    /**
     * Диапазон страниц, хотя бы частично видимых во вьюпорте высотой
     * [viewportHeight] при текущих `pan.y` и `zoom`. Возвращает
     * `[firstIndex, lastIndex]` (оба inclusive). Если страниц нет —
     * `-1 to -1`.
     */
    fun visiblePageRange(
        layout: PdfPagesLayout,
        panY: Float,
        zoom: Float,
        viewportHeight: Float,
    ): IntRange {
        val n = layout.pageHeightsPx.size
        if (n == 0 || zoom <= 0f) return IntRange.EMPTY
        var first = -1
        var last = -1
        for (i in 0 until n) {
            val top = panY + layout.pageTopsPx[i] * zoom
            val bottom = top + layout.pageHeightsPx[i] * zoom
            if (bottom < 0f || top > viewportHeight) continue
            if (first == -1) first = i
            last = i
        }
        return if (first == -1) IntRange.EMPTY else first..last
    }

    /**
     * Индекс первой страницы, у которой верхняя граница уже либо ниже,
     * либо равна верху вьюпорта (т.е. "первая видимая" в смысле LazyColumn).
     * Если все страницы выше — возвращает `pages.lastIndex`. Если нет
     * страниц — `0`.
     */
    fun firstVisiblePageIndex(
        layout: PdfPagesLayout,
        panY: Float,
        zoom: Float,
    ): Int {
        val n = layout.pageHeightsPx.size
        if (n == 0) return 0
        // Ищем последнюю страницу, у которой top ≤ 0 (т.е. её верх уже
        // ушёл за верхнюю кромку вьюпорта или совпал с ней).
        var idx = 0
        for (i in 0 until n) {
            val top = panY + layout.pageTopsPx[i] * zoom
            if (top <= 0f) idx = i else break
        }
        return idx
    }

    /**
     * Смещение внутри страницы [pageIndex] (в визуальных пикселях текущего
     * масштаба), на которое страница "ушла" за верх вьюпорта. Это та же
     * семантика, что у `lazyListState.firstVisibleItemScrollOffset` —
     * отрицательное `pageTop_visual` → положительный offset.
     */
    fun pageScrollOffsetPx(
        layout: PdfPagesLayout,
        pageIndex: Int,
        panY: Float,
        zoom: Float,
    ): Int {
        if (pageIndex !in layout.pageHeightsPx.indices) return 0
        val top = panY + layout.pageTopsPx[pageIndex] * zoom
        return (-top).coerceAtLeast(0f).toInt()
    }

    /**
     * Pan, который ставит верх страницы [pageIndex] на [offsetPx] выше
     * верха вьюпорта (т.е. эквивалент `lazyListState.scrollToItem(index,
     * scrollOffset = offsetPx)`).
     */
    fun panForPageScroll(
        layout: PdfPagesLayout,
        pageIndex: Int,
        offsetPx: Int,
        zoom: Float,
        currentPanX: Float,
    ): Offset {
        if (pageIndex !in layout.pageHeightsPx.indices) {
            return Offset(currentPanX, 0f)
        }
        val pageTopDoc = layout.pageTopsPx[pageIndex]
        // Хотим: panY + pageTopDoc*zoom = -offsetPx → panY = -offsetPx − pageTopDoc*zoom
        val panY = -offsetPx.toFloat() - pageTopDoc * zoom
        return Offset(currentPanX, panY)
    }

    /**
     * Pan для центрирования страницы [pageIndex] по горизонтали и
     * прижатия её верха к верху вьюпорта (используется `fitToWidth` и
     * `fitToPage`).
     */
    fun panForPageTop(
        layout: PdfPagesLayout,
        pageIndex: Int,
        zoom: Float,
        viewportWidth: Float,
    ): Offset {
        val centeringX = (viewportWidth - layout.basePageWidthPx * zoom) / 2f
        val panY = if (pageIndex in layout.pageHeightsPx.indices) {
            -layout.pageTopsPx[pageIndex] * zoom
        } else 0f
        return Offset(centeringX, panY)
    }

    /**
     * Edge-clamp [pan] для интерактивного панорамирования
     * ([PdfViewerState.panBy]): держит документ хотя бы частично в
     * вьюпорте, когда он больше вьюпорта по оси. Если контент целиком
     * меньше вьюпорта, [pan] по этой оси НЕ трогаем — иначе любой scroll
     * (даже только вертикальный) затирает горизонтальное смещение,
     * выставленное cursor-anchored zoom'ом не по центру.
     *
     * Начальное центрирование делается отдельно в
     * [PdfViewerState.applyPendingInitialScrollIfNeeded] при первом
     * измерении viewport.
     */
    fun clampPan(
        pan: Offset,
        layout: PdfPagesLayout,
        zoom: Float,
        viewportSize: FloatSize,
    ): Offset {
        val contentW = layout.basePageWidthPx * zoom
        val contentH = layout.totalHeightPx * zoom
        val x = if (contentW <= viewportSize.width) {
            pan.x
        } else {
            val minX = viewportSize.width - contentW
            pan.x.coerceIn(minX, 0f)
        }
        val y = if (contentH <= viewportSize.height) {
            pan.y
        } else {
            val minY = viewportSize.height - contentH
            pan.y.coerceIn(minY, 0f)
        }
        return Offset(x, y)
    }

    /**
     * Кламп [pan] для зума: ТОЛЬКО центрирование по тем осям, на которых
     * контент целиком помещается в вьюпорт. Edge-clamp не делаем — он
     * ломает cursor anchoring у границ (точка под курсором сдвигается на
     * шаг clamp'а каждый тик, и при многократном wheel-зуме страница
     * "уплывает").
     */
    fun centeringClamp(
        pan: Offset,
        layout: PdfPagesLayout,
        zoom: Float,
        viewportSize: FloatSize,
    ): Offset {
        val contentW = layout.basePageWidthPx * zoom
        val contentH = layout.totalHeightPx * zoom
        val x = if (contentW <= viewportSize.width) {
            (viewportSize.width - contentW) / 2f
        } else pan.x
        val y = if (contentH <= viewportSize.height) {
            (viewportSize.height - contentH) / 2f
        } else pan.y
        return Offset(x, y)
    }

    /** Zoom такой, что страница [pageIndex] занимает всю ширину вьюпорта. */
    fun fitToWidthZoom(
        layout: PdfPagesLayout,
        viewportWidth: Float,
    ): Float {
        val base = layout.basePageWidthPx
        if (base <= 0f || viewportWidth <= 0f) return 1f
        return (viewportWidth / base).coerceIn(MIN_ZOOM, MAX_ZOOM)
    }

    /** Zoom такой, что страница [pageIndex] помещается целиком во вьюпорт. */
    fun fitToPageZoom(
        layout: PdfPagesLayout,
        pageIndex: Int,
        viewportSize: FloatSize,
    ): Float {
        val base = layout.basePageWidthPx
        if (base <= 0f || pageIndex !in layout.pageHeightsPx.indices) return 1f
        val pageH = layout.pageHeightsPx[pageIndex]
        val byWidth = viewportSize.width / base
        val byHeight = viewportSize.height / pageH
        return minOf(byWidth, byHeight).coerceIn(MIN_ZOOM, MAX_ZOOM)
    }
}

/**
 * Float-эквивалент `androidx.compose.ui.geometry.Size`, чтобы не тащить
 * Compose в чистую математику.
 */
internal data class FloatSize(val width: Float, val height: Float)
