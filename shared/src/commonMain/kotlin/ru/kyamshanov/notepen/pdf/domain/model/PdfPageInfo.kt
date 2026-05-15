package ru.kyamshanov.notepen.pdf.domain.model

/**
 * Метрики одной страницы PDF-документа в пространстве документа (points, 1 pt = 1/72 дюйма).
 *
 * @param pageIndex нулевой индекс страницы в документе
 * @param widthPt ширина страницы в пунктах
 * @param heightPt высота страницы в пунктах
 * @param rotation угол поворота из PDF-словаря (0, 90, 180, 270)
 */
data class PdfPageInfo(
    val pageIndex: Int,
    val widthPt: Float,
    val heightPt: Float,
    val rotation: Int = 0,
) {
    /**
     * Соотношение сторон с учётом поворота страницы (ширина / высота в экранном пространстве).
     */
    val aspectRatio: Float
        get() = if (rotation == 90 || rotation == 270) heightPt / widthPt else widthPt / heightPt
}
