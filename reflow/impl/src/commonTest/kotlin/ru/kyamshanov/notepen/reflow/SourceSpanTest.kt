package ru.kyamshanov.notepen.reflow

import ru.kyamshanov.notepen.reflow.api.ReflowBlock
import ru.kyamshanov.notepen.reflow.api.ReflowRect
import ru.kyamshanov.notepen.reflow.api.SourceSpan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SourceSpanTest {
    @Test
    fun `paragraph spans cover every non-space char exactly once`() {
        val blocks = ReflowAssembler.assemble(listOf(page(line("hello world", top = 100f)))).blocks
        val paragraph = assertIs<ReflowBlock.Paragraph>(blocks.single())
        assertEquals("hello world", paragraph.text)
        assertCoversNonSpaceChars(paragraph.text, paragraph.source)
    }

    @Test
    fun `inter-line join keeps spans contiguous and excludes the space`() {
        val glyphs = line("first line", top = 100f) + line("second line", top = 112f)
        val paragraph =
            assertIs<ReflowBlock.Paragraph>(
                ReflowAssembler.assemble(listOf(page(glyphs))).blocks.single(),
            )
        assertEquals("first line second line", paragraph.text)
        assertCoversNonSpaceChars(paragraph.text, paragraph.source)
    }

    @Test
    fun `dropped hyphen yields no span and adjacent runs`() {
        val glyphs = line("exam-", top = 100f) + line("ple sentence", top = 112f)
        val paragraph =
            assertIs<ReflowBlock.Paragraph>(
                ReflowAssembler.assemble(listOf(page(glyphs))).blocks.single(),
            )
        assertEquals("example sentence", paragraph.text)
        // no span is the removed hyphen
        assertFalse(paragraph.source.any { paragraph.text.substring(it.charStart, it.charEnd) == "-" })
        assertCoversNonSpaceChars(paragraph.text, paragraph.source)
        // 'm' (index 3) and 'p' (index 4) are adjacent across the join
        assertTrue(paragraph.source.any { it.charEnd == 4 }, "expected a span ending at 'm'")
        assertTrue(paragraph.source.any { it.charStart == 4 }, "expected a span starting at 'p'")
    }

    @Test
    fun `join word split by typographic hyphen`() {
        val glyphs = line("дог‐", top = 100f) + line("мы конец", top = 112f)
        val paragraph =
            assertIs<ReflowBlock.Paragraph>(
                ReflowAssembler.assemble(listOf(page(glyphs))).blocks.single(),
            )
        assertEquals("догмы конец", paragraph.text)
    }

    @Test
    fun `compound hyphen kept and joined without a space when next line is uppercase`() {
        // составной перенос «Plugin-Name»: дефис сохраняем, но пробел не вставляем
        val glyphs = line("exam-", top = 100f) + line("Ple", top = 112f)
        val paragraph =
            assertIs<ReflowBlock.Paragraph>(
                ReflowAssembler.assemble(listOf(page(glyphs))).blocks.single(),
            )
        assertEquals("exam-Ple", paragraph.text)
        assertTrue(paragraph.source.any { paragraph.text.substring(it.charStart, it.charEnd) == "-" })
    }

    @Test
    fun `heading carries spans`() {
        val glyphs = line("Title", top = 50f, fontSize = 20f) + line("body text here", top = 100f)
        val heading = assertIs<ReflowBlock.Heading>(ReflowAssembler.assemble(listOf(page(glyphs))).blocks[0])
        assertEquals("Title", heading.text)
        assertTrue(heading.source.isNotEmpty())
        assertCoversNonSpaceChars(heading.text, heading.source)
    }

    @Test
    fun `glyph bounds normalised to page size`() {
        // одиночный глиф 'a': прямоугольник в пунктах (60,80)-(120,160) на 600×800
        val glyphs = line("a", top = 80f, startX = 60f, charWidth = 60f, fontSize = 80f)
        val paragraph =
            assertIs<ReflowBlock.Paragraph>(
                ReflowAssembler.assemble(listOf(page(glyphs))).blocks.single(),
            )
        val bounds = paragraph.source.single().bounds
        assertAlmostEquals(0.1f, bounds.left)
        assertAlmostEquals(0.1f, bounds.top)
        assertAlmostEquals(0.2f, bounds.right)
        assertAlmostEquals(0.2f, bounds.bottom)
    }

    @Test
    fun `non-square page normalises x and y independently`() {
        val glyphs = line("a", top = 500f, startX = 200f, charWidth = 60f, fontSize = 80f)
        val paragraph =
            assertIs<ReflowBlock.Paragraph>(
                ReflowAssembler.assemble(listOf(page(glyphs, widthPt = 400f, heightPt = 1000f))).blocks.single(),
            )
        val bounds = paragraph.source.single().bounds
        assertAlmostEquals(200f / 400f, bounds.left)
        assertAlmostEquals(500f / 1000f, bounds.top)
        assertAlmostEquals(260f / 400f, bounds.right)
        assertAlmostEquals(580f / 1000f, bounds.bottom)
    }

    @Test
    fun `all bounds lie within unit square for in-page glyphs`() {
        val paragraph =
            assertIs<ReflowBlock.Paragraph>(
                ReflowAssembler.assemble(listOf(page(line("normal text", top = 100f)))).blocks.single(),
            )
        paragraph.source.forEach {
            assertTrue(it.bounds.left in 0f..1f && it.bounds.right in 0f..1f, "x out of [0,1]: ${it.bounds}")
            assertTrue(it.bounds.top in 0f..1f && it.bounds.bottom in 0f..1f, "y out of [0,1]: ${it.bounds}")
        }
    }

    @Test
    fun `spans carry their source page index`() {
        val pages =
            listOf(
                page(line("first", top = 100f), pageIndex = 0),
                page(line("second", top = 100f), pageIndex = 1),
            )
        val blocks = ReflowAssembler.assemble(pages).blocks
        val page0 = assertIs<ReflowBlock.Paragraph>(blocks[0])
        val page1 = assertIs<ReflowBlock.Paragraph>(blocks[1])
        assertTrue(page0.source.isNotEmpty() && page0.source.all { it.pageIndex == 0 })
        assertTrue(page1.source.isNotEmpty() && page1.source.all { it.pageIndex == 1 })
    }

    @Test
    fun `figure on a later page is normalised by that page dimensions`() {
        val pages =
            listOf(
                page(line("a", top = 100f), pageIndex = 0),
                page(
                    glyphs = line("x", top = 50f),
                    images = listOf(ReflowRect(40f, 100f, 200f, 300f)),
                    pageIndex = 1,
                    widthPt = 400f,
                    heightPt = 1000f,
                ),
            )
        val figure = ReflowAssembler.assemble(pages).blocks.filterIsInstance<ReflowBlock.Figure>().single()
        assertEquals(1, figure.pageIndex)
        assertAlmostEquals(40f / 400f, figure.bounds.left)
        assertAlmostEquals(100f / 1000f, figure.bounds.top)
        assertAlmostEquals(200f / 400f, figure.bounds.right)
        assertAlmostEquals(300f / 1000f, figure.bounds.bottom)
    }

    @Test
    fun `loose letter tracking does not split a word`() {
        val fontSize = 10f
        val spaceWidth = 12f

        fun glyph(
            char: String,
            left: Float,
        ) = RawGlyph(char, ReflowRect(left, 100f, left + 5f, 110f), fontSize, spaceWidth)
        // Зазоры между буквами 4pt: > 0.25*кегль (2.5), но < 0.5*пробел (6) → не пробел.
        val glyphs = listOf(glyph("c", 50f), glyph("a", 59f), glyph("t", 68f))
        val paragraph =
            assertIs<ReflowBlock.Paragraph>(
                ReflowAssembler.assemble(listOf(page(glyphs))).blocks.single(),
            )
        assertEquals("cat", paragraph.text)
    }

    /** Каждый неблэнк-символ покрыт ровно одним спаном; пробелы — ничем. */
    private fun assertCoversNonSpaceChars(
        text: String,
        spans: List<SourceSpan>,
    ) {
        val covered = mutableSetOf<Int>()
        for (span in spans) {
            assertTrue(span.charStart in 0..text.length && span.charEnd in span.charStart..text.length)
            assertTrue(span.charStart < span.charEnd, "empty span must be dropped: $span")
            assertFalse(text.substring(span.charStart, span.charEnd).contains(' '), "span covers a space")
            for (i in span.charStart until span.charEnd) {
                assertTrue(covered.add(i), "index $i covered by more than one span")
            }
        }
        val expected = text.indices.filter { text[it] != ' ' }.toSet()
        assertEquals(expected, covered, "covered indices must equal all non-space positions")
    }
}
