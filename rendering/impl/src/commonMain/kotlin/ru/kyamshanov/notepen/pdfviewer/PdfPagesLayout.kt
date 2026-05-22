package ru.kyamshanov.notepen.pdfviewer

import androidx.compose.ui.geometry.Offset
import ru.kyamshanov.notepen.annotation.domain.model.PageExtent
import ru.kyamshanov.notepen.pdf.domain.model.PdfPageInfo

/**
 * Координатные пространства, с которыми работает viewer:
 *
 * - **Document space** (Y, units = "base px"): вертикальная стопка страничных
 *   **слотов**, уложенных при единичном масштабе. Y = 0 — верх слота первой
 *   страницы. X = 0 соответствует левому краю PDF-колонки шириной
 *   [Layout.basePageWidthPx]; PDF-колонка горизонтально центрируется. **Слот**
 *   страницы может быть шире/выше PDF за счёт [PageExtent] — slot.left =
 *   `extent.left * basePageWidthPx` (≤ 0), slot.right =
 *   `extent.right * basePageWidthPx` (≥ basePageWidthPx).
 * - **Viewport space**: экранные пиксели внутри Composable.
 * - **Page space** (нормализованные PDF-страничные координаты): `[0..1]` —
 *   сама PDF-страница; точки штрихов могут выходить за `[0..1]` в пределах
 *   текущего [PageExtent].
 *
 * Связь Document → Viewport:
 *
 *     viewport.x = pan.x + docPoint.x * zoom   (PDF при docX=0 ложится в pan.x)
 *     viewport.y = pan.y + docPoint.y * zoom
 *
 * Слот страницы i занимает в viewport прямоугольник:
 *
 *     left   = pan.x + extent.left  * basePageWidthPx * zoom
 *     top    = pan.y + pageTopsPx[i] * zoom
 *     width  = pageWidthsPx[i]  * zoom
 *     height = pageHeightsPx[i] * zoom
 *
 * PDF-битмап внутри слота располагается со сдвигом
 * `(-extent.left * basePageWidthPx, -extent.top * pdfHeightsPx[i])`.
 */
data class PdfPagesLayout(
    val basePageWidthPx: Float,
    val pageExtents: List<PageExtent>,
    /** PDF-высота каждой страницы (без учёта extent). */
    val pdfHeightsPx: FloatArray,
    /** Ширина слота каждой страницы (PDF × extent.width). */
    val pageWidthsPx: FloatArray,
    /**
     * Высота PDF-страницы в document space — `pdfHeightsPx[i]`. Стекинг
     * по PDF-размерам, чтобы рост extent одной страницы не сдвигал
     * соседние: слот может выезжать за границу PDF страницы наружу и
     * перекрывать (визуально) пустую зону вокруг соседних PDF.
     */
    val pageHeightsPx: FloatArray,
    /** Y верха PDF-страницы i в document space (по PDF-стекингу). */
    val pageTopsPx: FloatArray,
    /** Высота, занятая всеми PDF-страницами (без учёта расширений). */
    val totalHeightPx: Float,
    /** Левая граница самого «торчащего влево» слота (≤ 0). */
    val contentLeftPx: Float,
    /** Правая граница самого «торчащего вправо» слота (≥ basePageWidthPx). */
    val contentRightPx: Float,
    /** Верхняя граница самого «торчащего вверх» слота (≤ 0). */
    val contentTopPx: Float,
    /** Нижняя граница самого «торчащего вниз» слота (≥ totalHeightPx). */
    val contentBottomPx: Float,
) {
    companion object {

        /**
         * Строит layout для [pages] при базовой ширине [basePageWidthPx].
         * [extents] — extent каждой страницы (отсутствующие → [PageExtent.Pdf]).
         *
         * **Стекинг — строго по PDF-размерам.** Слоты могут выходить за
         * пределы своего PDF-«окна» (в зоне extent), но это не влияет на
         * pageTopsPx соседей: PDF-страница, на которой пользователь рисует,
         * не уплывает, и нижестоящие страницы не съезжают, когда extent
         * текущей растёт. Перекрытие slot'ов в зоне extent визуально
         * допускается: extent-поля по умолчанию пусты у соседей, штрихи
         * рендерятся в z-порядке размещения placeables.
         */
        fun build(
            pages: List<PdfPageInfo>,
            basePageWidthPx: Float,
            extents: List<PageExtent> = emptyList(),
            pageSpacingPx: Float = 0f,
        ): PdfPagesLayout {
            val n = pages.size
            val pdfHeights = FloatArray(n)
            val slotWidths = FloatArray(n)
            val tops = FloatArray(n)
            val ext = List(n) { i -> extents.getOrNull(i) ?: PageExtent.Pdf }

            var y = 0f
            var minLeft = 0f
            var maxRight = if (n > 0) basePageWidthPx else 0f
            var minTop = 0f
            var maxBottom = 0f
            for (i in pages.indices) {
                val aspect = pages[i].aspectRatio.takeIf { it > 0f } ?: 1f
                val e = ext[i]
                val pdfH = basePageWidthPx / aspect
                pdfHeights[i] = pdfH
                slotWidths[i] = basePageWidthPx * e.width
                tops[i] = y
                // Границы слота в document space — для clamp'а вьюпорта.
                minTop = minOf(minTop, y + e.top * pdfH)
                maxBottom = maxOf(maxBottom, y + e.bottom * pdfH)
                y += pdfH + if (i < pages.lastIndex) pageSpacingPx else 0f
                minLeft = minOf(minLeft, e.left * basePageWidthPx)
                maxRight = maxOf(maxRight, e.right * basePageWidthPx)
            }
            if (n == 0) maxBottom = 0f
            return PdfPagesLayout(
                basePageWidthPx = basePageWidthPx,
                pageExtents = ext,
                pdfHeightsPx = pdfHeights,
                pageWidthsPx = slotWidths,
                pageHeightsPx = pdfHeights,
                pageTopsPx = tops,
                totalHeightPx = y,
                contentLeftPx = minLeft,
                contentRightPx = maxRight,
                contentTopPx = minTop,
                contentBottomPx = maxBottom,
            )
        }
    }
}

