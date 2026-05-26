package ru.kyamshanov.notepen.book

import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPoint
import ru.kyamshanov.notepen.reflow.StrokeTextMapper
import ru.kyamshanov.notepen.reflow.api.ReflowBlock
import ru.kyamshanov.notepen.reflow.api.SourceSpan
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Рендерер JVM отдаёт reflow-документ из той же верстки, что и PDF: истинная
 * структура [BookContent] (без эвристик) и провенанс [SourceSpan], по которому
 * штрихи editor'а сопоставляются с текстом ([StrokeTextMapper]).
 */
class JvmBookPdfReflowTest {
    private val book =
        BookContent(
            metadata = BookMetadata(title = "Title"),
            blocks =
                listOf(
                    ContentBlock.Heading(level = 1, text = "Heading One"),
                    ContentBlock.Paragraph(
                        listOf(
                            InlineSpan("Plain "),
                            InlineSpan("bold", bold = true),
                            InlineSpan(" and "),
                            InlineSpan("code", code = true),
                            InlineSpan(" words."),
                        ),
                    ),
                    ContentBlock.ListItem(listOf(InlineSpan("First item")), ordered = false, ordinal = 0, level = 0),
                    ContentBlock.Blockquote(listOf(InlineSpan("A quoted line."))),
                    ContentBlock.HorizontalRule,
                ),
        )

    private fun render(): ru.kyamshanov.notepen.reflow.api.ReflowDocument {
        val pdf = File.createTempFile("reflow", ".pdf")
        try {
            return JvmBookPdfRenderer.render(book, pdf).reflow
        } finally {
            pdf.delete()
        }
    }

    @Test
    fun `emits true structure from book content`() {
        val blocks = render().blocks
        assertTrue(blocks.any { it is ReflowBlock.Heading && it.text.contains("Heading One") }, "heading must survive")
        assertTrue(blocks.any { it is ReflowBlock.Paragraph && it.text.contains("Plain bold and code words.") }, "paragraph text")
        assertTrue(blocks.any { it is ReflowBlock.ListItem && it.text.contains("First item") }, "list item")
        assertTrue(blocks.any { it is ReflowBlock.Blockquote && it.text.contains("A quoted line.") }, "blockquote")
        assertTrue(blocks.any { it is ReflowBlock.Divider }, "horizontal rule -> divider")
    }

    @Test
    fun `source spans satisfy the substring invariant and lie on the page`() {
        for (block in render().blocks) {
            val (text, spans) = textAndSpans(block) ?: continue
            for (span in spans) {
                assertTrue(span.charStart in 0..text.length, "charStart in range")
                assertTrue(span.charEnd in span.charStart..text.length, "charEnd in range")
                assertTrue(span.charEnd > span.charStart, "non-empty span")
                assertTrue(span.pageIndex >= 0, "valid page")
                assertTrue(span.bounds.left <= span.bounds.right && span.bounds.top <= span.bounds.bottom, "ordered bounds")
                assertTrue(span.bounds.left in -0.02f..1.02f && span.bounds.right in -0.02f..1.02f, "x within page")
                assertTrue(span.bounds.top in -0.02f..1.02f && span.bounds.bottom in -0.02f..1.02f, "y within page")
            }
        }
    }

    @Test
    fun `inline bold and code carry into source-span flags`() {
        val paragraph = render().blocks.filterIsInstance<ReflowBlock.Paragraph>().first { it.text.contains("bold") }
        assertTrue(paragraph.source.any { it.bold }, "a bold run must be flagged")
        assertTrue(paragraph.source.any { it.monospace }, "a code run must be flagged monospace")
    }

    @Test
    fun `a stroke over an emitted span anchors that text`() {
        val doc = render()
        val paragraphIndex = doc.blocks.indexOfFirst { it is ReflowBlock.Paragraph && it.text.contains("bold") }
        val paragraph = doc.blocks[paragraphIndex] as ReflowBlock.Paragraph
        val span = paragraph.source.first { it.bold }
        val midY = (span.bounds.top + span.bounds.bottom) / 2f
        val stroke =
            DrawingPath(
                points =
                    listOf(
                        DrawingPoint(span.bounds.left + EPS, midY),
                        DrawingPoint(span.bounds.right - EPS, midY),
                    ),
            )

        val anchor =
            StrokeTextMapper
                .anchorsFor(doc, span.pageIndex, stroke)
                .firstOrNull { it.blockIndex == paragraphIndex && it.charStart < span.charEnd && it.charEnd > span.charStart }

        assertNotNull(anchor, "stroke over the word must anchor its text range")
        assertTrue(paragraph.text.substring(anchor.charStart, anchor.charEnd).contains("bold"), "anchored text covers the word")
    }

    private fun textAndSpans(block: ReflowBlock): Pair<String, List<SourceSpan>>? =
        when (block) {
            is ReflowBlock.Heading -> block.text to block.source
            is ReflowBlock.Paragraph -> block.text to block.source
            is ReflowBlock.ListItem -> block.text to block.source
            is ReflowBlock.Blockquote -> block.text to block.source
            is ReflowBlock.Table, is ReflowBlock.Figure, ReflowBlock.Divider -> null
        }

    private companion object {
        private const val EPS = 0.003f
    }
}
