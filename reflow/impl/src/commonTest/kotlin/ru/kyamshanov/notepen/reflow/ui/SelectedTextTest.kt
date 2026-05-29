package ru.kyamshanov.notepen.reflow.ui

import ru.kyamshanov.notepen.reflow.api.PdfContentKind
import ru.kyamshanov.notepen.reflow.api.ReflowBlock
import ru.kyamshanov.notepen.reflow.api.ReflowDocument
import ru.kyamshanov.notepen.reflow.api.TextAnchor
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Извлечение выделенного текста для буфера обмена ([selectedText]) — чистая функция,
 * проверяем её отдельно от хит-теста раскладки (тот завязан на LayoutCoordinates).
 */
class SelectedTextTest {
    private fun doc(vararg blocks: ReflowBlock): ReflowDocument = ReflowDocument(kind = PdfContentKind.TEXT_BASED, blocks = blocks.toList())

    @Test
    fun `single block partial range`() {
        val document = doc(ReflowBlock.Paragraph("hello world"))
        val text = selectedText(document, listOf(TextAnchor(0, 0, 5)))
        assertEquals("hello", text)
    }

    @Test
    fun `multiple blocks join with newline`() {
        val document =
            doc(
                ReflowBlock.Paragraph("first paragraph"),
                ReflowBlock.Paragraph("second paragraph"),
            )
        val text =
            selectedText(
                document,
                listOf(TextAnchor(0, 6, 15), TextAnchor(1, 0, 6)),
            )
        assertEquals("paragraph\nsecond", text)
    }

    @Test
    fun `range clamps to text length`() {
        val document = doc(ReflowBlock.Paragraph("abc"))
        val text = selectedText(document, listOf(TextAnchor(0, 1, 99)))
        assertEquals("bc", text)
    }

    @Test
    fun `non-text blocks are skipped`() {
        val document =
            doc(
                ReflowBlock.Paragraph("text"),
                ReflowBlock.Divider,
                ReflowBlock.Paragraph("more"),
            )
        val text =
            selectedText(
                document,
                listOf(TextAnchor(0, 0, 4), TextAnchor(1, 0, 0), TextAnchor(2, 0, 4)),
            )
        assertEquals("text\nmore", text)
    }

    @Test
    fun `heading list code footnote blockquote all extract`() {
        val document =
            doc(
                ReflowBlock.Heading("H", level = 1),
                ReflowBlock.ListItem("item"),
                ReflowBlock.Code("code"),
                ReflowBlock.Footnote("note"),
                ReflowBlock.Blockquote("quote"),
            )
        val anchors =
            listOf(
                TextAnchor(0, 0, 1),
                TextAnchor(1, 0, 4),
                TextAnchor(2, 0, 4),
                TextAnchor(3, 0, 4),
                TextAnchor(4, 0, 5),
            )
        assertEquals("H\nitem\ncode\nnote\nquote", selectedText(document, anchors))
    }

    @Test
    fun `empty selection yields empty string`() {
        val document = doc(ReflowBlock.Paragraph("abc"))
        assertEquals("", selectedText(document, emptyList()))
    }
}
