package ru.kyamshanov.notepen.pdf.infrastructure

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import ru.kyamshanov.notepen.annotation.domain.model.PageRotation
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
        rotationQuarters: Int,
        cropLeftN: Float,
        cropTopN: Float,
        cropRightN: Float,
        cropBottomN: Float,
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

            // PdfRenderer уже учитывает собственный поворот PDF (/Rotate) при
            // рендере, а loader сообщает уже повёрнутые размеры — поэтому
            // применяем только пользовательский поворот. Целевые widthPx/heightPx
            // переданы с учётом полного эффективного поворота И вырезки, поэтому
            // растеризуем целую страницу в «нативном» размере (стороны обратно
            // местами для четвертей 1/3, увеличенные так, чтобы вырезка [cropW ×
            // cropH] дала нужный размер), затем CROP → ROTATE матрицей.
            val q = PageRotation.normalizeQuarters(rotationQuarters)
            val cropW = (cropRightN - cropLeftN).coerceIn(0f, 1f).takeIf { it > 0f } ?: 1f
            val cropH = (cropBottomN - cropTopN).coerceIn(0f, 1f).takeIf { it > 0f } ?: 1f
            val croppedW = if (q % 2 == 1) heightPx else widthPx
            val croppedH = if (q % 2 == 1) widthPx else heightPx
            val renderW = (croppedW / cropW).toInt().coerceAtLeast(1)
            val renderH = (croppedH / cropH).toInt().coerceAtLeast(1)

            val bitmap = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888)
            // Process-global pdfium lock, not per-renderer: concurrent open/render/close
            // across different PdfRenderer instances corrupts pdfium's shared, non-thread-safe
            // FreeType font module → native crash in FT_Done_Face. See PdfiumRenderLock.
            synchronized(PdfiumRenderLock.lock) {
                val page: PdfRenderer.Page = androidDoc.renderer.openPage(pageIndex)
                try {
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                } finally {
                    page.close()
                }
            }

            // CROP в собственной системе координат страницы (до доворота), затем
            // ROTATE на пользовательский угол, затем — приведение к целевому
            // размеру (компенсация округления). См. PageCropRect / PageRotation.
            val cropped = cropBitmap(bitmap, cropLeftN, cropTopN, cropRightN, cropBottomN)
            val oriented = rotateCw(cropped, q)
            val sized = sizeTo(oriented, widthPx, heightPx)
            val pixels = IntArray(widthPx * heightPx)
            sized.getPixels(pixels, 0, widthPx, 0, 0, widthPx, heightPx)
            sized.recycle()

            PdfPageData(widthPx = widthPx, heightPx = heightPx, pixels = pixels)
        }

    /**
     * Поворачивает [source] на [quarters] четвертей CW. `0` — без копии.
     * Matrix.postRotate(градусы) крутит по часовой стрелке в системе с осью Y
     * вниз — совпадает с [PageRotation.rotatePointCw].
     */
    private fun rotateCw(
        source: Bitmap,
        quarters: Int,
    ): Bitmap {
        if (quarters == 0) return source
        val matrix = Matrix().apply { postRotate(quarters * DEGREES_PER_QUARTER.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
            .also { if (it !== source) source.recycle() }
    }

    /** Приводит [source] к размеру [widthPx] × [heightPx]; при совпадении — без копии. */
    private fun sizeTo(
        source: Bitmap,
        widthPx: Int,
        heightPx: Int,
    ): Bitmap =
        if (source.width == widthPx && source.height == heightPx) {
            source
        } else {
            Bitmap.createScaledBitmap(source, widthPx, heightPx, true)
                .also { if (it !== source) source.recycle() }
        }

    /**
     * Вырезает из [source] нормированный прямоугольник (доли, ось Y вниз) в
     * собственной системе координат страницы. Полная страница возвращается без
     * копии. Используется для разделения разворота на половины (FEATURE #4).
     */
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

    /**
     * Возвращает `null`: [android.graphics.pdf.PdfRenderer] не предоставляет
     * доступа к текстовой геометрии страницы.
     */
    override suspend fun documentTextLineHeight(document: PdfDocument): Float? = null

    private companion object {
        const val DEGREES_PER_QUARTER = 90
    }
}
