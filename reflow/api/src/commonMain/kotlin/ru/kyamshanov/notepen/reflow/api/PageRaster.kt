package ru.kyamshanov.notepen.reflow.api

/**
 * Растеризованная страница PDF под Lattice-анализ. Лёгкий KMP-data-class с
 * ARGB-`IntArray` пикселями + размером — соответствует структуре
 * [ru.kyamshanov.notepen.rendering.api.PageBitmap], но определён здесь, чтобы не
 * тащить dep `:reflow:api` → `:rendering:api` ради одного типа. Caller (EditorPanel
 * или подобный) адаптирует свою растеризацию в эту структуру одной строкой.
 *
 * Альфа-канал в пикселях игнорируется на стороне Lattice-морфологии (PDF-страницы
 * непрозрачные).
 *
 * @property pixels ARGB-пиксели, row-major, длина = [widthPx] × [heightPx]
 * @property widthPx ширина буфера в пикселях
 * @property heightPx высота буфера в пикселях
 */
public data class PageRaster(
    public val pixels: IntArray,
    public val widthPx: Int,
    public val heightPx: Int,
) {
    init {
        require(widthPx >= 0 && heightPx >= 0) { "dimensions must be non-negative" }
        require(pixels.size == widthPx * heightPx) {
            "pixels.size=${pixels.size} must equal widthPx*heightPx=${widthPx * heightPx}"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PageRaster) return false
        return widthPx == other.widthPx && heightPx == other.heightPx && pixels.contentEquals(other.pixels)
    }

    override fun hashCode(): Int {
        var result = widthPx
        result = 31 * result + heightPx
        result = 31 * result + pixels.contentHashCode()
        return result
    }
}

/**
 * Колбэк растеризации страницы PDF под Lattice-анализ. Вызывается рефайнером
 * лениво — только для страниц, на которых есть low-confidence Stream-table
 * кандидаты (см. `ReflowBlock.Figure.wasTableFallback`). Возвращает `null`,
 * если растеризация невозможна — в таком случае Lattice-анализ для этой
 * страницы пропускается, low-conf Figure остаётся Figure-crop'ом.
 *
 * `targetWidthPx` — целевая ширина растра; высота вычисляется реализацией под
 * aspect ratio страницы. Типично 1200 px (см.
 * `LatticeTableRefiner.DEFAULT_TARGET_WIDTH_PX`).
 */
public typealias PageBitmapProvider = suspend (pageIndex: Int, targetWidthPx: Int) -> PageRaster?
