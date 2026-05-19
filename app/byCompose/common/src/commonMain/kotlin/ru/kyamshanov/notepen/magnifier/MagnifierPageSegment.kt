package ru.kyamshanov.notepen.magnifier

import androidx.compose.ui.geometry.Rect

/**
 * Часть выделения лупы, относящаяся к одной странице PDF.
 *
 * Когда пользователь выделяет диагональ, пересекающую границу страниц,
 * результат — список таких сегментов: для каждой задетой страницы
 * хранится её page-normalized под-прямоугольник [targetOnPage] и
 * соответствующий диапазон по высоте окна лупы
 * [panelTopFrac]..[panelBottomFrac] (в долях `panelSize.height`).
 *
 * Сегменты идут в порядке сверху-вниз; `panelTopFrac` первого = 0,
 * `panelBottomFrac` последнего = 1, соседние смыкаются ровно. По
 * горизонтали все сегменты охватывают одинаковый x-диапазон
 * (выделение пользователя не сдвигается по X между страницами —
 * только wraps по Y).
 *
 * Для однополосного выделения список содержит один сегмент.
 */
data class MagnifierPageSegment(
    val pageIndex: Int,
    val targetOnPage: Rect,
    val panelTopFrac: Float,
    val panelBottomFrac: Float,
)
