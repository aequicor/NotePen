package ru.kyamshanov.notepen.pdf.infrastructure

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import ru.kyamshanov.notepen.pdf.domain.model.PdfDocument
import ru.kyamshanov.notepen.pdf.domain.model.PdfDocumentInfo
import ru.kyamshanov.notepen.pdf.domain.model.PdfPageInfo
import ru.kyamshanov.notepen.pdf.domain.port.PdfDocumentLoader

/**
 * Android-реализация [PdfDocumentLoader] поверх [android.graphics.pdf.PdfRenderer].
 *
 * Принимает как `file://`-пути, так и `content://`-URI (SAF), открывая их через [ContentResolver].
 *
 * @param context контекст приложения для доступа к [ContentResolver]
 * @param ioDispatcher диспетчер для блокирующего IO; не должен быть Main-диспетчером
 */
class AndroidPdfDocumentLoader(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
) : PdfDocumentLoader {

    override suspend fun load(path: String): PdfDocument = withContext(ioDispatcher) {
        val uri = Uri.parse(path)
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalArgumentException("Cannot open file descriptor for: $path")

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
