package ru.kyamshanov.notepen.reflow.lattice

import ru.kyamshanov.notepen.reflow.RawGlyph
import ru.kyamshanov.notepen.reflow.RawPage
import ru.kyamshanov.notepen.reflow.TableNoiseGuard
import ru.kyamshanov.notepen.reflow.api.PageBitmapProvider
import ru.kyamshanov.notepen.reflow.api.PageRaster
import ru.kyamshanov.notepen.reflow.api.ReflowBlock
import ru.kyamshanov.notepen.reflow.api.ReflowDocument
import ru.kyamshanov.notepen.reflow.api.ReflowRect
import ru.kyamshanov.notepen.reflow.api.SourceSpan

/**
 * Post-pass над [ReflowDocument]: для каждой [ReflowBlock.Figure] с
 * `wasTableFallback = true` (то есть Stream-фолбэк из low-conf таблицы) пытается
 * восстановить таблицу через Lattice — рендерит страницу, ищет грид-линии,
 * мапит глифы в ячейки сетки.
 *
 * Архитектурно отделён от [ru.kyamshanov.notepen.reflow.ReflowAssembler]: тот работает
 * без I/O (purely на сырых глифах), а рефайнер требует рендер страницы через
 * suspend-колбэк ([PageBitmapProvider]). Это позволяет ходить мимо Lattice совсем
 * (если caller не передал рендер) — на устройствах с дорогой растеризацией.
 *
 * Slice 2b.1: только алгоритм + контракт колбэка. Plumbing к
 * [ru.kyamshanov.notepen.rendering.api.PageRasterizer] и `BuildReflowReadingUseCase` —
 * Slice 2c.
 */
internal object LatticeTableRefiner {
    /**
     * Целевая ширина рендера страницы для Lattice. 1200 px — компромисс между
     * точностью поиска грид-линий (минимум 5% = 60 px достаточно для типичной
     * PDF-линии) и стоимостью растеризации (1200×~1700 ≈ 2 МП).
     */
    const val DEFAULT_TARGET_WIDTH_PX: Int = 1200

    /**
     * Минимум cells, чтобы рискнуть строить Table. 4 = простейшая 2×2 таблица.
     * Если Lattice нашёл 1–3 ячейки — это скорее шум (обрывок линии-разделителя
     * рядом с заголовком и т.п.), оставляем Figure-кроп.
     */
    private const val MIN_CELLS_FOR_TABLE = 4

    /**
     * Допуск группировки ячеек в один ряд: ячейки с верхней Y-координатой в пределах
     * этой доли высоты bbox идут в один ряд. Защищает от мелких float-расхождений
     * после конверсии px→pt и от не-идеально-параллельных линий рамки.
     */
    private const val ROW_GROUP_TOLERANCE_FRACTION = 0.05f

    /**
     * Масштаб конверсии PDF-points → integer для vector-path Lattice. 10 = 0.1 pt
     * на единицу. PDF-страница A4 (~595×842 pt) → ~5950×8420 int — достаточно
     * точно для кластеризации линий, и compact: int helps cluster-merge perform.
     */
    private const val VECTOR_INT_SCALE = 10f

    /**
     * Минимальная длина vector-линии (доля стороны страницы) — соответствует
     * `LatticeTableDetector.MIN_LINE_LENGTH_FRACTION = 0.05`, чтобы две стратегии
     * фильтровали одинаковые «обрывки» линий.
     */
    private const val VECTOR_MIN_LINE_FRACTION = 0.05f

    /**
     * Допуск кластеризации параллельных линий — те же 0.5% стороны, что и в
     * растровом пути [LatticeTableDetector.LINE_CLUSTER_TOLERANCE_FRACTION].
     */
    private const val VECTOR_CLUSTER_TOLERANCE_FRACTION = 0.005f

    suspend fun refine(
        document: ReflowDocument,
        rawPages: List<RawPage>,
        renderPage: PageBitmapProvider,
        targetWidthPx: Int = DEFAULT_TARGET_WIDTH_PX,
    ): ReflowDocument {
        val candidates =
            document.blocks
                .withIndex()
                .filter { (_, b) -> b is ReflowBlock.Figure && b.wasTableFallback }
        if (candidates.isEmpty()) return document

        val byPage =
            candidates.groupBy { (_, b) -> (b as ReflowBlock.Figure).pageIndex }
        val newBlocks = document.blocks.toMutableList()

        for ((pageIndex, indexedFigures) in byPage) {
            val context = preparePageContext(pageIndex, rawPages, renderPage, targetWidthPx)
            if (context != null) {
                indexedFigures.forEach { (idx, block) ->
                    val figure = block as ReflowBlock.Figure
                    val table =
                        reconstructTableForFigure(
                            figure = figure,
                            grid = context.grid,
                            page = context.page,
                            intSpaceWidth = context.bitmap.widthPx,
                            intSpaceHeight = context.bitmap.heightPx,
                        )
                    if (table != null) newBlocks[idx] = table
                }
            }
        }
        return ReflowDocument(kind = document.kind, blocks = newBlocks)
    }

