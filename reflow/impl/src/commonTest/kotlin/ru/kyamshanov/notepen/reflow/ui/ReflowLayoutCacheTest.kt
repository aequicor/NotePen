package ru.kyamshanov.notepen.reflow.ui

import ru.kyamshanov.notepen.reflow.api.PdfContentKind
import ru.kyamshanov.notepen.reflow.api.ReflowBlock
import ru.kyamshanov.notepen.reflow.api.ReflowDocument
import ru.kyamshanov.notepen.reflow.api.ReflowRect
import ru.kyamshanov.notepen.reflow.api.SourceSpan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ReflowLayoutCacheTest {
    private fun docOf(vararg blocks: ReflowBlock): ReflowDocument = ReflowDocument(PdfContentKind.TEXT_BASED, blocks.toList())

    private val rect = ReflowRect(0f, 0f, 1f, 1f)

    @Test
    fun fingerprintIsDeterministic() {
        val doc =
            docOf(
                ReflowBlock.Heading("H", 1),
                ReflowBlock.Paragraph("p1 text"),
                ReflowBlock.Figure(7, rect, aspectRatio = 1.6f),
                ReflowBlock.Divider,
            )
        assertEquals(fingerprintDocument(doc), fingerprintDocument(doc))
    }

    @Test
    fun differentTextChangesFingerprint() {
        val a = docOf(ReflowBlock.Paragraph("hello"))
        val b = docOf(ReflowBlock.Paragraph("hello!"))
        assertNotEquals(fingerprintDocument(a), fingerprintDocument(b))
    }

    @Test
    fun differentBlockTypeChangesFingerprint() {
        val a = docOf(ReflowBlock.Paragraph("hello"))
        val b = docOf(ReflowBlock.Heading("hello", level = 1))
        assertNotEquals(fingerprintDocument(a), fingerprintDocument(b))
    }

    @Test
    fun figureFieldsChangeFingerprint() {
        val a = docOf(ReflowBlock.Figure(1, rect, aspectRatio = 1.0f))
        val b = docOf(ReflowBlock.Figure(1, rect, aspectRatio = 1.5f))
        val c = docOf(ReflowBlock.Figure(2, rect, aspectRatio = 1.0f))
        assertNotEquals(fingerprintDocument(a), fingerprintDocument(b))
        assertNotEquals(fingerprintDocument(a), fingerprintDocument(c))
    }

    @Test
    fun tableTextChangesFingerprint() {
        val a =
            docOf(
                ReflowBlock.Table(
                    rows = listOf(ReflowBlock.TableRow(cells = listOf(ReflowBlock.TableCell("foo")))),
                ),
            )
        val b =
            docOf(
                ReflowBlock.Table(
                    rows = listOf(ReflowBlock.TableRow(cells = listOf(ReflowBlock.TableCell("bar")))),
                ),
            )
        assertNotEquals(fingerprintDocument(a), fingerprintDocument(b))
    }

    @Test
    fun unicodeIsHashedSomehow() {
        // Should not throw, deterministic. Different unicode → different fingerprints.
        val a = docOf(ReflowBlock.Paragraph("Привет, мир"))
        val b = docOf(ReflowBlock.Paragraph("Hello, world"))
        assertEquals(fingerprintDocument(a), fingerprintDocument(a))
        assertNotEquals(fingerprintDocument(a), fingerprintDocument(b))
    }

    @Test
    fun ignoresSourceSpans() {
        // Source spans don't influence layout (they're style hints, not content).
        // Fingerprint focuses on content; we don't include spans in mix to stay fast.
        // Verify two docs with same text but different spans have same fingerprint.
        val withoutSpan = docOf(ReflowBlock.Paragraph("hello"))
        val withSpan = docOf(ReflowBlock.Paragraph("hello", source = listOf(SourceSpan(0, 0, 5, rect, bold = true))))
        assertEquals(fingerprintDocument(withoutSpan), fingerprintDocument(withSpan))
    }
}
