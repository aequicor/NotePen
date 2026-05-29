package ru.kyamshanov.notepen.reflow

import ru.kyamshanov.notepen.reflow.api.ReflowBlock
import ru.kyamshanov.notepen.reflow.api.ReflowRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Покрывает группировку подряд идущих моноширинных строк в [ReflowBlock.Code].
 */
class CodeBlockDetectionTest {
    /**
     * Возвращает строку моноширинных глифов: каждый символ — [RawGlyph] с
     * `monospace=true`, в заданной Y-позиции.
     */
    private fun monoLine(
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
                monospace = true,
            )
        }
    }

    private fun proseLine(
        text: String,
        topY: Float,
    ): List<RawGlyph> {
        val charW = 6f
        return text.mapIndexed { i, ch ->
            RawGlyph(
                text = ch.toString(),
                rect =
                    ReflowRect(
                        left = 50f + i * charW,
                        top = topY,
                        right = 50f + (i + 1) * charW,
                        bottom = topY + 10f,
                    ),
                fontSizePt = 10f,
                monospace = false,
            )
        }
    }

    @Test
    fun groups_consecutive_monospace_lines_into_Code() {
        val page =
            RawPage(
                pageIndex = 0,
                widthPt = 600f,
                heightPt = 800f,
                glyphs =
                    monoLine("val x = 42", topY = 100f) +
                        monoLine("println(x)", topY = 130f) +
                        monoLine("fun main()", topY = 160f),
                images = emptyList(),
            )
        val doc = ReflowAssembler.assemble(listOf(page))
        val code = doc.blocks.filterIsInstance<ReflowBlock.Code>()
        assertEquals(1, code.size, "expected one Code block; got blocks=${doc.blocks.map { it::class.simpleName }}")
        val text = code.single().text
        assertTrue("val x = 42" in text)
        assertTrue("println(x)" in text)
        assertTrue("fun main()" in text)
        assertTrue('\n' in text, "Code block must preserve newlines between lines; got: '$text'")
    }

    @Test
    fun single_monospace_line_falls_back_to_Paragraph() {
        // Одиночная моноширинная строка между обычными — это либо inline-code в
        // абзаце, либо artefact (path к файлу); не должна стать Code блоком.
        val page =
            RawPage(
                pageIndex = 0,
                widthPt = 600f,
                heightPt = 800f,
                glyphs = monoLine("only_one_line", topY = 100f),
                images = emptyList(),
            )
        val doc = ReflowAssembler.assemble(listOf(page))
        assertEquals(0, doc.blocks.filterIsInstance<ReflowBlock.Code>().size, "одиночная строка не Code")
        // Демоутится в Paragraph (или Heading на edge-cases — не важно для нас здесь).
        assertTrue(
            doc.blocks.any { it is ReflowBlock.Paragraph || it is ReflowBlock.Heading },
            "ожидался Paragraph или Heading, got: ${doc.blocks.map { it::class.simpleName }}",
        )
    }

    @Test
    fun mixed_monospace_and_prose_emit_separate_blocks() {
        val page =
            RawPage(
                pageIndex = 0,
                widthPt = 600f,
                heightPt = 800f,
                glyphs =
                    proseLine("Here is some code:", topY = 100f) +
                        monoLine("val a = 1", topY = 130f) +
                        monoLine("val b = 2", topY = 160f) +
                        proseLine("And after the code.", topY = 200f),
                images = emptyList(),
            )
        val doc = ReflowAssembler.assemble(listOf(page))
        val codeBlocks = doc.blocks.filterIsInstance<ReflowBlock.Code>()
        val paragraphs = doc.blocks.filterIsInstance<ReflowBlock.Paragraph>()
        assertEquals(1, codeBlocks.size, "expected one Code block in middle")
        assertTrue(paragraphs.size >= 2, "expected ≥2 paragraphs around the code; got ${paragraphs.size}")
        // Порядок: первый Paragraph → Code → последний Paragraph.
        val codeIndex = doc.blocks.indexOf(codeBlocks.first())
        assertTrue(codeIndex > 0, "Code не первый блок")
        assertTrue(codeIndex < doc.blocks.lastIndex, "Code не последний блок")
        assertIs<ReflowBlock.Paragraph>(doc.blocks[codeIndex - 1])
        assertIs<ReflowBlock.Paragraph>(doc.blocks[codeIndex + 1])
    }
}
