package ru.kyamshanov.notepen.pdf.domain.port

import ru.kyamshanov.notepen.pdf.domain.model.PdfDocument
import ru.kyamshanov.notepen.pdf.domain.model.PdfPageData

/**
 * Порт для растеризации страницы PDF в пиксельный буфер.
 *
 * Реализации обязаны быть main-safe: блокирующее CPU/IO выполняется
 * через инжектируемый [kotlinx.coroutines.CoroutineDispatcher].
 */
interface PdfPageRenderer {

    /**
     * Растеризует страницу [pageIndex] из [document] в буфер заданного размера.
     *
     * @param document открытый документ, возвращённый [PdfDocumentLoader]
     * @param pageIndex нулевой индекс страницы; должен быть в диапазоне `[0, pageCount)`
     * @param widthPx желаемая ширина результата в пикселях (> 0)
     * @param heightPx желаемая высота результата в пикселях (> 0)
     * @return пиксельный буфер страницы в формате ARGB
     * @throws IndexOutOfBoundsException если [pageIndex] вне диапазона
     * @throws IllegalStateException если [document] уже закрыт
     */
    suspend fun renderPage(
        document: PdfDocument,
        pageIndex: Int,
        widthPx: Int,
        heightPx: Int,
    ): PdfPageData
}
