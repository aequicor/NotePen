package ru.kyamshanov.notepen.annotation.domain.model

/**
 * Прямоугольник рисуемой области страницы в координатах самой PDF-страницы.
 *
 * PDF-битмап всегда занимает прямоугольник `[0..1] × [0..1]` в этой системе.
 * Инварианты:
 *
 * ```
 * left   ≤ 0f
 * top    ≤ 0f
 * right  ≥ 1f
 * bottom ≥ 1f
 * ```
 *
 * Координаты штрихов ([DrawingPoint.x], [DrawingPoint.y]) нормализованы
 * относительно PDF-страницы и всегда лежат внутри текущего extent. Рисование
 * за пределами extent запрещено — точки клампируются к границам extent.
 * Расширить extent можно только явно, кнопками «+» ([expandedLeft]/[expandedRight]).
 */
data class PageExtent(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
) {
    /** Ширина в долях ширины PDF-страницы. Всегда ≥ 1. */
    val width: Float get() = right - left

    /** Высота в долях высоты PDF-страницы. Всегда ≥ 1. */
    val height: Float get() = bottom - top

    /** Расширяет левую границу на [fraction] от текущей ширины. */
    fun expandedLeft(fraction: Float): PageExtent = copy(left = left - width * fraction)

    /** Расширяет правую границу на [fraction] от текущей ширины. */
    fun expandedRight(fraction: Float): PageExtent = copy(right = right + width * fraction)

    /** Объединение с [other] — берёт минимумы/максимумы по каждой границе. */
    fun union(other: PageExtent): PageExtent {
        val l = minOf(left, other.left)
        val t = minOf(top, other.top)
        val r = maxOf(right, other.right)
        val b = maxOf(bottom, other.bottom)
        return if (l == left && t == top && r == right && b == bottom) {
            this
        } else {
            PageExtent(l, t, r, b)
        }
    }

    companion object {
        /** Extent, равный самой PDF-странице (без дополнительной рисуемой зоны). */
        val Pdf: PageExtent = PageExtent()
    }
}
