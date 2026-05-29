package ru.kyamshanov.notepen.reflow.lattice

/**
 * Чистые KMP-примитивы для Lattice-детектора таблиц: пороговая бинаризация и
 * run-length поиск длинных тёмных полос (кандидатов в линии грид-сетки).
 *
 * Работает напрямую с ARGB `IntArray` (как [ru.kyamshanov.notepen.rendering.api.PageBitmap.pixels])
 * — без bitmap-абстракции и `expect/actual`. Память: бинарная маска = N booleans
 * (1 байт каждый в JVM), runs хранятся как List, при 1200×1600 ≈ 2 МБ маски.
 *
 * Алгоритм опирается на простой row-by-row run-length анализ: морфологическая
 * эрозия с ядром N не нужна, потому что нам всё равно требуется явный extent
 * каждой линии, и run-length даёт его за один проход.
 */
internal object Morphology {
    /**
     * Порог яркости по min(R,G,B): пиксели ниже считаются «тёмными» (часть линии
     * или текста). Min-channel надёжен для PDF-линий, которые обычно чёрные или
     * однотонные; усреднённая luma уязвимее к anti-aliasing на грани цветов.
     */
    const val DEFAULT_LUMINANCE_THRESHOLD: Int = 180

    /**
     * Бинарная маска «тёмный пиксель» из ARGB-`IntArray`. Размер — `pixels.size`,
     * в порядке row-major. Альфа-канал игнорируется (PDF-страницы непрозрачные).
     */
    fun argbToBinary(
        pixels: IntArray,
        threshold: Int = DEFAULT_LUMINANCE_THRESHOLD,
    ): BooleanArray {
        val out = BooleanArray(pixels.size)
        for (i in pixels.indices) {
            val argb = pixels[i]
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            val luma = minOf(r, g, b)
            out[i] = luma < threshold
        }
        return out
    }

    /**
     * Сегмент линии — непрерывный run «тёмных» пикселей вдоль одной оси.
     *
     * Координаты в пиксельном пространстве: горизонтальный сегмент имеет `start`/`end`
     * по X и `perpPos` = Y; вертикальный — наоборот. `end` включительный.
     */
    data class LineSegment(
        val start: Int,
        val end: Int,
        val perpPos: Int,
    )

    /**
     * Горизонтальные сегменты ≥[minLength] пикселей: один проход по каждой строке.
     * Буквы текста (типично ≤20 px ширины основной горизонтальной части) отсекаются
     * порогом [minLength], а длинные грид-линии PDF (50+ px на типичной странице)
     * проходят.
     */
    fun findHorizontalRuns(
        binary: BooleanArray,
        width: Int,
        height: Int,
        minLength: Int,
    ): List<LineSegment> {
        require(binary.size == width * height) { "binary size must equal width*height" }
        require(minLength >= 1) { "minLength must be ≥1" }
        if (width <= 0 || height <= 0) return emptyList()
        val out = mutableListOf<LineSegment>()
        for (y in 0 until height) {
            val base = y * width
            var i = 0
            while (i < width) {
                if (binary[base + i]) {
                    var j = i + 1
                    while (j < width && binary[base + j]) j++
                    if (j - i >= minLength) out += LineSegment(start = i, end = j - 1, perpPos = y)
                    i = j
                } else {
                    i++
                }
            }
        }
        return out
    }

    /** Вертикальные сегменты ≥[minLength] пикселей — аналогично [findHorizontalRuns] по столбцам. */
    fun findVerticalRuns(
        binary: BooleanArray,
        width: Int,
        height: Int,
        minLength: Int,
    ): List<LineSegment> {
        require(binary.size == width * height) { "binary size must equal width*height" }
        require(minLength >= 1) { "minLength must be ≥1" }
        if (width <= 0 || height <= 0) return emptyList()
        val out = mutableListOf<LineSegment>()
        for (x in 0 until width) {
            var i = 0
            while (i < height) {
                if (binary[i * width + x]) {
                    var j = i + 1
                    while (j < height && binary[j * width + x]) j++
                    if (j - i >= minLength) out += LineSegment(start = i, end = j - 1, perpPos = x)
                    i = j
                } else {
                    i++
                }
            }
        }
        return out
    }

    /**
     * Грид-линия — кластер сегментов на близких перпендикулярных позициях. PDF-линия
     * обычно «толстая» 2–3 px (или anti-aliased на гранях), и каждая такая линия
     * порождает соседние [LineSegment] на нескольких подряд строках/столбцах.
     * Кластеризация сводит их к единой грид-линии: позиция = медианный `perpPos`
     * сегментов, extent — union интервалов.
     *
     * @property start левая/верхняя граница объединения сегментов (включительно)
     * @property end правая/нижняя граница объединения сегментов (включительно)
     * @property position перпендикулярная координата кластера (X для вертикальных, Y для горизонтальных)
     */
    data class GridLine(
        val start: Int,
        val end: Int,
        val position: Int,
    )

    /**
     * Кластеризует сегменты по близким `perpPos` (разница ≤[perpTolerancePx]) в
     * грид-линии. Внутри кластера extent — union (мин `start`, макс `end`), позиция
     * — медиана `perpPos`. Возвращаются по возрастанию позиции; сегменты должны
     * быть отсортированы по `perpPos` (метод сделает это сам).
     */
    fun clusterToGridLines(
        segments: List<LineSegment>,
        perpTolerancePx: Int,
    ): List<GridLine> {
        if (segments.isEmpty()) return emptyList()
        require(perpTolerancePx >= 0) { "perpTolerancePx must be ≥0" }
        val sorted = segments.sortedBy { it.perpPos }
        val out = mutableListOf<GridLine>()
        var clusterStart = sorted.first().start
        var clusterEnd = sorted.first().end
        var clusterPerps = mutableListOf(sorted.first().perpPos)
        for (i in 1 until sorted.size) {
            val seg = sorted[i]
            if (seg.perpPos - clusterPerps.last() <= perpTolerancePx) {
                if (seg.start < clusterStart) clusterStart = seg.start
                if (seg.end > clusterEnd) clusterEnd = seg.end
                clusterPerps += seg.perpPos
            } else {
                out += GridLine(start = clusterStart, end = clusterEnd, position = medianOf(clusterPerps))
                clusterStart = seg.start
                clusterEnd = seg.end
                clusterPerps = mutableListOf(seg.perpPos)
            }
        }
        out += GridLine(start = clusterStart, end = clusterEnd, position = medianOf(clusterPerps))
        return out
    }

    private fun medianOf(values: List<Int>): Int {
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }
}
