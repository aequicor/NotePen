package ru.kyamshanov.notepen.magnifier

import androidx.compose.ui.geometry.Rect

/**
 * Часть выделения лупы, относящаяся к одной странице PDF.
 *
 * Когда пользователь выделяет диагональ, пересекающую границу страниц,
 * результат — список таких сегментов: для каждой задетой страницы
 * хранится её page-normalized под-прямоугольник [targetOnPage] и
 * соответствующий диапазон окна лупы:
 * [panelLeftFrac]..[panelRightFrac] по ширине и
 * [panelTopFrac]..[panelBottomFrac] по высоте.
 *
 * Сегменты идут в порядке сверху-вниз / слева-направо; соседние смыкаются
 * в координатах панели. Для вертикального перехода между страницами
 * `panelLeftFrac..panelRightFrac` обычно равно `0..1`. Для книжного разворота
 * или split-разворота соседние логические страницы могут делить один Y-ряд,
 * и тогда именно X-диапазон определяет, в какую страницу попадёт ввод.
 *
 * Для однополосного выделения список содержит один сегмент.
 */
data class MagnifierPageSegment(
    val pageIndex: Int,
    val targetOnPage: Rect,
    val panelLeftFrac: Float = 0f,
    val panelRightFrac: Float = 1f,
    val panelTopFrac: Float,
    val panelBottomFrac: Float,
)
