package ru.kyamshanov.notepen.mainscreen.infrastructure

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.kyamshanov.notepen.mainscreen.domain.exception.ThumbnailGenerationException
import ru.kyamshanov.notepen.mainscreen.domain.port.PdfThumbnailGenerator
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

/**
 * Desktop (JVM)-генератор миниатюр для растровых изображений (PNG/JPEG).
 *
 * Декодирует изображение, вписывает в [widthPx]×[heightPx] с сохранением пропорций и кодирует в PNG.
 * Все исключения оборачиваются в [ThumbnailGenerationException].
 */
class ImageThumbnailGeneratorDesktop : PdfThumbnailGenerator {

    override suspend fun generate(uri: String, widthPx: Int, heightPx: Int): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(widthPx in 1..4096 && heightPx in 1..4096) {
                    "Thumbnail dimensions out of range: ${widthPx}x${heightPx}"
                }
                val source = ImageIO.read(File(uri))
                    ?: throw ThumbnailGenerationException("Unsupported or corrupt image: $uri")

                val scale = minOf(
                    widthPx.toFloat() / source.width,
                    heightPx.toFloat() / source.height,
                )
                val targetW = (source.width * scale).toInt().coerceAtLeast(1)
                val targetH = (source.height * scale).toInt().coerceAtLeast(1)

                val target = BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB)
                val g = target.createGraphics()
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                g.drawImage(source, 0, 0, targetW, targetH, null)
                g.dispose()

                val stream = ByteArrayOutputStream()
                if (!ImageIO.write(target, "PNG", stream)) {
                    throw ThumbnailGenerationException("No PNG writer available")
                }
                stream.toByteArray()
            }.mapFailure { cause ->
                ThumbnailGenerationException("Thumbnail generation failed", cause)
            }
        }
}

private fun <T> Result<T>.mapFailure(transform: (Throwable) -> Throwable): Result<T> =
    exceptionOrNull()?.let { Result.failure(transform(it)) } ?: this
