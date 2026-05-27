package ru.kyamshanov.notepen.pdf.infrastructure

import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
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
    ): PdfPageData =
        withContext(ioDispatcher) {
            val imageDoc =
                requireNotNull(document as? AndroidImageDocument) {
                    "AndroidImagePageRenderer requires a document opened by AndroidImageDocumentLoader"
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

            val source = Bitmap.createBitmap(imageDoc.widthPx, imageDoc.heightPx, Bitmap.Config.ARGB_8888)
            source.setPixels(imageDoc.pixels, 0, imageDoc.widthPx, 0, 0, imageDoc.widthPx, imageDoc.heightPx)
            val scaled = Bitmap.createScaledBitmap(source, widthPx, heightPx, true)
            source.recycle()

            val pixels = IntArray(widthPx * heightPx)
            scaled.getPixels(pixels, 0, widthPx, 0, 0, widthPx, heightPx)
            scaled.recycle()

            PdfPageData(widthPx = widthPx, heightPx = heightPx, pixels = pixels)
        }

    /** Возвращает `null`: документы-изображения не несут текстовой геометрии. */
    override suspend fun documentTextLineHeight(document: PdfDocument): Float? = null
}
