package ru.kyamshanov.notepen.reflow

import ru.kyamshanov.notepen.reflow.api.ReflowBlock
import ru.kyamshanov.notepen.reflow.api.ReflowRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Покрывает склейку через page-boundary дефиса: «expla-» в конце одной страницы
 * и «nation» в начале следующей → «explanation» в одном Paragraph.
 */
class CrossPageDehyphenationTest {
    private fun glyphsOf(
        text: String,
        topY: Float,
        startX: Float = 50f,
    ): List<RawGlyph> {
        val charW = 6f
        return text.mapIndexed { i, ch ->
            RawGlyph(
                text = ch.toString(),
                rect =
                    ReflowRect(
                        left = startX + i * charW,
                        top = topY,
                        right = startX + (i + 1) * charW,
                        bottom = topY + 10f,
                    ),
                fontSizePt = 10f,
            )
        }
    }

    private fun pageOf(
        text: String,
        index: Int,
    ): RawPage =
        RawPage(
            pageIndex = index,
            widthPt = 600f,
            heightPt = 200f,
            glyphs = glyphsOf(text, topY = 100f),
            images = emptyList(),
        )

    @Test
    fun hyphen_at_page_break_with_lowercase_continuation_merges() {
        val pages =
            listOf(
                pageOf("Here is some expla-", index = 0),
                pageOf("nation that follows.", index = 1),
            )
        val doc = ReflowAssembler.assemble(pages)
        val paragraphs = doc.blocks.filterIsInstance<ReflowBlock.Paragraph>()
        assertEquals(1, paragraphs.size, "expected merge into one paragraph; got ${paragraphs.size}: ${paragraphs.map { it.text }}")
        val text = paragraphs.single().text
        assertTrue("explanation" in text, "merged paragraph should contain 'explanation'; got: '$text'")
        assertTrue("expla-" !in text, "hyphen should be removed; got: '$text'")
    }

    @Test
    fun hyphen_at_page_break_with_uppercase_continuation_keeps_blocks_separate() {
        // «Plug-» + «Architecture» — uppercase следующего слова → compound,
        // не склеиваем.
        val pages =
            listOf(
                pageOf("Some Plug-", index = 0),
                pageOf("Architecture description.", index = 1),
            )
        val doc = ReflowAssembler.assemble(pages)
        val paragraphs = doc.blocks.filterIsInstance<ReflowBlock.Paragraph>()
        assertTrue(paragraphs.size >= 2, "expected separate paragraphs; got ${paragraphs.size}")
    }

    @Test
    fun heading_followed_by_lowercase_paragraph_is_not_merged() {
        val pages =
            listOf(
                pageOf("Some text-", index = 0),
            )
        // Только один Paragraph — нет следующего, не сливаем (но и не падаем).
        val doc = ReflowAssembler.assemble(pages)
        assertEquals(1, doc.blocks.filterIsInstance<ReflowBlock.Paragraph>().size)
    }
}
