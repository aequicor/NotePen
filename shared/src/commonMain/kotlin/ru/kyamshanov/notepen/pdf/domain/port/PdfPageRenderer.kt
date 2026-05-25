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

    /**
     * Возвращает репрезентативную высоту строки текста [document], нормированную
     * к ширине страницы (доля от ширины — в тех же единицах, что и
     * `MarkerSettings.strokeWidth` / `DrawingPath.strokeWidth`).
     *
     * Используется для подбора стартовой толщины маркера под высоту текста.
     *
     * @param document открытый документ, возвращённый [PdfDocumentLoader]
     * @return доля от ширины страницы, либо `null`, если текстовая геометрия
     *   недоступна на платформе или в документе нет текста
     */
    suspend fun documentTextLineHeight(document: PdfDocument): Float?
}
