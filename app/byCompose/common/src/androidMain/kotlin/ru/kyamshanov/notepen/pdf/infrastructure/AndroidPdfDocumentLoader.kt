package ru.kyamshanov.notepen.pdf.infrastructure

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import ru.kyamshanov.notepen.pdf.domain.model.PdfDocument
import ru.kyamshanov.notepen.pdf.domain.model.PdfDocumentInfo
import ru.kyamshanov.notepen.pdf.domain.model.PdfPageInfo
import ru.kyamshanov.notepen.pdf.domain.port.PdfDocumentLoader
import java.io.File

/**
 * Android-реализация [PdfDocumentLoader] поверх [android.graphics.pdf.PdfRenderer].
 *
 * @param ioDispatcher диспетчер для блокирующего IO; не должен быть Main-диспетчером
 */
class AndroidPdfDocumentLoader(private val ioDispatcher: CoroutineDispatcher) : PdfDocumentLoader {

    override suspend fun load(path: String): PdfDocument = withContext(ioDispatcher) {
        val file = File(path)
        require(file.exists()) { "PDF file not found: $path" }
        require(file.canRead()) { "PDF file is not readable: $path" }

        val pfd = try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to open PDF file descriptor: $path", e)
        }

        val renderer = try {
            PdfRenderer(pfd)
        } catch (e: Exception) {
            pfd.close()
            throw IllegalArgumentException("Failed to open PDF renderer: $path", e)
        }

        val pages = (0 until renderer.pageCount).map { index ->
            val page = renderer.openPage(index)
            val info = PdfPageInfo(
                pageIndex = index,
                widthPt = page.width.toFloat(),
                heightPt = page.height.toFloat(),
            )
            page.close()
            info
        }

        AndroidPdfDocument(
            renderer = renderer,
            pfd = pfd,
            info = PdfDocumentInfo(pageCount = renderer.pageCount, pages = pages),
        )
    }
}
