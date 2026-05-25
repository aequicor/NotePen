package ru.kyamshanov.notepen.pdf.infrastructure

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
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
 * Принимает три формата `path`:
 *  - абсолютный путь файловой системы без схемы (например, файлы, скачанные синком в cache);
 *  - `file://…` URI;
 *  - `content://…` URI (SAF) — открывается через [android.content.ContentResolver].
 *
 * @param context контекст приложения для доступа к ContentResolver
 * @param ioDispatcher диспетчер для блокирующего IO; не должен быть Main-диспетчером
 */
class AndroidPdfDocumentLoader(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
) : PdfDocumentLoader {
    override suspend fun load(path: String): PdfDocument =
        withContext(ioDispatcher) {
            val uri = Uri.parse(path)
            val pfd =
                when (uri.scheme) {
                    null, "file" -> openFromFile(uri.path ?: path)
                    else ->
                        context.contentResolver.openFileDescriptor(uri, "r")
                            ?: throw IllegalArgumentException("Cannot open file descriptor for: $path")
                }

            val renderer =
                try {
                    PdfRenderer(pfd)
                } catch (e: Exception) {
                    pfd.close()
                    throw IllegalArgumentException("Failed to open PDF renderer: $path", e)
                }

            val pages =
                (0 until renderer.pageCount).map { index ->
                    val page = renderer.openPage(index)
                    val info =
                        PdfPageInfo(
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

    private fun openFromFile(filePath: String): ParcelFileDescriptor {
        val file = File(filePath)
        if (!file.exists() || !file.canRead()) {
            throw java.io.FileNotFoundException("PDF file not found or not readable: $filePath")
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }
}
