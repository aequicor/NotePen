package ru.kyamshanov.notepen.mainscreen.infrastructure

import ru.kyamshanov.notepen.mainscreen.domain.port.PdfThumbnailGenerator
import ru.kyamshanov.notepen.pdf.infrastructure.isSupportedImagePath

/**
 * Композитный [PdfThumbnailGenerator] для desktop: по расширению пути делегирует
 * генерацию миниатюры либо изображениям, либо PDF.
 */
class JvmThumbnailGenerator(
    private val pdfGenerator: PdfThumbnailGenerator,
    private val imageGenerator: PdfThumbnailGenerator,
) : PdfThumbnailGenerator {
    override suspend fun generate(
        uri: String,
        widthPx: Int,
        heightPx: Int,
    ): Result<ByteArray> =
        if (isSupportedImagePath(uri)) {
            imageGenerator.generate(uri, widthPx, heightPx)
        } else {
            pdfGenerator.generate(uri, widthPx, heightPx)
        }
}
