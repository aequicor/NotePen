package ru.kyamshanov.notepen.pdf.infrastructure

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import ru.kyamshanov.notepen.pdf.domain.model.PdfDocument
import ru.kyamshanov.notepen.pdf.domain.model.PdfDocumentInfo
import ru.kyamshanov.notepen.pdf.domain.model.PdfPageInfo
import ru.kyamshanov.notepen.pdf.domain.port.PdfDocumentLoader
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * JVM-реализация [PdfDocumentLoader] для растровых изображений (PNG/JPEG).
 *
 * Декодирует файл в ARGB-буфер и представляет его как документ из одной страницы.
 *
 * @param ioDispatcher диспетчер для блокирующего IO; не должен быть Main-диспетчером
 */
class JvmImageDocumentLoader(private val ioDispatcher: CoroutineDispatcher) : PdfDocumentLoader {
    override suspend fun load(path: String): PdfDocument =
        withContext(ioDispatcher) {
            val file = File(path)
            require(file.exists()) { "Image file not found: $path" }
            require(file.canRead()) { "Image file is not readable: $path" }

            val source =
                try {
                    ImageIO.read(file)
                } catch (e: Exception) {
                    throw IllegalArgumentException("Failed to open image: $path", e)
                } ?: throw IllegalArgumentException("Unsupported or corrupt image: $path")

            val width = source.width
            val height = source.height
            val argb =
                if (source.type == BufferedImage.TYPE_INT_ARGB) {
                    source
                } else {
                    BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).also { converted ->
                        val g = converted.createGraphics()
                        g.drawImage(source, 0, 0, null)
                        g.dispose()
                    }
                }
            val pixels = argb.getRGB(0, 0, width, height, null, 0, width)

            val pageInfo =
                PdfPageInfo(
                    pageIndex = 0,
                    widthPt = width.toFloat(),
                    heightPt = height.toFloat(),
                    rotation = 0,
                )
            JvmImageDocument(
                widthPx = width,
                heightPx = height,
                pixels = pixels,
                info = PdfDocumentInfo(pageCount = 1, pages = listOf(pageInfo)),
            )
        }
}
