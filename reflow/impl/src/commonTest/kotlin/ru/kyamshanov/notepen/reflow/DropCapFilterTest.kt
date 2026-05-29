package ru.kyamshanov.notepen.reflow

import ru.kyamshanov.notepen.reflow.api.ReflowBlock
import ru.kyamshanov.notepen.reflow.api.ReflowRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DropCapFilterTest {
    private fun glyph(
        ch: Char,
        top: Float,
        x: Float,
        fontSize: Float,
    ): RawGlyph =
        RawGlyph(
            text = ch.toString(),
            rect = ReflowRect(left = x, top = top, right = x + fontSize * 0.6f, bottom = top + fontSize),
            fontSizePt = fontSize,
        )

    private fun textGlyphs(
        text: String,
        top: Float,
        x: Float,
        fontSize: Float,
    ): List<RawGlyph> {
        val charW = fontSize * 0.6f
        return text.mapIndexed { i, ch ->
            glyph(ch, top, x + i * charW, fontSize)
        }
    }

    @Test
    fun single_giant_letter_is_not_heading() {
        // Гигантская «O» (3.5× кегля) — буквица. Должна не стать Heading.
        val page =
            RawPage(
                pageIndex = 0,
                widthPt = 600f,
                heightPt = 200f,
                glyphs =
                    listOf(glyph('O', top = 100f, x = 50f, fontSize = 35f)) +
                        textGlyphs("nce upon a time there was a story.", top = 100f, x = 80f, fontSize = 10f),
                images = emptyList(),
            )
        val doc = ReflowAssembler.assemble(listOf(page))
        val headings = doc.blocks.filterIsInstance<ReflowBlock.Heading>()
        assertEquals(0, headings.size, "drop cap should NOT become heading; got: ${headings.map { it.text }}")
    }

    @Test
    fun multi_char_large_text_still_heading() {
        // Несколько глифов крупно (а не одиночная буква) — нормальный заголовок.
        val page =
            RawPage(
                pageIndex = 0,
                widthPt = 600f,
                heightPt = 200f,
                glyphs =
                    textGlyphs("Big Heading", top = 50f, x = 50f, fontSize = 30f) +
                        textGlyphs("Then some body content.", top = 130f, x = 50f, fontSize = 10f),
                images = emptyList(),
            )
        val doc = ReflowAssembler.assemble(listOf(page))
        val headings = doc.blocks.filterIsInstance<ReflowBlock.Heading>()
        assertTrue(headings.isNotEmpty(), "expected multi-glyph large text to be heading")
    }
}
