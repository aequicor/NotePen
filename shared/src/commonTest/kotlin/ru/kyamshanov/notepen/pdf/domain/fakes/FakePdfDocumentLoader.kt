package ru.kyamshanov.notepen.pdf.domain.fakes

import ru.kyamshanov.notepen.pdf.domain.model.PdfDocument
import ru.kyamshanov.notepen.pdf.domain.model.PdfDocumentInfo
import ru.kyamshanov.notepen.pdf.domain.port.PdfDocumentLoader

internal class FakePdfDocumentLoader(private val info: PdfDocumentInfo) : PdfDocumentLoader {
    var lastLoadedPath: String? = null
        private set

    override suspend fun load(path: String): PdfDocument {
        lastLoadedPath = path
        return FakePdfDocument(info)
    }
}
