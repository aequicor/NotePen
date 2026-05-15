package ru.kyamshanov.notepen.pdf.domain.model

/**
 * Неизменяемые метаданные PDF-документа, доступные без рендеринга страниц.
 *
 * @param pageCount общее число страниц
 * @param pages метрики каждой страницы, индексированные от нуля
 */
data class PdfDocumentInfo(
    val pageCount: Int,
    val pages: List<PdfPageInfo>,
)
