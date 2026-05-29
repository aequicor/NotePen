package ru.kyamshanov.notepen.pdf.infrastructure

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import ru.kyamshanov.notepen.annotation.domain.model.PageRotation
import ru.kyamshanov.notepen.pdf.domain.model.PdfDocument
import ru.kyamshanov.notepen.pdf.domain.model.PdfPageData
import ru.kyamshanov.notepen.pdf.domain.port.PdfPageRenderer
import java.awt.RenderingHints
import java.awt.image.BufferedImage

/**
 * JVM-реализация [PdfPageRenderer] поверх Apache PDFBox.
 *
 * Принимает только документы, открытые через [JvmPdfDocumentLoader].
 *
 * @param ioDispatcher диспетчер для блокирующего рендеринга; не должен быть Main-диспетчером
 */
class JvmPdfPageRenderer(
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
            val jvmDoc =
                requireNotNull(document as? JvmPdfDocument) {
                    "JvmPdfPageRenderer requires a document opened by JvmPdfDocumentLoader"
                }
            if (pageIndex < 0 || pageIndex >= document.info.pageCount) {
                throw IndexOutOfBoundsException(
                    "pageIndex $pageIndex out of [0, ${document.info.pageCount})",
                )
            }

            val pageInfo = document.info.pages[pageIndex]
            val userQuarters = PageRotation.normalizeQuarters(rotationQuarters)
            // Доля от ширины/высоты исходной (повёрнутой только собственным
            // /Rotate, ещё НЕ пользовательским) страницы, которую занимает вырезка.
            val cropW = (cropRightN - cropLeftN).coerceIn(0f, 1f).takeIf { it > 0f } ?: 1f
            val cropH = (cropBottomN - cropTopN).coerceIn(0f, 1f).takeIf { it > 0f } ?: 1f
            // PDFBox уже применяет собственный поворот PDF (pageInfo.rotation) при
            // рендере. Целевые widthPx/heightPx переданы уже с учётом полного
            // эффективного поворота (вызывающий меняет стороны для 90/270) И
            // вырезки. Чтобы PDF-растеризация шла в «нативной» (с учётом /Rotate)
            // ориентации, подбираем DPI по размеру ДО пользовательского доворота
            // (для четвертей 1/3 стороны поменяны обратно местами) и компенсируем
            // вырезку: рендерим целую страницу так, чтобы её часть [cropW × cropH]
            // дала нужные nativeTargetW/H.
            val nativeTargetW = if (userQuarters % 2 == 1) heightPx else widthPx
            val nativeTargetH = if (userQuarters % 2 == 1) widthPx else heightPx
            val fullTargetW = (nativeTargetW / cropW).toInt().coerceAtLeast(1)
            val fullTargetH = (nativeTargetH / cropH).toInt().coerceAtLeast(1)
            val dpi = calculateDpi(pageInfo.widthPt, pageInfo.heightPt, fullTargetW, fullTargetH, pageInfo.rotation)

            val rendered: BufferedImage =
                jvmDoc.useRenderer { renderer ->
                    renderer.renderImageWithDPI(pageIndex, dpi)
                }

            // Порядок CROP → ROTATE: вырезку определяем в собственной (до
            // пользовательского поворота) системе исходной страницы, поэтому
            // вырезаем СНАЧАЛА, затем доворачиваем половину. Так повёрнутый+
            // вырезанный растр совпадает со штрихами, которые хранятся в [0,1]
            // координатах ЛОГИЧЕСКОЙ половины и поворачиваются тем же
            // PageRotation.rotatePointCw.
            val cropped = cropToRect(rendered, cropLeftN, cropTopN, cropRightN, cropBottomN)
            val oriented = rotateCwQuarters(cropped, userQuarters)
            val pixels = scaleToTarget(oriented, widthPx, heightPx)
            PdfPageData(widthPx = widthPx, heightPx = heightPx, pixels = pixels)
        }

    override suspend fun documentTextLineHeight(document: PdfDocument): Float? =
        withContext(ioDispatcher) {
            val jvmDoc =
                requireNotNull(document as? JvmPdfDocument) {
                    "JvmPdfPageRenderer requires a document opened by JvmPdfDocumentLoader"
                }
            jvmDoc.useDocument { pdDoc ->
                val pagesToScan = minOf(TEXT_SCAN_PAGE_LIMIT, pdDoc.numberOfPages)
                if (pagesToScan <= 0) return@useDocument null

                val firstPage = pdDoc.getPage(0)
                val pageWidthPt = firstPage.mediaBox.width
                if (pageWidthPt <= 0f) return@useDocument null

                val heights = mutableListOf<Float>()
                val stripper =
                    object : PDFTextStripper() {
                        override fun writeString(
                            text: String,
                            textPositions: List<TextPosition>,
                        ) {
                            textPositions.forEach { pos ->
                                val h = pos.heightDir
                                if (h > 0f) heights += h
                            }
                        }
                    }
                stripper.startPage = 1
                stripper.endPage = pagesToScan
                // Output is discarded; we only collect glyph heights via writeString.
                stripper.getText(pdDoc)

                if (heights.isEmpty()) return@useDocument null
                val median = heights.sorted()[heights.size / 2]
                median / pageWidthPt
            }
        }

    /**
     * Вычисляет DPI рендеринга так, чтобы страница вписалась в [targetWidthPx] × [targetHeightPx].
     * PDFBox базовое разрешение = 72 DPI (1 пункт = 1 пиксель при 72 DPI).
     */
    private fun calculateDpi(
        widthPt: Float,
        heightPt: Float,
        targetWidthPx: Int,
        targetHeightPx: Int,
        rotation: Int,
    ): Float {
        val effectiveW = if (rotation == 90 || rotation == 270) heightPt else widthPt
        val effectiveH = if (rotation == 90 || rotation == 270) widthPt else heightPt
        val scaleX = targetWidthPx / effectiveW
        val scaleY = targetHeightPx / effectiveH
        return minOf(scaleX, scaleY).coerceAtLeast(0.1f) * 72f
    }

    /**
     * Вырезает из [source] нормированный прямоугольник `(leftN, topN, rightN,
     * bottomN)` (доли в собственной системе координат страницы, ось Y вниз).
     * Полная страница `(0,0,1,1)` возвращается без копии. Используется для
     * разделения разворота на левую/правую половины (FEATURE #4) ДО доворота.
     */
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
        // getSubimage делит буфер, поэтому делаем независимую копию.
        val sub = source.getSubimage(x, y, w, h)
        val copy = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = copy.createGraphics()
        g.drawImage(sub, 0, 0, null)
        g.dispose()
        return copy
    }

    /**
     * Поворачивает [source] на [quarters] четвертей (90°) по часовой стрелке.
     * `0` — возвращает источник без изменений. Для 1/3 ширина/высота меняются
     * местами. Направление совпадает с
     * [ru.kyamshanov.notepen.annotation.domain.model.PageRotation.rotatePointCw].
     */
    private fun rotateCwQuarters(
        source: BufferedImage,
        quarters: Int,
    ): BufferedImage {
        val q = PageRotation.normalizeQuarters(quarters)
        if (q == 0) return source
        val sw = source.width
        val sh = source.height
        val swap = q % 2 == 1
        val dw = if (swap) sh else sw
        val dh = if (swap) sw else sh
        val dst = BufferedImage(dw, dh, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until sh) {
            for (x in 0 until sw) {
                val rgb = source.getRGB(x, y)
                // CW-отображение пикселя: (x, y) -> повёрнутая позиция.
                val (nx, ny) =
                    when (q) {
                        1 -> (sh - 1 - y) to x // +90° CW
                        2 -> (sw - 1 - x) to (sh - 1 - y) // 180°
                        else -> y to (sw - 1 - x) // +270° CW
                    }
                dst.setRGB(nx, ny, rgb)
            }
        }
        return dst
    }

    private fun scaleToTarget(
        source: BufferedImage,
        widthPx: Int,
        heightPx: Int,
    ): IntArray {
        if (source.width == widthPx && source.height == heightPx) {
            return source.getRGB(0, 0, widthPx, heightPx, null, 0, widthPx)
        }
        val target = BufferedImage(widthPx, heightPx, BufferedImage.TYPE_INT_ARGB)
        val g = target.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.drawImage(source, 0, 0, widthPx, heightPx, null)
        g.dispose()
        return target.getRGB(0, 0, widthPx, heightPx, null, 0, widthPx)
    }

    private companion object {
        /** Сколько первых страниц сканировать ради репрезентативной высоты строки. */
        const val TEXT_SCAN_PAGE_LIMIT = 3
    }
}
