package ru.kyamshanov.notepen.annotation.domain.model

/**
 * Прямоугольник рисуемой области страницы в координатах самой PDF-страницы.
 *
 * PDF-битмап всегда занимает прямоугольник `[0..1] × [0..1]` в этой системе.
 * Extent монотонно растёт во все стороны по мере того, как пользователь рисует
 * за пределами PDF. Инварианты:
 *
 * ```
 * left   ≤ 0f
 * top    ≤ 0f
 * right  ≥ 1f
 * bottom ≥ 1f
 * ```
 *
 * Координаты штрихов ([DrawingPoint.x], [DrawingPoint.y]) остаются нормализованы
 * относительно PDF-страницы и могут выходить за `[0..1]` — но всегда лежат
 * внутри текущего extent.
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

    /**
     * Расширить extent так, чтобы он включал точку `(x, y)` с запасом [pad]
     * (в долях ширины PDF). Возвращает текущий объект, если точка уже
     * внутри extent.
     *
     * Pad добавляется только с тех сторон, с которых точка **реально вышла**
     * за границу — touch у самого края PDF (`x == 0`) не должен раздувать
     * extent во все стороны: иначе каждое первое касание любой страницы
     * триггерило бы relayout вьюера.
     */
    fun including(x: Float, y: Float, pad: Float = 0f): PageExtent {
        val l = if (x < left) x - pad else left
        val t = if (y < top) y - pad else top
        val r = if (x > right) x + pad else right
        val b = if (y > bottom) y + pad else bottom
        return if (l == left && t == top && r == right && b == bottom) {
            this
        } else {
            PageExtent(l, t, r, b)
        }
    }

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
