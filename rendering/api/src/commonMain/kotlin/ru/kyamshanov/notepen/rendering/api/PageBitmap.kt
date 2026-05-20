package ru.kyamshanov.notepen.rendering.api

/**
 * Растеризованная страница PDF с метаданными масштаба.
 *
 * Обёртка над пиксельным буфером, дополненная процентом масштаба,
 * при котором страница была отрендерена.
 *
 * @property widthPx ширина буфера в пикселях
 * @property heightPx высота буфера в пикселях
 * @property scalePercent процент масштаба вьюера на момент растеризации (100 = 1:1)
 * @property pixels ARGB-пиксели, длина = [widthPx] × [heightPx]
 */
public data class PageBitmap(
    val widthPx: Int,
    val heightPx: Int,
    val scalePercent: Int,
    val pixels: IntArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PageBitmap) return false
        return widthPx == other.widthPx &&
            heightPx == other.heightPx &&
            scalePercent == other.scalePercent &&
            pixels.contentEquals(other.pixels)
    }

    override fun hashCode(): Int {
        var result = widthPx
        result = 31 * result + heightPx
        result = 31 * result + scalePercent
        result = 31 * result + pixels.contentHashCode()
        return result
    }
}
