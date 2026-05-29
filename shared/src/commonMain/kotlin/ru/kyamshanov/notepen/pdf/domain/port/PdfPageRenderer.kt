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
     * @param pageIndex нулевой индекс **исходной** страницы; должен быть в
     *   диапазоне `[0, pageCount)`. При разделении разворотов (FEATURE #4) одна
     *   исходная страница даёт две логические половины — вызывающий резолвит
     *   логический индекс в (исходный индекс + вырезка) и передаёт сюда уже
     *   исходный индекс плюс [cropLeftN]..[cropBottomN].
     * @param widthPx желаемая ширина результата в пикселях (> 0); это размер уже
     *   с учётом эффективного поворота (вызывающий меняет ширину/высоту местами
     *   для четвертей 1/3) И вырезки (для половины разворота ширина вдвое меньше)
     * @param heightPx желаемая высота результата в пикселях (> 0)
     * @param rotationQuarters пользовательский поворот страницы в четвертях
     *   оборота `[0, 3]` по часовой стрелке, **поверх** собственного поворота PDF
     *   (`PdfPageInfo.rotation`). `0` — без доворота. Реализация обязана повернуть
     *   растр на этот угол так, чтобы он совпал с поворотом штрихов
     *   (`PageRotation.rotatePointCw` — +90° CW).
     * @param cropLeftN левый край вырезки, доля ширины ИСХОДНОЙ страницы в её
     *   собственной (до пользовательского поворота) системе координат, ось Y вниз
     * @param cropTopN верхний край вырезки (доля высоты исходной страницы)
     * @param cropRightN правый край вырезки
     * @param cropBottomN нижний край вырезки
     * @return пиксельный буфер страницы в формате ARGB
     * @throws IndexOutOfBoundsException если [pageIndex] вне диапазона
     * @throws IllegalStateException если [document] уже закрыт
     */
    suspend fun renderPage(
        document: PdfDocument,
        pageIndex: Int,
        widthPx: Int,
        heightPx: Int,
        rotationQuarters: Int = 0,
        cropLeftN: Float = 0f,
        cropTopN: Float = 0f,
        cropRightN: Float = 1f,
        cropBottomN: Float = 1f,
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
