package ru.kyamshanov.notepen.reflow.api

/**
 * Прямоугольная область в координатах PDF-страницы.
 *
 * Единицы — пункты PDF (1 пункт = 1/72 дюйма). Начало координат — верхний левый
 * угол страницы, ось Y направлена вниз. Используется для ссылки на нетекстовые
 * области, которые рендерятся как изображение ([ReflowBlock.Figure]).
 *
 * @property left левая граница (пункты)
 * @property top верхняя граница (пункты)
 * @property right правая граница (пункты)
 * @property bottom нижняя граница (пункты)
 */
public data class ReflowRect(
    public val left: Float,
    public val top: Float,
    public val right: Float,
    public val bottom: Float,
) {
    /** Ширина области в пунктах. */
    public val width: Float get() = right - left

    /** Высота области в пунктах. */
    public val height: Float get() = bottom - top
}