/**
 * Чистая математика viewer'а — все функции детерминированы и тестируются
 * без Compose.
 */
object PdfViewerMath {

    /** Минимальный визуальный зум (25%). */
    const val MIN_ZOOM: Float = 0.25f

    /** Максимальный визуальный зум (800%). */
    const val MAX_ZOOM: Float = 8.0f

    /**
     * Дополнительный «оверскролл»-буфер для горизонтального панорамирования,
     * когда контент шире вьюпорта: разрешает увести pan.x на эту долю ширины
     * PDF-колонки за строгую границу контента. Растёт с зумом, давая запас
     * хода на больших масштабах, чтобы удобно дотянуться до полей страницы.
     * На осях, где контент помещается в экран, буфер не применяется — страница
     * остаётся в пределах вьюпорта.
     */
    private const val OVERSCROLL_PAGE_FRACTION = 0.25f

    /**
     * Потолок размера слота страницы в пикселях для layout-фазы. Compose
     * `Constraints` не может упаковать сторону >~32767 при второй большой
     * стороне, поэтому страница НИКОГДА не раскладывается крупнее этого
     * предела: визуальный зум сверх него добавляется GPU-трансформой
     * (`residualScale`), а не размером layout'а. 16000 — с запасом ниже лимита.
     */
    const val MAX_LAYOUT_DIM_PX: Float = 16_000f

    /**
     * Максимальный визуальный зум, который ещё можно «впечь» в размер layout'а
     * для [layout], не превысив [MAX_LAYOUT_DIM_PX] по большей стороне любого
     * слота. Адаптивно к размеру страниц: для крупных страниц cap ниже.
     * Возвращает значение в [[1f], [MAX_ZOOM]].
     */
    fun layoutZoomCap(layout: PdfPagesLayout): Float {
        var maxDim = layout.basePageWidthPx
        val n = layout.pageWidthsPx.size
        for (i in 0 until n) {
            val w = layout.pageWidthsPx[i]
            val h = layout.pdfHeightsPx[i] * layout.pageExtents[i].height
            if (w > maxDim) maxDim = w
            if (h > maxDim) maxDim = h
        }
        return if (maxDim <= 0f) MAX_ZOOM else (MAX_LAYOUT_DIM_PX / maxDim).coerceIn(1f, MAX_ZOOM)
    }

    /** Зум, применяемый к размеру/растеризации (capped). */
    fun layoutZoom(zoom: Float, cap: Float): Float = zoom.coerceAtMost(cap)