    /**
     * Альтернативный путь Lattice через **векторные грид-линии** страницы:
     * вместо растеризации и морфологии берёт `RawPage.vectorLines` напрямую,
     * кластеризует их в `LatticeTableDetector.GridLine` и пытается восстановить
     * таблицы. Работает без `PageBitmapProvider` — для PDF с нарисованными
     * рамками таблиц это быстрее (нет PDFRenderer) и точнее (нет потерь от
     * раундинга при бинаризации anti-aliased линий).
     *
     * Применять до/вместо [refine]: вызывается из `extract()` JVM-экстрактора
     * после assemble, до возвращения документа. Если для всех страниц с
     * fallback Figure нет vector lines — no-op.
     */
    fun refineFromVectorLines(
        document: ReflowDocument,
        rawPages: List<RawPage>,
    ): ReflowDocument {
        val candidates =
            document.blocks
                .withIndex()
                .filter { (_, b) -> b is ReflowBlock.Figure && b.wasTableFallback }
        if (candidates.isEmpty()) return document

        val byPage = candidates.groupBy { (_, b) -> (b as ReflowBlock.Figure).pageIndex }
        val newBlocks = document.blocks.toMutableList()
        for ((pageIndex, indexedFigures) in byPage) {
            val rawPage = rawPages.getOrNull(pageIndex) ?: continue
            if (rawPage.vectorLines.isEmpty()) continue
            val grid = buildGridFromVectors(rawPage) ?: continue
            val intWidth = (rawPage.widthPt * VECTOR_INT_SCALE).toInt().coerceAtLeast(1)
            val intHeight = (rawPage.heightPt * VECTOR_INT_SCALE).toInt().coerceAtLeast(1)
            indexedFigures.forEach { (idx, block) ->
                val figure = block as ReflowBlock.Figure
                val table =
                    reconstructTableForFigure(
                        figure = figure,
                        grid = grid,
                        page = rawPage,
                        intSpaceWidth = intWidth,
                        intSpaceHeight = intHeight,
                    )
                if (table != null) newBlocks[idx] = table
            }
        }
        return ReflowDocument(kind = document.kind, blocks = newBlocks)
    }

    /**
     * Строит [LatticeTableDetector.TableGrid] из векторных линий страницы:
     * масштабирует pt → int (через [VECTOR_INT_SCALE]), кластеризует параллельные
     * близкие линии — точная аналогия растрового [Morphology.clusterToGridLines].
     */
    private fun buildGridFromVectors(rawPage: RawPage): LatticeTableDetector.TableGrid? {
        val intWidth = (rawPage.widthPt * VECTOR_INT_SCALE).toInt().coerceAtLeast(1)
        val intHeight = (rawPage.heightPt * VECTOR_INT_SCALE).toInt().coerceAtLeast(1)
        val tolY = (intHeight * VECTOR_CLUSTER_TOLERANCE_FRACTION).toInt().coerceAtLeast(1)
        val tolX = (intWidth * VECTOR_CLUSTER_TOLERANCE_FRACTION).toInt().coerceAtLeast(1)
        val minHRun = (intWidth * VECTOR_MIN_LINE_FRACTION).toInt().coerceAtLeast(1)
        val minVRun = (intHeight * VECTOR_MIN_LINE_FRACTION).toInt().coerceAtLeast(1)
        val hSegments = mutableListOf<Morphology.LineSegment>()
        val vSegments = mutableListOf<Morphology.LineSegment>()
        for (line in rawPage.vectorLines) {
            val start = (line.start * VECTOR_INT_SCALE).toInt()
            val end = (line.end * VECTOR_INT_SCALE).toInt()
            val perp = (line.perpPos * VECTOR_INT_SCALE).toInt()
            val length = end - start
            if (line.isHorizontal) {
                if (length >= minHRun) hSegments += Morphology.LineSegment(start, end, perp)
            } else {
                if (length >= minVRun) vSegments += Morphology.LineSegment(start, end, perp)
            }
        }
        val h = Morphology.clusterToGridLines(hSegments, tolY)
        val v = Morphology.clusterToGridLines(vSegments, tolX)
        return LatticeTableDetector.detectFromGridLines(h, v)
    }

