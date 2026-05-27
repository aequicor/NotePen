package ru.kyamshanov.notepen.pdf.infrastructure

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import ru.kyamshanov.notepen.pdf.domain.model.PdfDocument
import ru.kyamshanov.notepen.pdf.domain.model.PdfPageData
import ru.kyamshanov.notepen.pdf.domain.port.PdfPageRenderer

/**
 * Android-реализация [PdfPageRenderer] поверх [android.graphics.pdf.PdfRenderer].
 *
 * Принимает только документы, открытые через [AndroidPdfDocumentLoader].
 *
 * @param ioDispatcher диспетчер для блокирующего рендеринга; не должен быть Main-диспетчером
 */
class AndroidPdfPageRenderer(
    private val ioDispatcher: CoroutineDispatcher,
) : PdfPageRenderer {
    override suspend fun renderPage(
        document: PdfDocument,
        pageIndex: Int,
        widthPx: Int,
        heightPx: Int,
    ): PdfPageData =
        withContext(ioDispatcher) {
            val androidDoc =
                requireNotNull(document as? AndroidPdfDocument) {
                    "AndroidPdfPageRenderer requires a document opened by AndroidPdfDocumentLoader"
                }
            if (pageIndex < 0 || pageIndex >= document.info.pageCount) {
                throw IndexOutOfBoundsException(
                    "pageIndex $pageIndex out of [0, ${document.info.pageCount})",
                )
            }

            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            synchronized(androidDoc.renderer) {
                val page: PdfRenderer.Page = androidDoc.renderer.openPage(pageIndex)
                try {
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                } finally {
                    page.close()
                }
            }

            val pixels = IntArray(widthPx * heightPx)
            bitmap.getPixels(pixels, 0, widthPx, 0, 0, widthPx, heightPx)
            bitmap.recycle()

            PdfPageData(widthPx = widthPx, heightPx = heightPx, pixels = pixels)
        }

    /**
     * Возвращает `null`: [android.graphics.pdf.PdfRenderer] не предоставляет
     * доступа к текстовой геометрии страницы.
     */
    override suspend fun documentTextLineHeight(document: PdfDocument): Float? = null
}
