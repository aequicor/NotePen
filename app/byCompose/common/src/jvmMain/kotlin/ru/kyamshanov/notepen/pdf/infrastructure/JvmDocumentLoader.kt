package ru.kyamshanov.notepen.pdf.infrastructure

import ru.kyamshanov.notepen.pdf.domain.model.PdfDocument
import ru.kyamshanov.notepen.pdf.domain.port.PdfDocumentLoader

/**
 * Композитный [PdfDocumentLoader] для desktop: по расширению пути делегирует
 * загрузку либо [JvmImageDocumentLoader] (PNG/JPEG), либо [JvmPdfDocumentLoader] (PDF).
 */
class JvmDocumentLoader(
    private val pdfLoader: PdfDocumentLoader,
    private val imageLoader: PdfDocumentLoader,
) : PdfDocumentLoader {

    override suspend fun load(path: String): PdfDocument =
        if (isSupportedImagePath(path)) imageLoader.load(path) else pdfLoader.load(path)
}
