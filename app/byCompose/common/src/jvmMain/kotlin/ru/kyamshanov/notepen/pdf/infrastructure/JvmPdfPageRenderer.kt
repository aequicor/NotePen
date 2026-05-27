package ru.kyamshanov.notepen.pdf.infrastructure

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
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
            val dpi = calculateDpi(pageInfo.widthPt, pageInfo.heightPt, widthPx, heightPx, pageInfo.rotation)

            val rendered: BufferedImage =
                jvmDoc.useRenderer { renderer ->
                    renderer.renderImageWithDPI(pageIndex, dpi)
                }

            val pixels = scaleToTarget(rendered, widthPx, heightPx)
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
