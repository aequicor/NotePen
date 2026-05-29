package ru.kyamshanov.notepen.reflow.segmentation

import ru.kyamshanov.notepen.reflow.api.ReflowRect

/**
 * Алгоритм XY-cut для определения порядка чтения многоколоночных и сложно
 * свёрстанных страниц (типично — OCR/HYBRID PDF, где `groupLines` склеивает
 * параллельные строки разных колонок в одну).
 *
 * Идея: рекурсивно ищем максимальный whitespace-разрыв вдоль одной оси,
 * чередуя горизонтальную и вертикальную; если разрыв превышает порог — режем
 * на два региона и рекурсируем по другой оси. Терминальные регионы выдаются в
 * порядке чтения (T→B для горизонтальных разрезов, L→R для вертикальных).
 *
 * Phase A: только алгоритм + тесты в commonMain. Интеграция с
 * [ru.kyamshanov.notepen.reflow.ReflowAssembler] (вызов перед `groupLines`,
 * обработка каждого региона независимо) — следующий слайс.
 *
 * Сложность: O(N log N) на каждом уровне рекурсии (сортировка/проекции по
 * глифам), глубина — O(log N) для разумно сегментированных страниц. На
 * практике — миллисекунды на странице с ≤5000 глифов.
 */
internal object XyCutSegmenter {
    /**
     * Минимальная доля стороны страницы для whitespace-разрыва, чтобы он
     * считался границей колонки/раздела. 2% от высоты ≈ 16 pt при странице
     * 800 pt — отделяет колонки и крупные межблочные зазоры от обычной
     * межстрочной выноски.
     */
    const val DEFAULT_MIN_GAP_FRACTION: Float = 0.02f

    /**
     * Сегментирует страницу: возвращает список регионов в порядке чтения.
     * Каждый регион — список индексов глифов из исходного [glyphRects]. Глифы
     * внутри одного региона перечислены в исходном порядке (вызывающая сторона
     * сама их группирует в строки/абзацы).
     *
     * `null`-входы и крайние случаи: пустой список → пустой список регионов;
     * одна точка / все глифы вырожденны → один регион со всеми индексами.
     *
     * @param glyphRects прямоугольники глифов в PDF-пунктах (top-left origin)
     * @param pageWidthPt ширина страницы для нормализации порога
     * @param pageHeightPt высота страницы для нормализации порога
     * @param minGapFraction см. [DEFAULT_MIN_GAP_FRACTION]
     */
    fun segment(
        glyphRects: List<ReflowRect>,
        pageWidthPt: Float,
        pageHeightPt: Float,
        minGapFraction: Float = DEFAULT_MIN_GAP_FRACTION,
    ): List<List<Int>> {
        if (glyphRects.isEmpty()) return emptyList()
        val refs = glyphRects.mapIndexed { idx, r -> GlyphRef(idx, r) }
        val minGapY = pageHeightPt * minGapFraction
        val minGapX = pageWidthPt * minGapFraction
        val out = mutableListOf<List<Int>>()
        recurse(refs, minGapY, minGapX, out)
        return out
    }

    /**
     * Рекурсивный шаг smart-XY-cut: на каждом уровне ищем максимальный разрыв
     * по обеим осям и режем по **большему** (а не строго чередуем оси).
     * Так двухколоночный layout с разной высотой колонок (правая короче левой)
     * сначала режется вертикально между колонками, а классический alternating
     * сначала отделил бы низ левой колонки от правой целиком — нарушая порядок
     * чтения.
     *
     * Reading order:
     *  - горизонтальный разрез → сначала верхний регион (`lower` = с меньшим Y),
     *    затем нижний;
     *  - вертикальный → сначала левый (`lower` = с меньшим X), затем правый.
     */
    private fun recurse(
        refs: List<GlyphRef>,
        minGapY: Float,
        minGapX: Float,
        out: MutableList<List<Int>>,
    ) {
        if (refs.isEmpty()) return
        val cut = if (refs.size >= 2) bestCut(refs, minGapY, minGapX) else null
        if (cut == null) {
            // Не делим — лист (один глиф или регион без значимых разрывов).
            out += refs.map { it.index }
        } else {
            recurse(cut.lower, minGapY, minGapX, out)
            recurse(cut.upper, minGapY, minGapX, out)
        }
    }

