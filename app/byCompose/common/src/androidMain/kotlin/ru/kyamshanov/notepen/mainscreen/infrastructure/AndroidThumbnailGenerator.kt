package ru.kyamshanov.notepen.mainscreen.infrastructure

import android.content.Context
import android.net.Uri
import ru.kyamshanov.notepen.mainscreen.domain.port.PdfThumbnailGenerator
import ru.kyamshanov.notepen.pdf.infrastructure.isSupportedImageMime
import ru.kyamshanov.notepen.pdf.infrastructure.isSupportedImagePath

/**
 * Композитный [PdfThumbnailGenerator] для Android: определяет тип файла (MIME через
 * ContentResolver, fallback на расширение) и делегирует генерацию изображениям или PDF.
 */
class AndroidThumbnailGenerator(
    private val context: Context,
    private val pdfGenerator: PdfThumbnailGenerator,
    private val imageGenerator: PdfThumbnailGenerator,
) : PdfThumbnailGenerator {

    override suspend fun generate(uri: String, widthPx: Int, heightPx: Int): Result<ByteArray> =
        if (isImage(uri)) {
            imageGenerator.generate(uri, widthPx, heightPx)
        } else {
            pdfGenerator.generate(uri, widthPx, heightPx)
        }

    private fun isImage(uri: String): Boolean {
        val mime = runCatching { context.contentResolver.getType(Uri.parse(uri)) }.getOrNull()
        if (mime != null) return isSupportedImageMime(mime)
        return isSupportedImagePath(uri)
    }
}
