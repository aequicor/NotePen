package ru.kyamshanov.notepen.pdf.domain.fakes

import ru.kyamshanov.notepen.pdf.domain.model.PdfDocument
import ru.kyamshanov.notepen.pdf.domain.model.PdfDocumentInfo

internal class FakePdfDocument(override val info: PdfDocumentInfo) : PdfDocument {
    var closed = false
        private set

    override fun close() {
        closed = true
    }
}