    /** Лучший разрез на этой итерации: выбираем ось с большим whitespace-gap. */
    private fun bestCut(
        refs: List<GlyphRef>,
        minGapY: Float,
        minGapX: Float,
    ): CutResult? {
        val hCut = tryHorizontalCut(refs, minGapY)
        val vCut = tryVerticalCut(refs, minGapX)
        return when {
            hCut != null && vCut != null -> if (hCut.gapWidth >= vCut.gapWidth) hCut else vCut
            hCut != null -> hCut
            else -> vCut
        }
    }

    /**
     * Ищет максимальный горизонтальный whitespace-разрыв (по Y) шире [minGapY] и,
     * если найден, делит [refs] на верхний/нижний регионы. `null` означает «нет
     * достаточного разрыва, регион неделим горизонтально».
     */
    private fun tryHorizontalCut(
        refs: List<GlyphRef>,
        minGapY: Float,
    ): CutResult? {
        val intervals = refs.map { it.rect.top to it.rect.bottom }
        val gap = largestGap(intervals, minGapY) ?: return null
        val splitY = (gap.first + gap.second) / 2f
        val lower = refs.filter { it.rect.top < splitY }
        val upper = refs.filter { it.rect.top >= splitY }
        return if (lower.isEmpty() || upper.isEmpty()) null else CutResult(gap.second - gap.first, lower, upper)
    }

    /**
     * Симметричный поиск вертикального whitespace-разрыва (по X). Используется
     * для отделения колонок и других вертикальных «коридоров».
     */
    private fun tryVerticalCut(
        refs: List<GlyphRef>,
        minGapX: Float,
    ): CutResult? {
        val intervals = refs.map { it.rect.left to it.rect.right }
        val gap = largestGap(intervals, minGapX) ?: return null
        val splitX = (gap.first + gap.second) / 2f
        val left = refs.filter { it.rect.left < splitX }
        val right = refs.filter { it.rect.left >= splitX }
        return if (left.isEmpty() || right.isEmpty()) null else CutResult(gap.second - gap.first, left, right)
    }

    /**
     * Результат разреза одной оси: ширина whitespace-разрыва (для выбора оси на
     * этом уровне рекурсии) + разделённые регионы в порядке чтения (`lower`
     * сначала: меньший Y для H, меньший X для V).
     */
    private data class CutResult(
        val gapWidth: Float,
        val lower: List<GlyphRef>,
        val upper: List<GlyphRef>,
    )

    /**
     * Самый большой пустой промежуток в проекции [intervals] (`start..end`)
     * на ось, шириной ≥ [minWidth]. Возвращает `(gapStart, gapEnd)` или `null`,
     * если такого нет.
     *
     * Алгоритм: сливаем перекрывающиеся интервалы (merge overlapping); пробелы
     * между соседними мерженными интервалами — кандидаты в разрывы; берём
     * самый широкий. O(N log N) на сортировке.
     */
    private fun largestGap(
        intervals: List<Pair<Float, Float>>,
        minWidth: Float,
    ): Pair<Float, Float>? {
        if (intervals.size < 2) return null
        val merged = mergeIntervals(intervals)
        return (0 until merged.size - 1).asSequence()
            .map { i -> merged[i].second to merged[i + 1].first }
            .filter { (start, end) -> end - start >= minWidth }
            .maxByOrNull { (start, end) -> end - start }
    }

    /** Сливает перекрывающиеся интервалы в неперекрывающиеся (по возрастанию). */
    private fun mergeIntervals(intervals: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
        val sorted = intervals.sortedBy { it.first }
        val merged = mutableListOf<Pair<Float, Float>>(sorted.first())
        for (i in 1 until sorted.size) {
            val (t, b) = sorted[i]
            val (lastT, lastB) = merged.last()
            if (t <= lastB) {
                merged[merged.size - 1] = lastT to maxOf(lastB, b)
            } else {
                merged += t to b
            }
        }
        return merged
    }

    /** Глиф с исходным индексом — нужен, чтобы вернуть индексы в порядке чтения. */
    private data class GlyphRef(
        val index: Int,
        val rect: ReflowRect,
    )
}
