package ru.kyamshanov.notepen.reflow

import ru.kyamshanov.notepen.reflow.api.PdfContentKind
import ru.kyamshanov.notepen.reflow.api.ReflowBlock
import ru.kyamshanov.notepen.reflow.api.ReflowRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReflowAssemblerTest {

    @Test
    fun `classify empty document as image only`() {
        assertEquals(PdfContentKind.IMAGE_ONLY, ReflowAssembler.classify(emptyList()))
    }

    @Test
    fun `classify pages with text as text based`() {
        val pages = listOf(page(line("hello world", top = 100f)), page(line("second page", top = 100f)))
        assertEquals(PdfContentKind.TEXT_BASED, ReflowAssembler.classify(pages))
    }

    @Test
    fun `classify mixed pages as hybrid`() {
        val pages = listOf(page(line("has text", top = 100f)), page(emptyList()))
        assertEquals(PdfContentKind.HYBRID, ReflowAssembler.classify(pages))
    }

    @Test
    fun `classify pages without text as image only`() {
        assertEquals(PdfContentKind.IMAGE_ONLY, ReflowAssembler.classify(listOf(page(emptyList()))))
    }

    @Test
    fun `merge tightly spaced lines into one paragraph`() {
        val glyphs = line("first line", top = 100f) + line("second line", top = 112f)
        val blocks = ReflowAssembler.assemble(listOf(page(glyphs))).blocks
        assertEquals(1, blocks.size)
        val paragraph = assertIs<ReflowBlock.Paragraph>(blocks.single())
        assertEquals("first line second line", paragraph.text)
    }

    @Test
    fun `split paragraphs separated by a large gap`() {
        val glyphs = line("paragraph one", top = 100f) + line("paragraph two", top = 140f)
        val blocks = ReflowAssembler.assemble(listOf(page(glyphs))).blocks
        assertEquals(2, blocks.size)
        assertEquals("paragraph one", (blocks[0] as ReflowBlock.Paragraph).text)
        assertEquals("paragraph two", (blocks[1] as ReflowBlock.Paragraph).text)
    }

    @Test
    fun `detect heading by larger font size`() {
        val glyphs = line("Title", top = 50f, fontSize = 20f) +
            line("body text follows the heading", top = 100f)
        val blocks = ReflowAssembler.assemble(listOf(page(glyphs))).blocks
        assertEquals(2, blocks.size)
        val heading = assertIs<ReflowBlock.Heading>(blocks[0])
        assertEquals("Title", heading.text)
        assertEquals(1, heading.level)
        assertIs<ReflowBlock.Paragraph>(blocks[1])
    }

    @Test
    fun `join hyphenated word across line break`() {
        val glyphs = line("exam-", top = 100f) + line("ple sentence", top = 112f)
        val blocks = ReflowAssembler.assemble(listOf(page(glyphs))).blocks
        val paragraph = assertIs<ReflowBlock.Paragraph>(blocks.single())
        assertEquals("example sentence", paragraph.text)
    }

    @Test
    fun `place a figure between paragraphs in reading order`() {
        val glyphs = line("before image", top = 100f) + line("after image", top = 200f)
        val image = ReflowRect(left = 50f, top = 130f, right = 300f, bottom = 180f)
        val blocks = ReflowAssembler.assemble(listOf(page(glyphs, images = listOf(image)))).blocks
        assertEquals(3, blocks.size)
        assertEquals("before image", (blocks[0] as ReflowBlock.Paragraph).text)
        val figure = assertIs<ReflowBlock.Figure>(blocks[1])
        // bounds нормализованы в [0..1] относительно страницы 600×800
        assertAlmostEquals(50f / 600f, figure.bounds.left)
        assertAlmostEquals(130f / 800f, figure.bounds.top)
        assertAlmostEquals(300f / 600f, figure.bounds.right)
        assertAlmostEquals(180f / 800f, figure.bounds.bottom)
        assertEquals("after image", (blocks[2] as ReflowBlock.Paragraph).text)
    }

    @Test
    fun `order two columns left then right`() {
        val left = (0 until 6).flatMap { line("left column line text", top = 100f + it * 14f, startX = 40f) }
        val right = (0 until 6).flatMap { line("right column line text", top = 100f + it * 14f, startX = 340f) }
        val blocks = ReflowAssembler.assemble(listOf(page(left + right))).blocks
        val firstParagraph = blocks.filterIsInstance<ReflowBlock.Paragraph>().first().text
        assertTrue(firstParagraph.startsWith("left column"), "expected left column first, got: $firstParagraph")
    }
}
