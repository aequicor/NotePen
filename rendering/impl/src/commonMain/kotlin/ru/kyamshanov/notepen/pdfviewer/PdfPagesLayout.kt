package ru.kyamshanov.notepen.pdfviewer

import androidx.compose.ui.geometry.Offset
import ru.kyamshanov.notepen.annotation.domain.model.PageExtent
import ru.kyamshanov.notepen.pdf.domain.model.PdfPageInfo

/**
 * Режим раскладки страниц (FEATURE #5 — книжный разворот / two-up).
 *
 * - [SINGLE] — одна вертикальная стопка центрированных страниц (поведение по
 *   умолчанию, как было до фичи): X-левый край каждой PDF-страницы = 0.
 * - [SPREAD] — две соседние ЛОГИЧЕСКИЕ страницы кладутся бок-о-бок в один Y-ряд:
 *   пара `(2k, 2k+1)` делит верх (`pageTopsPx` одинаков), левая занимает левую
 *   половину, правая — правую, между ними — зазор-«корешок» [SPREAD_GUTTER_PX].
 *   Висячая нечётная последняя страница занимает левую половину одна. Высота
 *   ряда = max высот пары. Это **отдельный** от FEATURE #4 (split) и режима
 *   чтения (reflow) механизм: spread пейринг идёт по уже ЛОГИЧЕСКИМ страницам.
 *
 *   MVP-упрощение: жёсткий left-first пейринг (страница 0 — слева, 1 — справа).
 *   Никакого odd/even-spine выравнивания обложки.
 */
enum class SpreadMode {
    /** Одна вертикальная стопка центрированных страниц. */
    SINGLE,

