package ru.kyamshanov.notepen.pdf.infrastructure

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import ru.kyamshanov.notepen.pdf.domain.model.PdfDocument
import ru.kyamshanov.notepen.pdf.domain.model.PdfDocumentInfo
import ru.kyamshanov.notepen.pdf.domain.model.PdfPageInfo
import ru.kyamshanov.notepen.pdf.domain.port.PdfDocumentLoader
import java.io.File

/**
 * JVM-реализация [PdfDocumentLoader] поверх Apache PDFBox.
 *
 * @param ioDispatcher диспетчер для блокирующего IO; не должен быть Main-диспетчером
 */
class JvmPdfDocumentLoader(private val ioDispatcher: CoroutineDispatcher) : PdfDocumentLoader {
    override suspend fun load(path: String): PdfDocument =
        withContext(ioDispatcher) {
            val file = File(path)
            require(file.exists()) { "PDF file not found: $path" }
            require(file.canRead()) { "PDF file is not readable: $path" }

            val document =
                try {
                    Loader.loadPDF(file)
                } catch (e: Exception) {
                    throw IllegalArgumentException("Failed to open PDF: $path", e)
                }

            val pages =
                document.pages.mapIndexed { index, page ->
                    val box = page.mediaBox
                    PdfPageInfo(
                        pageIndex = index,
                        widthPt = box.width,
                        heightPt = box.height,
                        rotation = page.rotation,
                    )
                }

            JvmPdfDocument(
                renderer = PDFRenderer(document),
                document = document,
                info = PdfDocumentInfo(pageCount = document.numberOfPages, pages = pages),
            )
        }
}
