package ru.kyamshanov.notepen.mainscreen.infrastructure

import android.content.Context
import android.net.Uri
import ru.kyamshanov.notepen.book.EbookToPdfConverter
import ru.kyamshanov.notepen.mainscreen.domain.port.PdfThumbnailGenerator
import ru.kyamshanov.notepen.pdf.infrastructure.isSupportedImageMime
import ru.kyamshanov.notepen.pdf.infrastructure.isSupportedImagePath
import java.io.File

/**
 * Композитный [PdfThumbnailGenerator] для Android: определяет тип файла (MIME через
 * ContentResolver, fallback на расширение) и делегирует генерацию изображениям или PDF.
 *
 * Электронные книги (EPUB/FB2/комиксы) — не PDF, поэтому перед PDF-веткой они
 * сначала верстаются в PDF через [converter] (тот же `cacheDir`-кеш, что и при
 * открытии документа). Без этого `PdfRenderer` на сыром ebook-архиве падает, и
 * эскиз скатывается в `ThumbnailState.Error` (пиктограмма «битое изображение»).
 * Зеркалит desktop-путь (`PdfThumbnailGeneratorDesktop`).
 *
 * @param converter конвертер книга → PDF; для не-книжных путей не вызывается
 */
class AndroidThumbnailGenerator(
    private val context: Context,
    private val pdfGenerator: PdfThumbnailGenerator,
    private val imageGenerator: PdfThumbnailGenerator,
    private val converter: EbookToPdfConverter,
) : PdfThumbnailGenerator {
    override suspend fun generate(
        uri: String,
        widthPx: Int,
        heightPx: Int,
    ): Result<ByteArray> =
        when {
            converter.canConvert(uri) -> {
                runCatching { converter.ensurePdf(uri) }.fold(
                    // ensurePdf returns a bare filesystem path; PdfThumbnailGeneratorAndroid
                    // opens via contentResolver.openFileDescriptor(Uri.parse(uri)), so wrap
                    // it as a file:// URI rather than passing the raw path.
                    onSuccess = { pdfPath ->
                        pdfGenerator.generate(Uri.fromFile(File(pdfPath)).toString(), widthPx, heightPx)
                    },
                    onFailure = { Result.failure(it) },
                )
            }
            isImage(uri) -> imageGenerator.generate(uri, widthPx, heightPx)
            else -> pdfGenerator.generate(uri, widthPx, heightPx)
        }

    private fun isImage(uri: String): Boolean {
        val mime = runCatching { context.contentResolver.getType(Uri.parse(uri)) }.getOrNull()
        if (mime != null) return isSupportedImageMime(mime)
        return isSupportedImagePath(uri)
    }
}
