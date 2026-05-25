package ru.kyamshanov.notepen.pdf.infrastructure

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import ru.kyamshanov.notepen.pdf.domain.model.PdfDocument
import ru.kyamshanov.notepen.pdf.domain.model.PdfPageData
import ru.kyamshanov.notepen.pdf.domain.port.PdfPageRenderer
import java.awt.RenderingHints
import java.awt.image.BufferedImage

/**
 * JVM-реализация [PdfPageRenderer] для документов-изображений.
 *
 * Принимает только документы, открытые через [JvmImageDocumentLoader].
 * Масштабирует хранимый ARGB-буфер под целевой размер.
 *
 * @param ioDispatcher диспетчер для блокирующего масштабирования; не должен быть Main-диспетчером
 */
class JvmImagePageRenderer(private val ioDispatcher: CoroutineDispatcher) : PdfPageRenderer {
    override suspend fun renderPage(
        document: PdfDocument,
        pageIndex: Int,
        widthPx: Int,
        heightPx: Int,
    ): PdfPageData =
        withContext(ioDispatcher) {
            val imageDoc =
                requireNotNull(document as? JvmImageDocument) {
                    "JvmImagePageRenderer requires a document opened by JvmImageDocumentLoader"
                }
            if (pageIndex != 0) {
                throw IndexOutOfBoundsException("pageIndex $pageIndex out of [0, 1)")
            }

            if (imageDoc.widthPx == widthPx && imageDoc.heightPx == heightPx) {
                return@withContext PdfPageData(
                    widthPx = widthPx,
                    heightPx = heightPx,
                    pixels = imageDoc.pixels.copyOf(),
                )
            }

            val source = BufferedImage(imageDoc.widthPx, imageDoc.heightPx, BufferedImage.TYPE_INT_ARGB)
            source.setRGB(0, 0, imageDoc.widthPx, imageDoc.heightPx, imageDoc.pixels, 0, imageDoc.widthPx)

            val target = BufferedImage(widthPx, heightPx, BufferedImage.TYPE_INT_ARGB)
            val g = target.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.drawImage(source, 0, 0, widthPx, heightPx, null)
            g.dispose()

            val pixels = target.getRGB(0, 0, widthPx, heightPx, null, 0, widthPx)
            PdfPageData(widthPx = widthPx, heightPx = heightPx, pixels = pixels)
        }

    /** Возвращает `null`: документы-изображения не несут текстовой геометрии. */
    override suspend fun documentTextLineHeight(document: PdfDocument): Float? = null
}
