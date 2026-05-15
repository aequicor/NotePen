package ru.kyamshanov.notepen.pdf.domain.model

/**
 * Растровый результат рендеринга одной страницы PDF.
 *
 * Пиксели хранятся в формате ARGB (packed int). Порядок: строка за строкой,
 * слева направо, сверху вниз. Размер массива равен `widthPx * heightPx`.
 *
 * @param widthPx ширина растра в пикселях
 * @param heightPx высота растра в пикселях
 * @param pixels ARGB-пиксели
 */
data class PdfPageData(
    val widthPx: Int,
    val heightPx: Int,
    val pixels: IntArray,
) {
    // IntArray не реализует структурное equals/hashCode — переопределяем явно.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PdfPageData) return false
        return widthPx == other.widthPx &&
            heightPx == other.heightPx &&
            pixels.contentEquals(other.pixels)
    }

    override fun hashCode(): Int {
        var result = widthPx
        result = 31 * result + heightPx
        result = 31 * result + pixels.contentHashCode()
        return result
    }
}
