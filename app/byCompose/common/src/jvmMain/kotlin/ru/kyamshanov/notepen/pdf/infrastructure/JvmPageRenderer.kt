package ru.kyamshanov.notepen.pdf.infrastructure

import ru.kyamshanov.notepen.pdf.domain.model.ImageBackedDocument
import ru.kyamshanov.notepen.pdf.domain.model.PdfDocument
import ru.kyamshanov.notepen.pdf.domain.model.PdfPageData
import ru.kyamshanov.notepen.pdf.domain.port.PdfPageRenderer

/**
 * Композитный [PdfPageRenderer] для desktop: по типу документа делегирует рендеринг
 * либо [JvmImagePageRenderer] (для [ImageBackedDocument]), либо [JvmPdfPageRenderer] (PDF).
 */
class JvmPageRenderer(
    private val pdfRenderer: PdfPageRenderer,
    private val imageRenderer: PdfPageRenderer,
) : PdfPageRenderer {

    override suspend fun renderPage(
        document: PdfDocument,
        pageIndex: Int,
        widthPx: Int,
        heightPx: Int,
    ): PdfPageData {
        val renderer = if (document is ImageBackedDocument) imageRenderer else pdfRenderer
        return renderer.renderPage(document, pageIndex, widthPx, heightPx)
    }

    override suspend fun documentTextLineHeight(document: PdfDocument): Float? {
        val renderer = if (document is ImageBackedDocument) imageRenderer else pdfRenderer
        return renderer.documentTextLineHeight(document)
    }
}