    /**
     * Готовит контекст Lattice-анализа одной страницы: ищет [RawPage], рендерит
     * битмап через [renderPage], запускает [LatticeTableDetector]. `null`, если
     * хотя бы один шаг не отработал — page вне диапазона / renderPage вернул null /
     * грид-линий недостаточно. Соответствующие low-conf Figure остаются как есть.
     */
    private suspend fun preparePageContext(
        pageIndex: Int,
        rawPages: List<RawPage>,
        renderPage: PageBitmapProvider,
        targetWidthPx: Int,
    ): PageLatticeContext? {
        val rawPage = rawPages.getOrNull(pageIndex)
        val bitmap = rawPage?.let { renderPage(pageIndex, targetWidthPx) }
        val grid =
            bitmap?.let { LatticeTableDetector.detect(it.pixels, it.widthPx, it.heightPx) }
        return if (rawPage != null && bitmap != null && grid != null) {
            PageLatticeContext(page = rawPage, bitmap = bitmap, grid = grid)
        } else {
            null
        }
    }

    /** Контекст одной страницы для прохода Lattice — экономит на повторных вызовах. */
    private data class PageLatticeContext(
        val page: RawPage,
        val bitmap: PageRaster,
        val grid: LatticeTableDetector.TableGrid,
    )

    /**
     * Восстанавливает Table из Lattice-сетки, ограниченной областью [figure]: берём
     * только ячейки, чьи центры лежат внутри пиксельного bbox фигуры; мапим глифы
     * страницы по их координатам в эти ячейки; группируем ячейки в ряды по Y.
     *
     * Возвращает `null`, если:
     *  - ячеек внутри Figure меньше [MIN_CELLS_FOR_TABLE] (скорее шум);
     *  - получилось <2 рядов после группировки (как и Stream).
     */
    private fun reconstructTableForFigure(
        figure: ReflowBlock.Figure,
        grid: LatticeTableDetector.TableGrid,
        page: RawPage,
        intSpaceWidth: Int,
        intSpaceHeight: Int,
    ): ReflowBlock.Table? {
        val figPx = figureToPixelRect(figure.bounds, intSpaceWidth, intSpaceHeight)
        val cellsInFig = grid.cells.filter { it.centerInside(figPx) }
        // Ячеек мало — скорее шум; ≥2 рядов нужны как и Stream. Объединяем гарды
        // ниже в одну ветку, чтобы число return'ов не росло.
        val rows =
            if (cellsInFig.size < MIN_CELLS_FOR_TABLE) {
                emptyList()
            } else {
                groupCellsByRow(cellsInFig.map { pixelCellToPdfPoint(it, intSpaceWidth, intSpaceHeight, page) })
            }
        if (rows.size < 2) return null
        val tableRows =
            rows.map { rowCells ->
                ReflowBlock.TableRow(
                    cells = rowCells.sortedBy { it.left }.map { rect -> buildLatticeCell(rect, page) },
                )
            }
        // OCR-noise guard (F-7): PDFBox ловит фантомные rulings между колонками глифов
        // на сканах → грид 25+ колонок по 1 символу. Тот же helper, что у Stream-пути
        // ([ru.kyamshanov.notepen.reflow.TableNoiseGuard]). Без этой проверки Lattice
        // возвращал Table(confidence = 1f) в обход F-1. При срабатывании — null, и caller
        // сохраняет исходный Figure-кроп страницы (строго лучше сломанной таблицы).
        // Иначе Lattice — детерминированный сигнал от нарисованной сетки, confidence=1.
        return if (TableNoiseGuard.isOcrNoiseTable(tableRows)) {
            null
        } else {
            ReflowBlock.Table(rows = tableRows, confidence = 1f)
        }
    }

    /** Конверсия normalized [0..1] → int `PixelRect` через размер целевого int-space. */
    private fun figureToPixelRect(
        bounds: ReflowRect,
        intSpaceWidth: Int,
        intSpaceHeight: Int,
    ): LatticeTableDetector.PixelRect =
        LatticeTableDetector.PixelRect(
            left = (bounds.left * intSpaceWidth).toInt(),
            top = (bounds.top * intSpaceHeight).toInt(),
            right = (bounds.right * intSpaceWidth).toInt(),
            bottom = (bounds.bottom * intSpaceHeight).toInt(),
        )

    private fun LatticeTableDetector.PixelRect.centerInside(outer: LatticeTableDetector.PixelRect): Boolean {
        val cx = (left + right) / 2
        val cy = (top + bottom) / 2
        return cx in outer.left..outer.right && cy in outer.top..outer.bottom
    }

