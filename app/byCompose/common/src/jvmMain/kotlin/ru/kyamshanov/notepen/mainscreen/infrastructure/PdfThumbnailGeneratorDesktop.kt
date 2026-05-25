package ru.kyamshanov.notepen.mainscreen.infrastructure

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.rendering.RenderDestination
import ru.kyamshanov.notepen.book.EbookToPdfConverter
import ru.kyamshanov.notepen.mainscreen.domain.exception.ThumbnailGenerationException
import ru.kyamshanov.notepen.mainscreen.domain.port.PdfThumbnailGenerator
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

/**
 * Desktop (JVM)-реализация [PdfThumbnailGenerator].
 *
 * Использует Apache PdfBox для рендеринга первой страницы PDF в PNG.
 *
 * Электронные книги (EPUB/FB2/комиксы) не являются PDF, поэтому перед загрузкой
 * они сначала верстаются в PDF через [converter] (тот же кеш, что и при открытии
 * документа). Без этого `Loader.loadPDF` на сыром ebook'е падает с разбором
 * (например, «End-of-File, expected line»), и эскиз не строится.
 *
 * Пустой PDF (0 страниц) → Result.failure(ThumbnailGenerationException) (TC-44, CC-4).
 * Все исключения оборачиваются в [ThumbnailGenerationException].
 *
 * @param converter конвертер книга → PDF; для не-книжных путей вызов
 *   проксируется без изменений
 * @param ioDispatcher диспетчер для блокирующего IO/CPU; не должен быть Main
 */
class PdfThumbnailGeneratorDesktop(
    private val converter: EbookToPdfConverter,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PdfThumbnailGenerator {
    override suspend fun generate(
        uri: String,
        widthPx: Int,
        heightPx: Int,
    ): Result<ByteArray> =
        withContext(ioDispatcher) {
            runCatching {
                require(widthPx in 1..4096 && heightPx in 1..4096) {
                    "Thumbnail dimensions out of range: ${widthPx}x$heightPx"
                }
                val pdfPath = if (converter.canConvert(uri)) converter.ensurePdf(uri) else uri
                Loader.loadPDF(File(pdfPath)).use { doc ->
                    if (doc.numberOfPages == 0) throw ThumbnailGenerationException("Empty PDF: no pages")
                    val renderer = PDFRenderer(doc)
                    val page = doc.pages[0]
                    val scale = widthPx.toFloat() / page.mediaBox.width
                    // EXPORT avoids screen-dependent Java2D pipeline on Windows (DirectX/D3D)
                    val raw: BufferedImage = renderer.renderImage(0, scale, ImageType.RGB, RenderDestination.EXPORT)
                    val image =
                        if (raw.type == BufferedImage.TYPE_INT_ARGB) {
                            raw
                        } else {
                            val converted = BufferedImage(raw.width, raw.height, BufferedImage.TYPE_INT_ARGB)
                            val g = converted.createGraphics()
                            g.drawImage(raw, 0, 0, null)
                            g.dispose()
                            converted
                        }
                    val stream = ByteArrayOutputStream()
                    if (!ImageIO.write(image, "PNG", stream)) {
                        throw ThumbnailGenerationException("No PNG writer for image type ${raw.type}")
                    }
                    stream.toByteArray()
                }
            }.mapFailure { cause ->
                ThumbnailGenerationException("Thumbnail generation failed", cause)
            }
        }
}

private fun <T> Result<T>.mapFailure(transform: (Throwable) -> Throwable): Result<T> =
    exceptionOrNull()?.let { Result.failure(transform(it)) } ?: this
