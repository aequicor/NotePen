package ru.kyamshanov.notepen.book

import kotlinx.coroutines.test.runTest
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.port.PdfExporter
import ru.kyamshanov.notepen.pdf.domain.model.PdfDocument
import ru.kyamshanov.notepen.pdf.domain.model.PdfDocumentInfo
import ru.kyamshanov.notepen.pdf.domain.port.PdfDocumentLoader
import ru.kyamshanov.notepen.reflow.api.PdfContentKind
import ru.kyamshanov.notepen.reflow.api.PdfReflowExtractor
import ru.kyamshanov.notepen.reflow.api.ReflowDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Декораторы должны подменять путь EPUB на путь сконвертированного PDF и
 * проксировать всё остальное без изменений.
 */
class EpubAwareDecoratorsTest {
    private class FakeConverter(
        private val epub: Boolean,
        private val reflowDoc: ReflowDocument? = null,
    ) : EbookToPdfConverter,
        BookReflowProvider {
        override fun canConvert(path: String): Boolean = epub

        override suspend fun ensurePdf(path: String): String = "$path#pdf"

        override suspend fun reflowFor(path: String): ReflowDocument? = reflowDoc
    }

    private class RecordingLoader : PdfDocumentLoader {
        var seen: String? = null

        override suspend fun load(path: String): PdfDocument {
            seen = path
            return object : PdfDocument {
                override val info = PdfDocumentInfo(pageCount = 0, pages = emptyList())

                override fun close() = Unit
            }
        }
    }

    private class RecordingExporter : PdfExporter {
        var seen: String? = null

        override suspend fun export(
            sourcePdfPath: String,
            annotations: Map<Int, List<DrawingPath>>,
            outputPath: String,
        ): Result<Unit> {
            seen = sourcePdfPath
            return Result.success(Unit)
        }
    }

    private class RecordingExtractor : PdfReflowExtractor {
        var seen: String? = null

        override suspend fun probe(path: String): PdfContentKind {
            seen = path
            return PdfContentKind.TEXT_BASED
        }

        override suspend fun extract(path: String): ReflowDocument {
            seen = path
            return ReflowDocument(kind = PdfContentKind.TEXT_BASED, blocks = emptyList())
        }
    }

    @Test
    fun loader_converts_epub_then_delegates() =
        runTest {
            val delegate = RecordingLoader()
            EbookAwarePdfDocumentLoader(delegate, FakeConverter(epub = true)).load("/a/x.epub")
            assertEquals("/a/x.epub#pdf", delegate.seen)
        }

    @Test
    fun loader_passes_non_epub_through() =
        runTest {
            val delegate = RecordingLoader()
            EbookAwarePdfDocumentLoader(delegate, FakeConverter(epub = false)).load("/a/x.pdf")
            assertEquals("/a/x.pdf", delegate.seen)
        }

    @Test
    fun exporter_converts_epub_source() =
        runTest {
            val delegate = RecordingExporter()
            EbookAwarePdfExporter(delegate, FakeConverter(epub = true))
                .export("/a/x.epub", emptyMap(), "/out.pdf")
            assertEquals("/a/x.epub#pdf", delegate.seen)
        }

    @Test
    fun extractor_uses_book_reflow_when_available() =
        runTest {
            val delegate = RecordingExtractor()
            val doc = ReflowDocument(kind = PdfContentKind.TEXT_BASED, blocks = emptyList())
            val converter = FakeConverter(epub = true, reflowDoc = doc)
            val result = EbookAwarePdfReflowExtractor(delegate, converter, converter).extract("/a/x.epub")
            assertSame(doc, result)
            assertNull(delegate.seen) // структура из книги — PDF не выскребается
        }

    @Test
    fun extractor_falls_back_to_pdf_when_no_sidecar() =
        runTest {
            val delegate = RecordingExtractor()
            val converter = FakeConverter(epub = true, reflowDoc = null)
            EbookAwarePdfReflowExtractor(delegate, converter, converter).extract("/a/x.epub")
            assertEquals("/a/x.epub#pdf", delegate.seen)
        }

    @Test
    fun extractor_passes_non_ebook_through() =
        runTest {
            val delegate = RecordingExtractor()
            val converter = FakeConverter(epub = false)
            EbookAwarePdfReflowExtractor(delegate, converter, converter).extract("/a/x.pdf")
            assertEquals("/a/x.pdf", delegate.seen)
        }
}
