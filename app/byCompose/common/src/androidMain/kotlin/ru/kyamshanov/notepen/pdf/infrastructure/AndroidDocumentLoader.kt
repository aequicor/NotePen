package ru.kyamshanov.notepen.pdf.infrastructure

import android.content.Context
import android.net.Uri
import ru.kyamshanov.notepen.pdf.domain.model.PdfDocument
import ru.kyamshanov.notepen.pdf.domain.port.PdfDocumentLoader

/**
 * Композитный [PdfDocumentLoader] для Android: определяет тип файла и делегирует
 * загрузку либо [AndroidImageDocumentLoader] (PNG/JPEG), либо [AndroidPdfDocumentLoader] (PDF).
 *
 * Тип определяется по MIME через ContentResolver (для `content://`), с fallback на расширение пути.
 */
class AndroidDocumentLoader(
    private val context: Context,
    private val pdfLoader: PdfDocumentLoader,
    private val imageLoader: PdfDocumentLoader,
) : PdfDocumentLoader {

    override suspend fun load(path: String): PdfDocument =
        if (isImage(path)) imageLoader.load(path) else pdfLoader.load(path)

    private fun isImage(path: String): Boolean {
        val uri = Uri.parse(path)
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        if (mime != null) return isSupportedImageMime(mime)
        return isSupportedImagePath(path)
    }
}
