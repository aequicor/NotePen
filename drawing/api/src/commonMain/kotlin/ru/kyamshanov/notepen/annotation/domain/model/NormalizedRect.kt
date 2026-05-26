package ru.kyamshanov.notepen.annotation.domain.model

import kotlinx.serialization.Serializable

/**
 * Прямоугольная область в нормализованных координатах страницы `[0..1]` — той же
 * системе, что и точки штрихов ([DrawingPoint]): 0 — левый/верхний край страницы,
 * 1 — правый/нижний, ось Y направлена вниз. Значения могут слегка выходить за
 * `[0..1]` у самого края страницы.
 *
 * @property left левая граница (доля ширины страницы)
 * @property top верхняя граница (доля высоты страницы)
 * @property right правая граница (доля ширины страницы)
 * @property bottom нижняя граница (доля высоты страницы)
 */
@Serializable
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)
