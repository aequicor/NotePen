package ru.kyamshanov.notepen.reflow.lattice

import kotlinx.coroutines.test.runTest
import ru.kyamshanov.notepen.reflow.RawGlyph
import ru.kyamshanov.notepen.reflow.RawPage
import ru.kyamshanov.notepen.reflow.api.PageBitmapProvider
import ru.kyamshanov.notepen.reflow.api.PageRaster
import ru.kyamshanov.notepen.reflow.api.PdfContentKind
import ru.kyamshanov.notepen.reflow.api.ReflowBlock
import ru.kyamshanov.notepen.reflow.api.ReflowDocument
import ru.kyamshanov.notepen.reflow.api.ReflowRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.fail

class LatticeTableRefinerTest {
    /**
     * Главный happy-path: fallback-Figure заменяется на восстановленную таблицу,
     * когда LatticeTableDetector находит сетку, а глифы попадают в ячейки.
     *
     * Раскладка теста: страница 600×400 pt, bitmap 1200×800 px (масштаб 2x). Грид:
     *  H-линии Y={100,400,700}, V-линии X={100,600,1100} → 2×2 ячейки.
     *  В PDF-coords ячейки: top-left=(50,50)-(300,200), top-right=(300,50)-(550,200),
     *  bot-left=(50,200)-(300,350), bot-right=(300,200)-(550,350).
     */
    @Test
    fun `refine replaces fallback Figure with reconstructed Table when Lattice finds a grid`() =
        runTest {
            val widthPt = 600f
            val heightPt = 400f
            val bitmapW = 1200
            val bitmapH = 800

            val glyphs =
                cellWord("Name", startX = 80f, top = 80f) +
                    cellWord("Type", startX = 320f, top = 80f) +
                    cellWord("Age", startX = 80f, top = 240f) +
                    cellWord("Int", startX = 320f, top = 240f)
            val rawPage =
                RawPage(
                    pageIndex = 0,
                    widthPt = widthPt,
                    heightPt = heightPt,
                    glyphs = glyphs,
                    images = emptyList(),
                )

            val figure =
                ReflowBlock.Figure(
                    pageIndex = 0,
                    bounds = ReflowRect(left = 0.05f, top = 0.05f, right = 0.95f, bottom = 0.95f),
                    aspectRatio = widthPt / heightPt,
                    wasTableFallback = true,
                )
            val document =
                ReflowDocument(
                    kind = PdfContentKind.TEXT_BASED,
                    blocks = listOf(figure),
                )

            val gridPixels =
                bitmap(bitmapW, bitmapH) { x, y ->
                    val hYs = listOf(100, 400, 700)
                    val vXs = listOf(100, 600, 1100)
                    (y in hYs && x in 100..1100) || (x in vXs && y in 100..700)
                }
            val renderPage: PageBitmapProvider = { idx, _ ->
                if (idx == 0) PageRaster(gridPixels, bitmapW, bitmapH) else null
            }

            val refined = LatticeTableRefiner.refine(document, listOf(rawPage), renderPage)
            val table = assertIs<ReflowBlock.Table>(refined.blocks.single())
            assertEquals(2, table.rows.size)
            assertEquals(2, table.rows[0].cells.size)
            assertEquals(2, table.rows[1].cells.size)
            assertEquals("Name", table.rows[0].cells[0].text)
            assertEquals("Type", table.rows[0].cells[1].text)
            assertEquals("Age", table.rows[1].cells[0].text)
            assertEquals("Int", table.rows[1].cells[1].text)
            assertEquals(1f, table.confidence, "Lattice — детерминированный сигнал, confidence=1")
        }

    @Test
    fun `refineFromVectorLines reconstructs Table from vector grid lines`() =
        runTest {
            // Та же раскладка, что и в bitmap-варианте, но грид задан
            // VectorLine'ами прямо в PDF-points — без растеризации.
            val widthPt = 600f
            val heightPt = 400f
            val glyphs =
                cellWord("Name", startX = 80f, top = 80f) +
                    cellWord("Type", startX = 320f, top = 80f) +
                    cellWord("Age", startX = 80f, top = 240f) +
                    cellWord("Int", startX = 320f, top = 240f)
            // 3 H-линии (Y=50, 200, 350) + 3 V-линии (X=50, 300, 550) → 2×2 ячейки.
            val vectorLines =
                listOf(
                    ru.kyamshanov.notepen.reflow.VectorLine(true, start = 50f, end = 550f, perpPos = 50f),
                    ru.kyamshanov.notepen.reflow.VectorLine(true, start = 50f, end = 550f, perpPos = 200f),
                    ru.kyamshanov.notepen.reflow.VectorLine(true, start = 50f, end = 550f, perpPos = 350f),
                    ru.kyamshanov.notepen.reflow.VectorLine(false, start = 50f, end = 350f, perpPos = 50f),
                    ru.kyamshanov.notepen.reflow.VectorLine(false, start = 50f, end = 350f, perpPos = 300f),
                    ru.kyamshanov.notepen.reflow.VectorLine(false, start = 50f, end = 350f, perpPos = 550f),
                )
            val rawPage =
                RawPage(
                    pageIndex = 0,
                    widthPt = widthPt,
                    heightPt = heightPt,
                    glyphs = glyphs,
                    images = emptyList(),
                    vectorLines = vectorLines,
                )
            val figure =
                ReflowBlock.Figure(
                    pageIndex = 0,
                    bounds = ReflowRect(0.05f, 0.05f, 0.95f, 0.95f),
                    aspectRatio = widthPt / heightPt,
                    wasTableFallback = true,
                )
            val document = ReflowDocument(kind = PdfContentKind.TEXT_BASED, blocks = listOf(figure))
            val refined = LatticeTableRefiner.refineFromVectorLines(document, listOf(rawPage))
            val table = assertIs<ReflowBlock.Table>(refined.blocks.single())
            assertEquals(2, table.rows.size, "2 рядов из 3 H-линий")
            assertEquals(2, table.rows[0].cells.size, "2 колонки из 3 V-линий")
            assertEquals("Name", table.rows[0].cells[0].text)
            assertEquals("Int", table.rows[1].cells[1].text)
            assertEquals(1f, table.confidence)
        }

