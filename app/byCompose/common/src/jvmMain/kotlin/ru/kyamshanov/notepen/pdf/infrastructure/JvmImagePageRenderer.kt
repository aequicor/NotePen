package ru.kyamshanov.notepen.pdf.infrastructure

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import ru.kyamshanov.notepen.annotation.domain.model.PageRotation
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
class JvmImagePageRenderer(
    private val ioDispatcher: CoroutineDispatcher,
) : PdfPageRenderer {
    override suspend fun renderPage(
        document: PdfDocument,
        pageIndex: Int,
        widthPx: Int,
        heightPx: Int,
        rotationQuarters: Int,
        cropLeftN: Float,
        cropTopN: Float,
        cropRightN: Float,
        cropBottomN: Float,
    ): PdfPageData =
        withContext(ioDispatcher) {
            val imageDoc =
                requireNotNull(document as? JvmImageDocument) {
                    "JvmImagePageRenderer requires a document opened by JvmImageDocumentLoader"
                }
            if (pageIndex != 0) {
                throw IndexOutOfBoundsException("pageIndex $pageIndex out of [0, 1)")
            }

            val q = PageRotation.normalizeQuarters(rotationQuarters)
            val fullCrop = isFullCrop(cropLeftN, cropTopN, cropRightN, cropBottomN)
            val sameSize = imageDoc.widthPx == widthPx && imageDoc.heightPx == heightPx
            if (q == 0 && fullCrop && sameSize) {
                return@withContext PdfPageData(
                    widthPx = widthPx,
                    heightPx = heightPx,
                    pixels = imageDoc.pixels.copyOf(),
                )
            }

            val source = BufferedImage(imageDoc.widthPx, imageDoc.heightPx, BufferedImage.TYPE_INT_ARGB)
            source.setRGB(0, 0, imageDoc.widthPx, imageDoc.heightPx, imageDoc.pixels, 0, imageDoc.widthPx)
            // CROP → ROTATE: вырезка в собственной системе координат страницы, см.
            // JvmPdfPageRenderer / PageCropRect.
            val cropped = cropToRect(source, cropLeftN, cropTopN, cropRightN, cropBottomN)
            val oriented = rotateCwQuarters(cropped, q)

            val target = BufferedImage(widthPx, heightPx, BufferedImage.TYPE_INT_ARGB)
            val g = target.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.drawImage(oriented, 0, 0, widthPx, heightPx, null)
            g.dispose()

            val pixels = target.getRGB(0, 0, widthPx, heightPx, null, 0, widthPx)
            PdfPageData(widthPx = widthPx, heightPx = heightPx, pixels = pixels)
        }

    private fun cropToRect(
        source: BufferedImage,
        leftN: Float,
        topN: Float,
        rightN: Float,
        bottomN: Float,
    ): BufferedImage {
        val l = leftN.coerceIn(0f, 1f)
        val t = topN.coerceIn(0f, 1f)
        val r = rightN.coerceIn(l, 1f)
        val b = bottomN.coerceIn(t, 1f)
        if (isFullCrop(l, t, r, b)) return source
        val x = (l * source.width).toInt().coerceIn(0, source.width - 1)
        val y = (t * source.height).toInt().coerceIn(0, source.height - 1)
        val w = ((r - l) * source.width).toInt().coerceIn(1, source.width - x)
        val h = ((b - t) * source.height).toInt().coerceIn(1, source.height - y)
        val sub = source.getSubimage(x, y, w, h)
        val copy = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = copy.createGraphics()
        g.drawImage(sub, 0, 0, null)
        g.dispose()
        return copy
    }

    private fun rotateCwQuarters(
        source: BufferedImage,
        quarters: Int,
    ): BufferedImage {
        val q = PageRotation.normalizeQuarters(quarters)
        if (q == 0) return source
        val sw = source.width
        val sh = source.height
        val swap = q % 2 == 1
        val dst = BufferedImage(if (swap) sh else sw, if (swap) sw else sh, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until sh) {
            for (x in 0 until sw) {
                val rgb = source.getRGB(x, y)
                val (nx, ny) =
                    when (q) {
                        1 -> (sh - 1 - y) to x
                        2 -> (sw - 1 - x) to (sh - 1 - y)
                        else -> y to (sw - 1 - x)
                    }
                dst.setRGB(nx, ny, rgb)
            }
        }
        return dst
    }

    /** Возвращает `null`: документы-изображения не несут текстовой геометрии. */
    override suspend fun documentTextLineHeight(document: PdfDocument): Float? = null
}