    /** Int-space ячейка → прямоугольник в PDF-пунктах (как у глифов). */
    private fun pixelCellToPdfPoint(
        cell: LatticeTableDetector.PixelRect,
        intSpaceWidth: Int,
        intSpaceHeight: Int,
        page: RawPage,
    ): PointRect {
        val pxToWPt = page.widthPt / intSpaceWidth.toFloat()
        val pxToHPt = page.heightPt / intSpaceHeight.toFloat()
        return PointRect(
            left = cell.left * pxToWPt,
            top = cell.top * pxToHPt,
            right = cell.right * pxToWPt,
            bottom = cell.bottom * pxToHPt,
        )
    }

    /**
     * Группирует ячейки в ряды по top-координате. Ряды — соседние ячейки с
     * близким `top` (допуск [ROW_GROUP_TOLERANCE_FRACTION] от высоты ячейки).
     */
    private fun groupCellsByRow(cells: List<PointRect>): List<List<PointRect>> {
        if (cells.isEmpty()) return emptyList()
        val sorted = cells.sortedBy { it.top }
        val rows = mutableListOf<MutableList<PointRect>>(mutableListOf(sorted.first()))
        var currentRowTop = sorted.first().top
        var currentRowHeight = sorted.first().bottom - sorted.first().top
        for (i in 1 until sorted.size) {
            val cell = sorted[i]
            val tolerance = currentRowHeight * ROW_GROUP_TOLERANCE_FRACTION
            if (kotlin.math.abs(cell.top - currentRowTop) <= tolerance) {
                rows.last().add(cell)
            } else {
                rows.add(mutableListOf(cell))
                currentRowTop = cell.top
                currentRowHeight = cell.bottom - cell.top
            }
        }
        return rows
    }

    /**
     * Собирает [ReflowBlock.TableCell]: глифы страницы, чьи центры попадают в [cell],
     * сортируются в порядке чтения (top→bottom, left→right в строке) и склеиваются
     * в текст с пробелами на стыках. Provenance — [SourceSpan] на каждый глиф.
     */
    private fun buildLatticeCell(
        cell: PointRect,
        page: RawPage,
    ): ReflowBlock.TableCell {
        val inside = glyphsInside(cell, page.glyphs)
        if (inside.isEmpty()) return ReflowBlock.TableCell(text = "", source = emptyList())
        val sorted = inside.sortedWith(compareBy<RawGlyph> { it.rect.top }.thenBy { it.rect.left })
        val sb = StringBuilder()
        val spans = mutableListOf<SourceSpan>()
        var prevRight = Float.NaN
        var prevTop = Float.NaN
        for (glyph in sorted) {
            if (sb.isNotEmpty()) {
                val isNewLine = !prevTop.isNaN() && glyph.rect.top - prevTop > glyph.fontSizePt * NEWLINE_THRESHOLD
                val isWideGap =
                    !prevRight.isNaN() && glyph.rect.left - prevRight > glyph.fontSizePt * SPACE_THRESHOLD
                if (isNewLine || isWideGap) sb.append(' ')
            }
            val start = sb.length
            sb.append(glyph.text)
            spans +=
                SourceSpan(
                    pageIndex = page.pageIndex,
                    charStart = start,
                    charEnd = sb.length,
                    bounds = normalised(glyph.rect, page.widthPt, page.heightPt),
                    bold = glyph.bold,
                    monospace = glyph.monospace,
                    italic = glyph.italic,
                )
            prevRight = glyph.rect.right
            prevTop = glyph.rect.top
        }
        return ReflowBlock.TableCell(text = sb.toString(), source = spans)
    }

    private fun glyphsInside(
        cell: PointRect,
        glyphs: List<RawGlyph>,
    ): List<RawGlyph> =
        glyphs.filter { g ->
            val cx = (g.rect.left + g.rect.right) / 2f
            val cy = (g.rect.top + g.rect.bottom) / 2f
            cx in cell.left..cell.right && cy in cell.top..cell.bottom
        }

    private fun normalised(
        rect: ReflowRect,
        widthPt: Float,
        heightPt: Float,
    ): ReflowRect =
        if (widthPt <= 0f || heightPt <= 0f) {
            rect
        } else {
            ReflowRect(rect.left / widthPt, rect.top / heightPt, rect.right / widthPt, rect.bottom / heightPt)
        }

    /**
     * Доля кегля, начиная с которой переход между глифами трактуется как пробел.
     * Согласуется с эвристиками ReflowAssembler.SPACE_FACTOR=0.25, чуть выше — для
     * Lattice интересны явные межсловные зазоры внутри ячейки.
     */
    private const val SPACE_THRESHOLD = 0.3f

    /** Доля кегля, начиная с которой смена top-координаты глифа — это новая строка. */
    private const val NEWLINE_THRESHOLD = 0.5f

    /** Прямоугольник в PDF-пунктах (как у [RawGlyph.rect]). */
    private data class PointRect(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    )
}
