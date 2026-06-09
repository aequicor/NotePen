package ru.kyamshanov.notepen.pdf.infrastructure

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import ru.kyamshanov.notepen.pdf.domain.model.PdfPageData
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class JvmPdfPageRendererTest {
    @Test
    fun renderPageKeepsBlankPdfBackgroundOpaqueWhite() =
        runTest {
            val path = writeBlankPdf()
            val loader = JvmPdfDocumentLoader(Dispatchers.Unconfined)
            val renderer = JvmPdfPageRenderer(Dispatchers.Unconfined)
            val doc = loader.load(path)

            try {
                val page = renderer.renderPage(doc, pageIndex = 0, widthPx = 64, heightPx = 64)

                assertOpaqueWhite(page)
            } finally {
                doc.close()
            }
        }

    @Test
    fun renderTileKeepsBlankPdfBackgroundOpaqueWhite() =
        runTest {
            val path = writeBlankPdf()
            val loader = JvmPdfDocumentLoader(Dispatchers.Unconfined)
            val renderer = JvmPdfPageRenderer(Dispatchers.Unconfined)
            val doc = loader.load(path)

            try {
                val directTile =
                    renderer.renderTile(
                        document = doc,
                        pageIndex = 0,
                        fullPageWidthPx = 512,
                        fullPageHeightPx = 512,
                        tileLeftPx = 128,
                        tileTopPx = 128,
                        tileWidthPx = 64,
                        tileHeightPx = 64,
                    )
                val rotatedFallbackTile =
                    renderer.renderTile(
                        document = doc,
                        pageIndex = 0,
                        fullPageWidthPx = 512,
                        fullPageHeightPx = 512,
                        tileLeftPx = 128,
                        tileTopPx = 128,
                        tileWidthPx = 64,
                        tileHeightPx = 64,
                        rotationQuarters = 1,
                    )

                assertOpaqueWhite(directTile)
                assertOpaqueWhite(rotatedFallbackTile)
            } finally {
                doc.close()
            }
        }

    private fun writeBlankPdf(): String {
        val file = File.createTempFile("notepen-blank", ".pdf")
        file.deleteOnExit()
        PDDocument().use { doc ->
            doc.addPage(PDPage(PDRectangle.LETTER))
            doc.save(file)
        }
        return file.absolutePath
    }

    private fun assertOpaqueWhite(page: PdfPageData) {
        assertEquals(page.widthPx * page.heightPx, page.pixels.size)
        page.pixels.forEach { pixel ->
            assertEquals(0xFF_FF_FF_FF.toInt(), pixel)
        }
    }
}
