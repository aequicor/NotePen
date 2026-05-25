package ru.kyamshanov.notepen.pdf.infrastructure

import kotlinx.coroutines.test.runTest
import ru.kyamshanov.notepen.pdf.domain.model.ImageBackedDocument
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JvmImageDocumentLoaderTest {
    private fun writeTempPng(
        width: Int,
        height: Int,
    ): String {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.color = Color.RED
        g.fillRect(0, 0, width, height)
        g.dispose()
        val file = File.createTempFile("notepen-test", ".png")
        file.deleteOnExit()
        ImageIO.write(img, "PNG", file)
        return file.absolutePath
    }

    @Test
    fun loads_png_as_single_page_document() =
        runTest {
            val path = writeTempPng(120, 80)
            val loader =
                JvmDocumentLoader(
                    pdfLoader = JvmPdfDocumentLoader(kotlinx.coroutines.Dispatchers.Unconfined),
                    imageLoader = JvmImageDocumentLoader(kotlinx.coroutines.Dispatchers.Unconfined),
                )
            val doc = loader.load(path)
            assertTrue(doc is ImageBackedDocument)
            assertEquals(1, doc.info.pageCount)
            assertEquals(120f, doc.info.pages[0].widthPt)
            assertEquals(80f, doc.info.pages[0].heightPt)
        }

    @Test
    fun renders_png_page_scaled() =
        runTest {
            val path = writeTempPng(120, 80)
            val loader = JvmImageDocumentLoader(kotlinx.coroutines.Dispatchers.Unconfined)
            val renderer =
                JvmPageRenderer(
                    pdfRenderer = JvmPdfPageRenderer(kotlinx.coroutines.Dispatchers.Unconfined),
                    imageRenderer = JvmImagePageRenderer(kotlinx.coroutines.Dispatchers.Unconfined),
                )
            val doc = loader.load(path)
            val data = renderer.renderPage(doc, pageIndex = 0, widthPx = 60, heightPx = 40)
            assertEquals(60, data.widthPx)
            assertEquals(40, data.heightPx)
            assertEquals(60 * 40, data.pixels.size)
            // Center pixel should be opaque red.
            val center = data.pixels[40 / 2 * 60 + 60 / 2]
            assertEquals(0xFF, (center ushr 24) and 0xFF)
            assertEquals(0xFF, (center ushr 16) and 0xFF)
        }
}
