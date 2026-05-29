package ru.kyamshanov.notepen.pdf.infrastructure

import android.graphics.Bitmap
import android.graphics.Matrix
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import ru.kyamshanov.notepen.annotation.domain.model.PageRotation
import ru.kyamshanov.notepen.pdf.domain.model.PdfDocument
import ru.kyamshanov.notepen.pdf.domain.model.PdfPageData
import ru.kyamshanov.notepen.pdf.domain.port.PdfPageRenderer

/**
 * Android-реализация [PdfPageRenderer] для документов-изображений.
 *
 * Принимает только документы, открытые через [AndroidImageDocumentLoader].
 * Масштабирует хранимый ARGB-буфер под целевой размер.
 *
 * @param ioDispatcher диспетчер для блокирующего масштабирования; не должен быть Main-диспетчером
 */
class AndroidImagePageRenderer(
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
                requireNotNull(document as? AndroidImageDocument) {
                    "AndroidImagePageRenderer requires a document opened by AndroidImageDocumentLoader"
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

            val source = Bitmap.createBitmap(imageDoc.widthPx, imageDoc.heightPx, Bitmap.Config.ARGB_8888)
            source.setPixels(imageDoc.pixels, 0, imageDoc.widthPx, 0, 0, imageDoc.widthPx, imageDoc.heightPx)
            // CROP → ROTATE: вырезка в собственной системе координат страницы.
            val cropped = cropBitmap(source, cropLeftN, cropTopN, cropRightN, cropBottomN)
            val oriented =
                if (q == 0) {
                    cropped
                } else {
                    val matrix = Matrix().apply { postRotate(q * DEGREES_PER_QUARTER.toFloat()) }
                    Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, matrix, true)
                        .also { if (it !== cropped) cropped.recycle() }
                }
            val scaled = Bitmap.createScaledBitmap(oriented, widthPx, heightPx, true)
            if (scaled !== oriented) oriented.recycle()

            val pixels = IntArray(widthPx * heightPx)
            scaled.getPixels(pixels, 0, widthPx, 0, 0, widthPx, heightPx)
            scaled.recycle()

            PdfPageData(widthPx = widthPx, heightPx = heightPx, pixels = pixels)
        }

    private fun cropBitmap(
        source: Bitmap,
        leftN: Float,
        topN: Float,
        rightN: Float,
        bottomN: Float,
    ): Bitmap {
        val l = leftN.coerceIn(0f, 1f)
        val t = topN.coerceIn(0f, 1f)
        val r = rightN.coerceIn(l, 1f)
        val b = bottomN.coerceIn(t, 1f)
        if (isFullCrop(l, t, r, b)) return source
        val x = (l * source.width).toInt().coerceIn(0, source.width - 1)
        val y = (t * source.height).toInt().coerceIn(0, source.height - 1)
        val w = ((r - l) * source.width).toInt().coerceIn(1, source.width - x)
        val h = ((b - t) * source.height).toInt().coerceIn(1, source.height - y)
        return Bitmap.createBitmap(source, x, y, w, h).also { if (it !== source) source.recycle() }
    }

    /** Возвращает `null`: документы-изображения не несут текстовой геометрии. */
    override suspend fun documentTextLineHeight(document: PdfDocument): Float? = null

    private companion object {
        const val DEGREES_PER_QUARTER = 90
    }
}
