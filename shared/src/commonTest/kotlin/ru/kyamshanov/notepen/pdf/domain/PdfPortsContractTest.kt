package ru.kyamshanov.notepen.pdf.domain

import kotlinx.coroutines.runBlocking
import ru.kyamshanov.notepen.pdf.domain.fakes.FakePdfDocument
import ru.kyamshanov.notepen.pdf.domain.fakes.FakePdfDocumentLoader
import ru.kyamshanov.notepen.pdf.domain.fakes.FakePdfPageRenderer
import ru.kyamshanov.notepen.pdf.domain.model.PdfDocumentInfo
import ru.kyamshanov.notepen.pdf.domain.model.PdfPageInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PdfPortsContractTest {

    private val singlePageInfo = PdfDocumentInfo(
        pageCount = 1,
        pages = listOf(PdfPageInfo(0, 595f, 842f)),
    )

    // --- PdfDocumentLoader ---

    @Test
    fun loaderReturnsDocumentWithCorrectInfo() = runBlocking {
        val loader = FakePdfDocumentLoader(singlePageInfo)
        val doc = loader.load("/fake/path.pdf")
        assertEquals(singlePageInfo, doc.info)
        doc.close()
    }

    @Test
    fun loaderRecordsLastLoadedPath() = runBlocking {
        val loader = FakePdfDocumentLoader(singlePageInfo)
        loader.load("/some/path.pdf")
        assertEquals("/some/path.pdf", loader.lastLoadedPath)
    }

    // --- PdfDocument ---

    @Test
    fun documentCloseIsIdempotent() {
        val doc = FakePdfDocument(singlePageInfo)
        assertFalse(doc.closed)
        doc.close()
        assertTrue(doc.closed)
        doc.close() // second call must not throw
    }

    // --- PdfPageRenderer ---

    @Test
    fun rendererReturnsDimensionsAsRequested() = runBlocking {
        val doc = FakePdfDocument(singlePageInfo)
        val renderer = FakePdfPageRenderer()
        val result = renderer.renderPage(doc, 0, 100, 141)
        assertEquals(100, result.widthPx)
        assertEquals(141, result.heightPx)
        assertEquals(100 * 141, result.pixels.size)
    }

    @Test
    fun rendererThrowsForNegativePageIndex() = runBlocking<Unit> {
        val doc = FakePdfDocument(singlePageInfo)
        val renderer = FakePdfPageRenderer()
        assertFailsWith<IndexOutOfBoundsException> {
            renderer.renderPage(doc, -1, 100, 141)
        }
    }

    @Test
    fun rendererThrowsForPageIndexEqualToPageCount() = runBlocking<Unit> {
        val doc = FakePdfDocument(singlePageInfo)
        val renderer = FakePdfPageRenderer()
        assertFailsWith<IndexOutOfBoundsException> {
            renderer.renderPage(doc, 1, 100, 141)
        }
    }

    @Test
    fun rendererWorksForLastValidPageIndex() = runBlocking {
        val info = PdfDocumentInfo(
            pageCount = 3,
            pages = (0..2).map { PdfPageInfo(it, 595f, 842f) },
        )
        val doc = FakePdfDocument(info)
        val renderer = FakePdfPageRenderer()
        val result = renderer.renderPage(doc, 2, 50, 70)
        assertEquals(50 * 70, result.pixels.size)
    }
}