    /** Две соседние логические страницы бок-о-бок (книжный разворот). */
    SPREAD,
}

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
    /**
     * X левого края PDF-колонки страницы i в document space (px).
     *
     * В [SpreadMode.SINGLE] всегда `0f` — единая центрированная колонка (X=0 —
     * её левый край для ВСЕХ страниц, центрирование делает `pan.x`). В
     * [SpreadMode.SPREAD] левая страница пары имеет `0f`, правая —
     * `basePageWidthPx + `[SPREAD_GUTTER_PX] (ряд центрируется целиком через
     * `pan.x`, см. [PdfViewerMath.panForPageTop]). Слот страницы со
     * [PageExtent] сдвигается на `pageLeftsPx[i] + extent.left * basePageWidthPx`.
     */
    val pageLeftsPx: FloatArray,
    /** Режим раскладки (одностраничный / разворот). */
    val spreadMode: SpreadMode,
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
         * Зазор-«корешок» между левой и правой страницами разворота
         * ([SpreadMode.SPREAD]) в document-space пикселях при `zoom = 1`.
         */
        const val SPREAD_GUTTER_PX: Float = 16f

        /**
         * Строит layout для [pages] при базовой ширине [basePageWidthPx].
         * [extents] — extent каждой страницы (отсутствующие → [PageExtent.Pdf]).
         * [spreadMode] выбирает одностраничную стопку ([SpreadMode.SINGLE]) или
         * книжный разворот ([SpreadMode.SPREAD], FEATURE #5).
         *
         * **Стекинг — строго по PDF-размерам.** Слоты могут выходить за
         * пределы своего PDF-«окна» (в зоне extent), но это не влияет на
         * pageTopsPx соседей: PDF-страница, на которой пользователь рисует,
         * не уплывает, и нижестоящие страницы не съезжают, когда extent
         * текущей растёт. Перекрытие slot'ов в зоне extent визуально
         * допускается: extent-поля по умолчанию пусты у соседей, штрихи
         * рендерятся в z-порядке размещения placeables.
         *
         * **Разворот ([SpreadMode.SPREAD]).** Пары `(2k, 2k+1)` кладутся в один
         * Y-ряд: левая страница на X=0, правая на `basePageWidthPx +`
         * [SPREAD_GUTTER_PX]; верх обеих — одинаковый, следующий ряд начинается
         * на `max` высот пары. Висячая нечётная последняя — левая половина одна.
         * Штриховые координаты не меняются (страницы по-прежнему `[0..1]`);
         * разнесение по X делает только раскладка через [pageLeftsPx].
         */
        @Suppress("LongMethod", "CyclomaticComplexMethod")
        fun build(
            pages: List<PdfPageInfo>,
            basePageWidthPx: Float,
            extents: List<PageExtent> = emptyList(),
            pageSpacingPx: Float = 0f,
            spreadMode: SpreadMode = SpreadMode.SINGLE,
        ): PdfPagesLayout {
            val n = pages.size
            val pdfHeights = FloatArray(n)
            val slotWidths = FloatArray(n)
            val tops = FloatArray(n)
            val lefts = FloatArray(n)
            val ext = List(n) { i -> extents.getOrNull(i) ?: PageExtent.Pdf }
            // X правой страницы пары — сразу за левой колонкой + корешок.
            val rightColumnX = basePageWidthPx + SPREAD_GUTTER_PX

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
                if (spreadMode == SpreadMode.SPREAD) {
                    // Чётный индекс — левая страница пары (X=0), нечётный — правая.
                    val isRight = i % 2 == 1
                    lefts[i] = if (isRight) rightColumnX else 0f
                    // Y продвигаем только после правой (или единственной висячей
                    // левой) страницы пары: ряд высотой = max высот пары.
                    if (isRight) {
                        val pairTop = tops[i - 1]
                        val rowHeight = maxOf(pdfHeights[i - 1], pdfH)
                        tops[i] = pairTop
                        y = pairTop + rowHeight + if (i < pages.lastIndex) pageSpacingPx else 0f
                    } else if (i == pages.lastIndex) {
                        y += pdfH
                    }
                    // Иначе (левая страница пары, не последняя) — Y продвинет
                    // следующая итерация (правая).
                } else {
                    lefts[i] = 0f
                    y += pdfH + if (i < pages.lastIndex) pageSpacingPx else 0f
                }
                // Границы слота в document space — для clamp'а вьюпорта (с учётом
                // X-позиции страницы в развороте).
                minTop = minOf(minTop, tops[i] + e.top * pdfH)
                maxBottom = maxOf(maxBottom, tops[i] + e.bottom * pdfH)
                minLeft = minOf(minLeft, lefts[i] + e.left * basePageWidthPx)
                maxRight = maxOf(maxRight, lefts[i] + e.right * basePageWidthPx)
            }
            if (n == 0) maxBottom = 0f
            return PdfPagesLayout(
                basePageWidthPx = basePageWidthPx,
                pageExtents = ext,
                pdfHeightsPx = pdfHeights,
                pageWidthsPx = slotWidths,
                pageHeightsPx = pdfHeights,
                pageTopsPx = tops,
                pageLeftsPx = lefts,
                spreadMode = spreadMode,
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
     * Во сколько раз double-tap «приближает» относительно fit-width — целевой
     * зум для чтения, как в Chrome / Photos / Acrobat (double-tap-to-zoom).
     */
    const val DOUBLE_TAP_ZOOM_FACTOR: Float = 2.5f

    /**
     * Допуск, выше которого текущий зум считается «уже приближённым»: double-tap
     * в этом состоянии возвращает к fit-width, иначе — приближает. Небольшой
     * запас над fit-width гасит дрожание float у самого порога.
     */
    const val DOUBLE_TAP_FIT_EPSILON: Float = 1.05f

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
     * Доля размера листа, которая обязана остаться видимой при «свободном»
     * перетаскивании (см. [clampPanFree]): когда лист помещается во вьюпорт, его
     * можно увести почти за край, но не меньше этой доли на экране — чтобы лист
     * нельзя было потерять полностью.
     */
    private const val FREE_PAN_MIN_VISIBLE_FRACTION = 0.25f

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
    fun layoutZoom(
        zoom: Float,
        cap: Float,
    ): Float = zoom.coerceAtMost(cap)

    /**
     * Остаточный множитель зума сверх [layoutZoom], применяемый GPU-трансформой.
     * `1f`, пока `zoom <= cap`; иначе `zoom / cap`.
     */
    fun residualScale(
        zoom: Float,
        cap: Float,
    ): Float {
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
        val panNew =
            Offset(
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
     * Левая страница пары разворота для логического [index] в
     * [SpreadMode.SPREAD]: чётный индекс — он сам, нечётный (правая половина
     * пары) — `index - 1`. В [SpreadMode.SINGLE] — тождественно [index].
     * Навигация/счётчик/якорь в развороте «садятся» на левую страницу пары.
     */
    fun spreadLeftPageOf(
        layout: PdfPagesLayout,
        index: Int,
    ): Int = if (layout.spreadMode == SpreadMode.SPREAD && index % 2 == 1) index - 1 else index

    /**
     * Диапазон страниц для РАСТЕРИЗАЦИИ: видимый [visible] плюс буфер
     * [bufferPages] с каждой стороны, оклампленный в `[0, pageCount-1]`.
     *
     * В [SpreadMode.SPREAD] границы выравниваются по разворотам (низ — на левую
     * страницу пары, верх — на правую), а буфер считается **целыми разворотами**
     * (`bufferPages` разворотов = `bufferPages × 2` страниц). Иначе при буфере в
     * 1 страницу переход к соседнему развороту всегда заставал бы ровно одну из
     * его двух страниц нерастеризованной — та «доезжала» бы на лету (см. F4).
     * В [SpreadMode.SINGLE] — обычное расширение на [bufferPages] страниц.
     */
    fun bufferedRenderRange(
        layout: PdfPagesLayout,
        visible: IntRange,
        bufferPages: Int,
        pageCount: Int,
    ): IntRange {
        if (visible.isEmpty() || pageCount == 0) return IntRange.EMPTY
        val lastIdx = pageCount - 1
        return if (layout.spreadMode == SpreadMode.SPREAD) {
            val visFirst = spreadLeftPageOf(layout, visible.first.coerceIn(0, lastIdx))
            val visLastRight = (spreadLeftPageOf(layout, visible.last.coerceIn(0, lastIdx)) + 1).coerceAtMost(lastIdx)
            val first = (visFirst - bufferPages * 2).coerceAtLeast(0)
            val last = (visLastRight + bufferPages * 2).coerceAtMost(lastIdx)
            first..last
        } else {
            val first = (visible.first - bufferPages).coerceAtLeast(0)
            val last = (visible.last + bufferPages).coerceAtMost(lastIdx)
            first..last
        }
    }

    /**
     * Порядок растеризации страниц окна [window]: СНАЧАЛА видимые [visible],
     * затем буферные (опережающие/отстающие). Под сериализующим растеризатором
     * (process-global pdfium-лок на Android, PDFBox на JVM) отправленное раньше
     * раньше и займёт очередь — поэтому видимая страница (в развороте — ОБЕ
     * страницы пары) рисуется до буфера, а не ждёт, пока отрисуется опережающая
     * буферная страница соседнего разворота (корень F4: буфер-впереди в цикле
     * `first..last` блокировал видимое под локом).
     */
    fun renderPriorityOrder(
        window: IntRange,
        visible: IntRange,
    ): List<Int> {
        if (window.isEmpty()) return emptyList()
        val vis = window.filter { it in visible }
        val buf = window.filter { it !in visible }
        return vis + buf
    }

    /**
     * Индекс первой страницы, у которой верхняя граница уже либо ниже,
     * либо равна верху вьюпорта (т.е. "первая видимая" в смысле LazyColumn).
     * Если все страницы выше — возвращает `pages.lastIndex`. Если нет
     * страниц — `0`. В [SpreadMode.SPREAD] правая страница пары делит верх с
     * левой — приводим результат к ЛЕВОЙ странице пары (она и есть «текущая»).
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
        return spreadLeftPageOf(layout, idx)
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
     * Ширина одного «ряда» раскладки в document-space px при `zoom = 1`:
     * [SpreadMode.SINGLE] — одна PDF-колонка ([PdfPagesLayout.basePageWidthPx]);
     * [SpreadMode.SPREAD] — две колонки + корешок
     * (`2 * basePageWidthPx + `[PdfPagesLayout.SPREAD_GUTTER_PX]). По ней
     * центрируется горизонтально весь разворот.
     */
    fun rowWidthPx(layout: PdfPagesLayout): Float =
        if (layout.spreadMode == SpreadMode.SPREAD) {
            layout.basePageWidthPx * 2f + PdfPagesLayout.SPREAD_GUTTER_PX
        } else {
            layout.basePageWidthPx
        }

    /**
     * Pan для центрирования **ряда раскладки** по горизонтали и прижатия верха
     * страницы [pageIndex] к верху вьюпорта (используется `fitToWidth` и
     * `fitToPage`). В развороте центрируется вся пара ([rowWidthPx]); в
     * одностраничном — PDF-колонка (не слот) — иначе видимая страница «прыгает»
     * вбок при росте extent.
     */
    fun panForPageTop(
        layout: PdfPagesLayout,
        pageIndex: Int,
        zoom: Float,
        viewportWidth: Float,
    ): Offset {
        val centeringX = (viewportWidth - rowWidthPx(layout) * zoom) / 2f
        val panY =
            if (pageIndex in layout.pageHeightsPx.indices) {
                -layout.pageTopsPx[pageIndex] * zoom
            } else {
                0f
            }
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
        val x =
            clampAxis(
                value = pan.x,
                edgeA = viewportSize.width - layout.contentRightPx * zoom,
                // `0f - x` вместо `-x`, чтобы для contentLeftPx == 0 получить +0f,
                // а не -0f (различимы Float.equals и ассертами в тестах).
                edgeB = 0f - layout.contentLeftPx * zoom,
                overflow = contentW > viewportSize.width,
                buffer = horizontalBuffer,
            )
        val y =
            clampAxis(
                value = pan.y,
                edgeA = viewportSize.height - layout.contentBottomPx * zoom,
                edgeB = 0f - layout.contentTopPx * zoom,
                overflow = contentH > viewportSize.height,
                buffer = verticalBuffer,
            )
        return Offset(x, y)
    }

    /**
     * Кламп [pan] для **свободного** drag-перетаскивания (см.
     * [PdfViewerState.panGestureBy]).
     *
     * Отличие от [clampPan]: когда лист (PDF-колонка / ряд раскладки, а НЕ слот
     * с [PageExtent]) **помещается** в вьюпорт по оси, его разрешено увести почти
     * за край — на экране обязана остаться лишь доля [FREE_PAN_MIN_VISIBLE_FRACTION]
     * листа. Это и есть «свободное перемещение»: лист можно поставить куда угодно,
     * без возврата к центру/краю. По **переполняющей** оси (зумленный лист) —
     * прежнее поведение [clampPan] (краевой clamp + оверскролл-буфер), чтобы не
     * ломать настроенный overscroll на больших масштабах.
     *
     * Границы по листу берутся от колонки `[0, `[rowWidthPx]`]` (и
     * `[0, totalHeightPx]`), а не от слота: правый «вылет» extent'а у скан-страниц
     * не должен смещать свободный ход.
     */
    fun clampPanFree(
        pan: Offset,
        layout: PdfPagesLayout,
        zoom: Float,
        viewportSize: FloatSize,
        horizontalBuffer: Float = layout.basePageWidthPx * zoom * OVERSCROLL_PAGE_FRACTION,
        verticalBuffer: Float = layout.basePageWidthPx * zoom * OVERSCROLL_PAGE_FRACTION,
    ): Offset {
        val rowW = rowWidthPx(layout) * zoom
        val totalH = layout.totalHeightPx * zoom
        val slot = clampPan(pan, layout, zoom, viewportSize, horizontalBuffer, verticalBuffer)
        val x =
            if (rowW <= viewportSize.width) {
                val keep = rowW * FREE_PAN_MIN_VISIBLE_FRACTION
                pan.x.coerceIn(keep - rowW, viewportSize.width - keep)
            } else {
                slot.x
            }
        val y =
            if (totalH <= viewportSize.height) {
                val keep = totalH * FREE_PAN_MIN_VISIBLE_FRACTION
                pan.y.coerceIn(keep - totalH, viewportSize.height - keep)
            } else {
                slot.y
            }
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
     * **PDF-лист** целиком помещается в вьюпорт. Edge-clamp не делаем — он
     * ломает cursor anchoring у границ (точка под курсором сдвигается на
     * шаг clamp'а каждый тик, и при многократном wheel-зуме страница
     * "уплывает").
     *
     * Центрируется именно ряд раскладки `[0, `[rowWidthPx]`]` ×
     * `[0, totalHeightPx]` (в развороте — пара колонок + корешок), а **не**
     * слот с [PageExtent]: иначе штрих/надпись, вылезшая за лист, утягивала бы
     * сам лист от центра.
     */
    fun centeringClamp(
        pan: Offset,
        layout: PdfPagesLayout,
        zoom: Float,
        viewportSize: FloatSize,
    ): Offset {
        val pdfW = rowWidthPx(layout) * zoom
        val pdfH = layout.totalHeightPx * zoom
        val x = if (pdfW <= viewportSize.width) (viewportSize.width - pdfW) / 2f else pan.x
        val y = if (pdfH <= viewportSize.height) (viewportSize.height - pdfH) / 2f else pan.y
        return Offset(x, y)
    }

    /**
     * Целевой зум для double-tap-to-zoom (как в Chrome / Photos / Acrobat).
     *
     * [availableWidthPx] — ширина свободной области вьюпорта (за вычетом
     * тулрейла/меню). Если текущий зум [currentZoom] заметно выше fit-width
     * (`> fit × `[DOUBLE_TAP_FIT_EPSILON]) — возвращаем fit-width (тап
     * «отдаляет»), иначе — `fit × `[DOUBLE_TAP_ZOOM_FACTOR] («приближаем» для
     * чтения). Результат кламп'ится в [[MIN_ZOOM], [MAX_ZOOM]].
     */
    fun doubleTapTargetZoom(
        currentZoom: Float,
        basePageWidthPx: Float,
        availableWidthPx: Float,
    ): Float {
        if (basePageWidthPx <= 0f) return currentZoom
        val available = availableWidthPx.coerceAtLeast(1f)
        val fitZoom = (available / basePageWidthPx).coerceIn(MIN_ZOOM, MAX_ZOOM)
        return if (currentZoom > fitZoom * DOUBLE_TAP_FIT_EPSILON) {
            fitZoom
        } else {
            (fitZoom * DOUBLE_TAP_ZOOM_FACTOR).coerceIn(MIN_ZOOM, MAX_ZOOM)
        }
    }

    /**
     * Pan для double-tap «по ширине», укладывающий ряд раскладки в **свободную
     * область** вьюпорта за вычетом плавающих панелей: тулрейла/настроек слева
     * ([insetStartPx]), счётчика страниц сверху ([insetTopPx]) и панели справа
     * ([insetEndPx]). Ряд ([rowWidthPx] — одна PDF-колонка в одностраничном,
     * пара колонок + корешок в развороте) центрируется по горизонтали внутри
     * этой области, а верх страницы [pageIndex] прижимается к её верхней кромке —
     * чтобы страница не уходила под рельсу и под счётчик.
     */
    fun panForFitWidth(
        layout: PdfPagesLayout,
        pageIndex: Int,
        zoom: Float,
        viewportWidth: Float,
        insetStartPx: Float,
        insetTopPx: Float,
        insetEndPx: Float,
    ): Offset {
        val availableWidth = (viewportWidth - insetStartPx - insetEndPx).coerceAtLeast(1f)
        val centeringX = insetStartPx + (availableWidth - rowWidthPx(layout) * zoom) / 2f
        val panY =
            insetTopPx +
                if (pageIndex in layout.pageHeightsPx.indices) {
                    -layout.pageTopsPx[pageIndex] * zoom
                } else {
                    0f
                }
        return Offset(centeringX, panY)
    }

    /**
     * Zoom такой, что ряд раскладки занимает всю ширину вьюпорта. В развороте
     * это укладывает ОБЕ страницы пары + корешок в ширину экрана (каждая
     * страница — примерно полэкрана), как книга на широком экране.
     */
    fun fitToWidthZoom(
        layout: PdfPagesLayout,
        viewportWidth: Float,
    ): Float {
        val base = rowWidthPx(layout)
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
data class FloatSize(
    val width: Float,
    val height: Float,
)
