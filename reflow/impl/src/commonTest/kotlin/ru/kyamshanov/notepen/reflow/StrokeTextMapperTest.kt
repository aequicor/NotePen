package ru.kyamshanov.notepen.reflow

import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPoint
import ru.kyamshanov.notepen.reflow.api.ReflowBlock
import ru.kyamshanov.notepen.reflow.api.ReflowDocument
import ru.kyamshanov.notepen.reflow.api.TextAnchor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StrokeTextMapperTest {
    @Test
    fun `stroke over one word anchors that word`() {
        val doc = helloWorld()
        val anchors = StrokeTextMapper.anchorsFor(doc, pageIndex = 0, path = stroke(norm(52f, 103f), norm(78f, 107f)))
        assertEquals(listOf(TextAnchor(0, 0, 5)), anchors)
        assertEquals("hello", substringOf(doc, anchors.single()))
    }

    @Test
    fun `stroke over two adjacent words anchors the merged range`() {
        val doc = helloWorld()
        val anchors = StrokeTextMapper.anchorsFor(doc, pageIndex = 0, path = stroke(norm(52f, 103f), norm(114f, 107f)))
        assertEquals(listOf(TextAnchor(0, 0, 11)), anchors)
        assertEquals("hello world", substringOf(doc, anchors.single()))
    }

    @Test
    fun `stroke on a different page anchors nothing`() {
        val anchors = StrokeTextMapper.anchorsFor(helloWorld(), pageIndex = 1, path = stroke(norm(52f, 103f), norm(78f, 107f)))
        assertTrue(anchors.isEmpty())
    }

    @Test
    fun `stroke away from text anchors nothing`() {
        val anchors = StrokeTextMapper.anchorsFor(helloWorld(), pageIndex = 0, path = stroke(norm(52f, 700f), norm(78f, 720f)))
        assertTrue(anchors.isEmpty())
    }

    @Test
    fun `empty stroke anchors nothing`() {
        assertTrue(StrokeTextMapper.anchorsFor(helloWorld(), pageIndex = 0, path = DrawingPath()).isEmpty())
    }

    @Test
    fun `stroke grazing only the separator anchors nothing`() {
        val anchors = StrokeTextMapper.anchorsFor(helloWorld(), pageIndex = 0, path = stroke(norm(81f, 103f), norm(85f, 107f)))
        assertTrue(anchors.isEmpty())
    }

    @Test
    fun `stroke over a hyphen-joined word anchors the whole word`() {
        val doc = ReflowAssembler.assemble(listOf(page(line("exam-", top = 100f) + line("ple sentence", top = 112f))))
        val anchors = StrokeTextMapper.anchorsFor(doc, pageIndex = 0, path = stroke(norm(52f, 103f), norm(72f, 118f)))
        assertEquals(listOf(TextAnchor(0, 0, 7)), anchors)
        assertEquals("example", substringOf(doc, anchors.single()))
    }

    @Test
    fun `partial-word stroke anchors only covered glyphs`() {
        val doc = helloWorld()
        val anchors = StrokeTextMapper.anchorsFor(doc, pageIndex = 0, path = stroke(norm(88f, 103f), norm(102f, 107f)))
        assertEquals(listOf(TextAnchor(0, 6, 9)), anchors)
        assertEquals("wor", substringOf(doc, anchors.single()))
    }

    @Test
    fun `anchors are ordered by block index`() {
        val doc = ReflowAssembler.assemble(listOf(page(line("alpha", top = 100f) + line("beta", top = 200f))))
        val anchors = StrokeTextMapper.anchorsFor(doc, pageIndex = 0, path = stroke(norm(50f, 103f), norm(82f, 205f)))
        assertEquals(listOf(0, 1), anchors.map { it.blockIndex })
    }

    @Test
    fun `multi-subpath stroke still anchors by full bounding box`() {
        val doc = helloWorld()
        val path = stroke(norm(52f, 103f), DrawingPoint(78f / PAGE_WIDTH_PT, 107f / PAGE_HEIGHT_PT, isNewPath = true))
        assertEquals(listOf(TextAnchor(0, 0, 5)), StrokeTextMapper.anchorsFor(doc, pageIndex = 0, path = path))
    }

    @Test
    fun `spansFor maps a text range back to page-anchored spans`() {
        val doc = helloWorld()
        val spans = StrokeTextMapper.spansFor(doc, TextAnchor(0, 0, 5))
        assertEquals(5, spans.size)
        assertTrue(spans.all { it.pageIndex == 0 })
        assertTrue(spans.all { it.bounds.left in 0f..1f && it.bounds.top in 0f..1f })
    }

    private fun helloWorld(): ReflowDocument = ReflowAssembler.assemble(listOf(page(line("hello world", top = 100f))))

    private fun substringOf(
        doc: ReflowDocument,
        anchor: TextAnchor,
    ): String {
        val text =
            when (val block = doc.blocks[anchor.blockIndex]) {
                is ReflowBlock.Paragraph -> block.text
                is ReflowBlock.Heading -> block.text
                is ReflowBlock.ListItem -> block.text
                is ReflowBlock.Blockquote -> block.text
                is ReflowBlock.Table -> ""
                is ReflowBlock.Figure -> ""
                ReflowBlock.Divider -> ""
            }
        return text.substring(anchor.charStart, anchor.charEnd)
    }

    private fun norm(
        xPt: Float,
        yPt: Float,
    ): DrawingPoint = DrawingPoint(xPt / PAGE_WIDTH_PT, yPt / PAGE_HEIGHT_PT)

    private fun stroke(vararg points: DrawingPoint): DrawingPath = DrawingPath(points = points.toList())
}