    @Test
    fun `refineFromVectorLines is no-op when no vector lines on the page`() =
        runTest {
            val figure =
                ReflowBlock.Figure(
                    pageIndex = 0,
                    bounds = ReflowRect(0f, 0f, 1f, 1f),
                    aspectRatio = 1f,
                    wasTableFallback = true,
                )
            val document = ReflowDocument(kind = PdfContentKind.TEXT_BASED, blocks = listOf(figure))
            val rawPage =
                RawPage(
                    pageIndex = 0,
                    widthPt = 600f,
                    heightPt = 400f,
                    glyphs = emptyList(),
                    images = emptyList(),
                    vectorLines = emptyList(),
                )
            val refined = LatticeTableRefiner.refineFromVectorLines(document, listOf(rawPage))
            assertEquals(document, refined)
        }

    @Test
    fun `refine skips Figures without wasTableFallback flag`() =
        runTest {
            val figure =
                ReflowBlock.Figure(
                    pageIndex = 0,
                    bounds = ReflowRect(0f, 0f, 1f, 1f),
                    aspectRatio = 1f,
                    wasTableFallback = false,
                )
            val document = ReflowDocument(kind = PdfContentKind.TEXT_BASED, blocks = listOf(figure))
            val rawPage =
                RawPage(pageIndex = 0, widthPt = 600f, heightPt = 400f, glyphs = emptyList(), images = emptyList())
            val never: PageBitmapProvider = { _, _ -> fail("renderPage не должен дёргаться без fallback-кандидатов") }

            val refined = LatticeTableRefiner.refine(document, listOf(rawPage), never)
            assertEquals(document, refined)
        }

    @Test
    fun `refine keeps Figure when Lattice does not find a grid`() =
        runTest {
            val figure =
                ReflowBlock.Figure(
                    pageIndex = 0,
                    bounds = ReflowRect(0f, 0f, 1f, 1f),
                    aspectRatio = 1f,
                    wasTableFallback = true,
                )
            val document = ReflowDocument(kind = PdfContentKind.TEXT_BASED, blocks = listOf(figure))
            val rawPage =
                RawPage(pageIndex = 0, widthPt = 600f, heightPt = 400f, glyphs = emptyList(), images = emptyList())
            // Чисто-белый битмап — никаких грид-линий, detect вернёт null.
            val white = IntArray(1200 * 800) { 0xFFFFFFFF.toInt() }
            val renderPage: PageBitmapProvider = { _, _ -> PageRaster(white, 1200, 800) }

            val refined = LatticeTableRefiner.refine(document, listOf(rawPage), renderPage)
            assertIs<ReflowBlock.Figure>(refined.blocks.single())
        }

    @Test
    fun `refine keeps Figure when bitmap callback returns null`() =
        runTest {
            val figure =
                ReflowBlock.Figure(
                    pageIndex = 0,
                    bounds = ReflowRect(0f, 0f, 1f, 1f),
                    aspectRatio = 1f,
                    wasTableFallback = true,
                )
            val document = ReflowDocument(kind = PdfContentKind.TEXT_BASED, blocks = listOf(figure))
            val rawPage =
                RawPage(pageIndex = 0, widthPt = 600f, heightPt = 400f, glyphs = emptyList(), images = emptyList())
            val nullRender: PageBitmapProvider = { _, _ -> null }

            val refined = LatticeTableRefiner.refine(document, listOf(rawPage), nullRender)
            assertEquals(document, refined)
        }

    /**
     * Раскладывает слово в [RawGlyph]'ы по символам шириной 6 pt: компактно
     * группируется в одну ячейку и сортируется в порядке чтения. По умолчанию
     * fontSizePt=10 (как в [ru.kyamshanov.notepen.reflow.ReflowTestFixtures]).
     */
    private fun cellWord(
        text: String,
        startX: Float,
        top: Float,
        charWidth: Float = 6f,
        fontSizePt: Float = 10f,
    ): List<RawGlyph> {
        var x = startX
        val result = mutableListOf<RawGlyph>()
        for (ch in text) {
            result +=
                RawGlyph(
                    text = ch.toString(),
                    rect = ReflowRect(left = x, top = top, right = x + charWidth, bottom = top + fontSizePt),
                    fontSizePt = fontSizePt,
                )
            x += charWidth
        }
        return result
    }

    private fun bitmap(
        width: Int,
        height: Int,
        draw: (x: Int, y: Int) -> Boolean,
    ): IntArray {
        val arr = IntArray(width * height)
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()
        for (y in 0 until height) {
            for (x in 0 until width) {
                arr[y * width + x] = if (draw(x, y)) black else white
            }
        }
        return arr
    }
}
