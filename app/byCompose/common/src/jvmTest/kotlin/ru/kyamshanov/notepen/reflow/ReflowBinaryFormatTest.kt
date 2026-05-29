package ru.kyamshanov.notepen.reflow

import ru.kyamshanov.notepen.reflow.api.PdfContentKind
import ru.kyamshanov.notepen.reflow.api.ReflowBlock
import ru.kyamshanov.notepen.reflow.api.ReflowDocument
import ru.kyamshanov.notepen.reflow.api.ReflowRect
import ru.kyamshanov.notepen.reflow.api.SourceSpan
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReflowBinaryFormatTest {
    private fun roundtrip(
        doc: ReflowDocument,
        sourceSize: Long = 1234L,
        sourceMtime: Long = 5678L,
    ): ReflowBinaryFormat.CachedDocument {
        val bytes =
            ByteArrayOutputStream().apply {
                ReflowBinaryFormat.write(doc, sourceSize, sourceMtime, this)
            }.toByteArray()
        return ReflowBinaryFormat.read(ByteArrayInputStream(bytes))
    }

    @Test
    fun emptyDocumentRoundtrips() {
        val doc = ReflowDocument(kind = PdfContentKind.TEXT_BASED, blocks = emptyList())
        val cached = roundtrip(doc, sourceSize = 100L, sourceMtime = 200L)
        assertEquals(doc, cached.document)
        assertEquals(100L, cached.sourceSize)
        assertEquals(200L, cached.sourceMtime)
    }

    @Test
    fun headingRoundtrips() {
        val rect = ReflowRect(0.1f, 0.2f, 0.3f, 0.4f)
        val doc =
            ReflowDocument(
                kind = PdfContentKind.HYBRID,
                blocks =
                    listOf(
                        ReflowBlock.Heading(
                            text = "Title",
                            level = 2,
                            source = listOf(SourceSpan(3, 0, 5, rect, bold = true)),
                        ),
                    ),
            )
        assertEquals(doc, roundtrip(doc).document)
    }

    @Test
    fun paragraphWithMultibyteUtf8Roundtrips() {
        val text = "Привет, мир — café😀 测试"
        val span = SourceSpan(0, 0, text.length, ReflowRect(0f, 0f, 1f, 1f), monospace = true)
        val doc =
            ReflowDocument(
                kind = PdfContentKind.TEXT_BASED,
                blocks = listOf(ReflowBlock.Paragraph(text, listOf(span))),
            )
        assertEquals(doc, roundtrip(doc).document)
    }

    @Test
    fun mixedBlocksRoundtrip() {
        val rect = ReflowRect(0.05f, 0.1f, 0.95f, 0.2f)
        val span = SourceSpan(2, 0, 4, rect, bold = true, monospace = true)
        val doc =
            ReflowDocument(
                kind = PdfContentKind.TEXT_BASED,
                blocks =
                    listOf(
                        ReflowBlock.Heading("H1", 1, listOf(span)),
                        ReflowBlock.Paragraph("para text", listOf(span)),
                        ReflowBlock.ListItem("• item", listOf(span)),
                        ReflowBlock.Blockquote("quoted", listOf(span)),
                        ReflowBlock.Table(
                            rows =
                                listOf(
                                    ReflowBlock.TableRow(
                                        cells =
                                            listOf(
                                                ReflowBlock.TableCell("c1", listOf(span)),
                                                ReflowBlock.TableCell("c2"),
                                            ),
                                    ),
                                    ReflowBlock.TableRow(
                                        cells =
                                            listOf(
                                                ReflowBlock.TableCell("c3"),
                                                ReflowBlock.TableCell("c4", listOf(span)),
                                            ),
                                    ),
                                ),
                        ),
                        ReflowBlock.Figure(7, ReflowRect(0.1f, 0.2f, 0.3f, 0.4f), aspectRatio = 1.6f),
                        ReflowBlock.Divider,
                    ),
            )
        assertEquals(doc, roundtrip(doc).document)
    }

    @Test
    fun spanFlagCombinationsRoundtrip() {
        val rect = ReflowRect(0f, 0f, 1f, 1f)
        val spans =
            listOf(
                SourceSpan(0, 0, 1, rect, bold = false, monospace = false),
                SourceSpan(0, 1, 2, rect, bold = true, monospace = false),
                SourceSpan(0, 2, 3, rect, bold = false, monospace = true),
                SourceSpan(0, 3, 4, rect, bold = true, monospace = true),
            )
        val doc =
            ReflowDocument(
                kind = PdfContentKind.IMAGE_ONLY,
                blocks = listOf(ReflowBlock.Paragraph("abcd", spans)),
            )
        assertEquals(doc, roundtrip(doc).document)
    }

    @Test
    fun tableConfidenceRoundtrips() {
        // v3: Table.confidence сохраняется в кэше; без round-trip cache-hit
        // приходил бы с дефолтным 1f, теряя Stream-сигнал для Lattice refiner'а.
        val table =
            ReflowBlock.Table(
                rows = listOf(ReflowBlock.TableRow(cells = listOf(ReflowBlock.TableCell("x"), ReflowBlock.TableCell("y")))),
                confidence = 0.42f,
            )
        val doc = ReflowDocument(kind = PdfContentKind.HYBRID, blocks = listOf(table))
        val restored = roundtrip(doc).document.blocks.single() as ReflowBlock.Table
        assertEquals(0.42f, restored.confidence)
        assertEquals(table, restored)
    }

    @Test
    fun figureWasTableFallbackRoundtrips() {
        val figure =
            ReflowBlock.Figure(
                pageIndex = 5,
                bounds = ReflowRect(0.1f, 0.2f, 0.8f, 0.6f),
                aspectRatio = 1.5f,
                wasTableFallback = true,
            )
        val doc = ReflowDocument(kind = PdfContentKind.HYBRID, blocks = listOf(figure))
        val restored = doc.let { roundtrip(it).document.blocks.single() as ReflowBlock.Figure }
        assertEquals(true, restored.wasTableFallback)
        assertEquals(figure, restored)
    }

    @Test
    fun spanItalicFlagRoundtrips() {
        // v3 flag bit 0x04: italic.
        val rect = ReflowRect(0f, 0f, 1f, 1f)
        val spans =
            listOf(
                SourceSpan(0, 0, 1, rect, italic = true),
                SourceSpan(0, 1, 2, rect, bold = true, italic = true),
                SourceSpan(0, 2, 3, rect, monospace = true, italic = false),
                SourceSpan(0, 3, 4, rect, bold = true, monospace = true, italic = true),
            )
        val doc = ReflowDocument(kind = PdfContentKind.TEXT_BASED, blocks = listOf(ReflowBlock.Paragraph("abcd", spans)))
        val restoredSpans = (roundtrip(doc).document.blocks.single() as ReflowBlock.Paragraph).source
        assertEquals(spans, restoredSpans)
    }

    @Test
    fun parserVersionRoundtrips() {
        // v5: parserVersion пишется в заголовок и читается обратно.
        val doc = ReflowDocument(kind = PdfContentKind.TEXT_BASED, blocks = emptyList())
        // По умолчанию — текущая версия парсера.
        assertEquals(
            ru.kyamshanov.notepen.reflow.api.REFLOW_PARSER_VERSION,
            roundtrip(doc).parserVersion,
        )
        // Явно заданная версия round-trip'ится без искажений.
        val bytes =
            ByteArrayOutputStream().apply {
                ReflowBinaryFormat.write(doc, 1L, 2L, this, parserVersion = 99)
            }.toByteArray()
        assertEquals(99, ReflowBinaryFormat.read(ByteArrayInputStream(bytes)).parserVersion)
    }

    @Test
    fun badMagicFails() {
        val bytes = ByteArray(40) { 0 }
        assertFailsWith<IllegalArgumentException> {
            ReflowBinaryFormat.read(ByteArrayInputStream(bytes))
        }
    }
}
