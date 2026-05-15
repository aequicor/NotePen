package ru.kyamshanov.notepen.pdf.infrastructure

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import ru.kyamshanov.notepen.pdf.domain.model.PdfDocument
import ru.kyamshanov.notepen.pdf.domain.model.PdfDocumentInfo

/**
 * JVM-реализация [PdfDocument] поверх Apache PDFBox.
 *
 * [renderer] — не thread-safe; все вызовы должны быть защищены через [synchronized] на этом объекте.
 */
internal class JvmPdfDocument(
    internal val renderer: PDFRenderer,
    private val document: PDDocument,
    override val info: PdfDocumentInfo,
) : PdfDocument {

    override fun close() = document.close()
}