    /**
     * Остаточный множитель зума сверх [layoutZoom], применяемый GPU-трансформой.
     * `1f`, пока `zoom <= cap`; иначе `zoom / cap`.
     */
    fun residualScale(zoom: Float, cap: Float): Float {
        val lz = layoutZoom(zoom, cap)
        return if (lz <= 0f) 1f else zoom / lz
    }

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
            // Видимость по границам slot'а — extent может выводить ink за
            // PDF, и страница должна оставаться «активной», пока хоть какая-то
            // часть extent попадает во вьюпорт.
            val ext = layout.pageExtents[i]
            val pdfH = layout.pdfHeightsPx[i]
            val top = panY + (layout.pageTopsPx[i] + ext.top * pdfH) * zoom
            val bottom = panY + (layout.pageTopsPx[i] + ext.bottom * pdfH) * zoom
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
     * Pan для центрирования **PDF-колонки** по горизонтали и прижатия верха
     * страницы [pageIndex] к верху вьюпорта (используется `fitToWidth` и
     * `fitToPage`). Центрируется именно PDF (не слот) — иначе видимая
     * страница «прыгает» вбок при росте extent.
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
     * ([PdfViewerState.panBy]).
     *
     * По каждой оси:
     * - Контент **помещается** во вьюпорт: pan ограничен так, чтобы контент
     *   целиком оставался в экране — его можно свободно сдвигать в пределах
     *   пустого поля (drag-to-pan / скролл), но не вытолкнуть за край.
     * - Контент **переполняет** вьюпорт: краевой clamp с дополнительным
     *   оверскролл-буфером ([OVERSCROLL_PAGE_FRACTION] от ширины PDF-колонки)
     *   по обеим осям — чтобы drag/wheel overscroll мог немного вытянуть
     *   документ за край до срабатывания пружины возврата.
     *
     * Учитывает [PdfPagesLayout.contentLeftPx] / [PdfPagesLayout.contentRightPx]
     * — границы слотов с учётом [PageExtent], не только PDF-колонки.
     *
     * [horizontalBuffer] / [verticalBuffer] — размер оверскролл-буфера по
     * соответствующей оси (px). По умолчанию — доля ширины PDF-колонки
     * ([OVERSCROLL_PAGE_FRACTION]); вызывающий может задать иной (например,
     * половину вьюпорта на touch-платформе).
     */
    fun clampPan(
        pan: Offset,
        layout: PdfPagesLayout,
        zoom: Float,
        viewportSize: FloatSize,
        horizontalBuffer: Float = layout.basePageWidthPx * zoom * OVERSCROLL_PAGE_FRACTION,
        verticalBuffer: Float = layout.basePageWidthPx * zoom * OVERSCROLL_PAGE_FRACTION,
    ): Offset {
        val contentW = (layout.contentRightPx - layout.contentLeftPx) * zoom
        val contentH = (layout.contentBottomPx - layout.contentTopPx) * zoom
        val x = clampAxis(
            value = pan.x,
            edgeA = viewportSize.width - layout.contentRightPx * zoom,
            // `0f - x` вместо `-x`, чтобы для contentLeftPx == 0 получить +0f,
            // а не -0f (различимы Float.equals и ассертами в тестах).
            edgeB = 0f - layout.contentLeftPx * zoom,
            overflow = contentW > viewportSize.width,
            buffer = horizontalBuffer,
        )
        val y = clampAxis(
            value = pan.y,
            edgeA = viewportSize.height - layout.contentBottomPx * zoom,
            edgeB = 0f - layout.contentTopPx * zoom,
            overflow = contentH > viewportSize.height,
            buffer = verticalBuffer,
        )
        return Offset(x, y)
    }

    /**
     * Кламп одной оси [pan]. [edgeA] / [edgeB] — два положения pan, при которых
     * соответствующие края контента совпадают с краями вьюпорта (порядок не
     * важен). При `overflow == false` контент держится целиком в экране; при
     * `overflow == true` — краевой clamp с оверскролл-[buffer].
     */
    private fun clampAxis(
        value: Float,
        edgeA: Float,
        edgeB: Float,
        overflow: Boolean,
        buffer: Float,
    ): Float {
        val lo = minOf(edgeA, edgeB)
        val hi = maxOf(edgeA, edgeB)
        return if (overflow) value.coerceIn(lo - buffer, hi + buffer) else value.coerceIn(lo, hi)
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
        val contentW = (layout.contentRightPx - layout.contentLeftPx) * zoom
        val contentH = (layout.contentBottomPx - layout.contentTopPx) * zoom
        val x = if (contentW <= viewportSize.width) {
            val midContent = (layout.contentLeftPx + layout.contentRightPx) / 2f * zoom
            viewportSize.width / 2f - midContent
        } else pan.x
        val y = if (contentH <= viewportSize.height) {
            val midContent = (layout.contentTopPx + layout.contentBottomPx) / 2f * zoom
            viewportSize.height / 2f - midContent
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
data class FloatSize(val width: Float, val height: Float)
