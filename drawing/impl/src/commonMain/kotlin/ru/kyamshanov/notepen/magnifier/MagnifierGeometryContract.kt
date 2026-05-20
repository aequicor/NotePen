package ru.kyamshanov.notepen.magnifier

import androidx.compose.ui.geometry.Rect

/**
 * Геометрия окна лупы, нужная [MagnifierInputController] для перевода
 * panel-local координат в page-normalized и для авто-прокрутки рамки.
 *
 * Абстрагирует `MagnifierState` из модуля рендеринга, чтобы контроллер не
 * зависел от рендеринга. Реализация — адаптер поверх `MagnifierState` в
 * `:rendering:impl`. Значения читаются «вживую» на момент вызова.
 */
interface MagnifierGeometry {

    /** Ширина PDF-области страницы в пикселях viewport'а. */
    val pageCanvasWidthPx: Float

    /** Текущие сегменты выделения лупы (сверху-вниз). */
    val segments: List<MagnifierPageSegment>

    /** Включена ли авто-прокрутка рамки после отрыва пера. */
    val autoScrollEnabled: Boolean

    /** Устанавливает однополосную рамку выделения для страницы [pageIndex]. */
    fun setSingleSegmentTarget(pageIndex: Int, targetOnPage: Rect)
}
