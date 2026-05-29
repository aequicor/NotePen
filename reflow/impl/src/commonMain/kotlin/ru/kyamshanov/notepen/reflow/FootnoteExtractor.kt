package ru.kyamshanov.notepen.reflow

import ru.kyamshanov.notepen.reflow.api.ReflowBlock
import ru.kyamshanov.notepen.reflow.api.SourceSpan

/**
 * Извлекает сноски (footnotes) из [RawPage]: small-font блок в нижней четверти
 * страницы. Возвращает пару (page без сносок, footnote-block если найден).
 *
 * Эвристика: глиф попадает в footnote-зону если
 *  - `rect.top >= heightPt × FOOTNOTE_ZONE_FRACTION` (нижняя ~25% страницы);
 *  - `fontSizePt <= bodyFont × FOOTNOTE_FONT_RATIO` (заметно меньше body-кегля);
 *  - Footnote-зона должна содержать ≥ FOOTNOTE_MIN_GLYPHS глифов (иначе это
 *    случайный мусор / номер страницы и т.п.).
 *
 * Полученные глифы группируются в строки и собираются в [ReflowBlock.Footnote]
 * с текстом, разделённым пробелами (как Paragraph). Marker (число/символ ссылки)
 * пока не парсится — это Phase 2.
 */
internal object FootnoteExtractor {
    /** Граница footnote-зоны по Y (доля высоты страницы). */
    private const val FOOTNOTE_ZONE_FRACTION = 0.75f

    /** Footnote font ≤ bodyFont × этот множитель. */
    private const val FOOTNOTE_FONT_RATIO = 0.85f

    /** Минимум глифов в зоне, чтобы счесть это footnote'ом, а не page number'ом. */
    private const val FOOTNOTE_MIN_GLYPHS = 12

    /**
     * Разделяет [page] на «main» (для обычной обработки) и сноску. Сноска
     * возвращается отдельным [ReflowBlock.Footnote] или `null`, если зона пустая
     * или не прошла гарды.
     */
    fun extractFootnote(
        page: RawPage,
        bodyFont: Float,
    ): FootnoteSplit {
        if (page.glyphs.isEmpty() || page.heightPt <= 0f || bodyFont <= 0f) {
            return FootnoteSplit(page = page, footnote = null)
        }
        val zoneTop = page.heightPt * FOOTNOTE_ZONE_FRACTION
        val maxFootnoteFont = bodyFont * FOOTNOTE_FONT_RATIO
        val footnoteIndices =
            page.glyphs.withIndex()
                .filter { (_, g) -> g.rect.top >= zoneTop && g.fontSizePt <= maxFootnoteFont }
                .map { it.index }
        if (footnoteIndices.size < FOOTNOTE_MIN_GLYPHS) {
            return FootnoteSplit(page = page, footnote = null)
        }
        val footnoteSet = footnoteIndices.toHashSet()
        val footnoteGlyphs = footnoteIndices.map { page.glyphs[it] }
        val cleanedPage =
            page.copy(
                glyphs = page.glyphs.filterIndexed { idx, _ -> idx !in footnoteSet },
            )
        val footnote = buildFootnoteBlock(footnoteGlyphs, page.pageIndex, page.widthPt, page.heightPt)
        return FootnoteSplit(page = cleanedPage, footnote = footnote)
    }

    /**
     * Собирает [ReflowBlock.Footnote] из глифов footnote-зоны: сортируем по
     * (top, left), склеиваем пробелами при значимом X-gap, переносы строк
     * рассматриваем как пробелы.
     */
    private fun buildFootnoteBlock(
        glyphs: List<RawGlyph>,
        pageIndex: Int,
        widthPt: Float,
        heightPt: Float,
    ): ReflowBlock.Footnote {
        val sorted = glyphs.sortedWith(compareBy<RawGlyph> { it.rect.top }.thenBy { it.rect.left })
        val sb = StringBuilder()
        val spans = mutableListOf<SourceSpan>()
        var prevRight = Float.NaN
        var prevTop = Float.NaN
        for (g in sorted) {
            if (sb.isNotEmpty()) {
                val newLine = !prevTop.isNaN() && g.rect.top - prevTop > g.fontSizePt * NEWLINE_THRESHOLD
                val wideGap = !prevRight.isNaN() && g.rect.left - prevRight > g.fontSizePt * SPACE_THRESHOLD
                if (newLine || wideGap) sb.append(' ')
            }
            val start = sb.length
            sb.append(g.text)
            spans +=
                SourceSpan(
                    pageIndex = pageIndex,
                    charStart = start,
                    charEnd = sb.length,
                    bounds = normalized(g.rect, widthPt, heightPt),
                    bold = g.bold,
                    monospace = g.monospace,
                    italic = g.italic,
                )
            prevRight = g.rect.right
            prevTop = g.rect.top
        }
        return ReflowBlock.Footnote(text = sb.toString().trim(), source = spans)
    }

    private fun normalized(
        rect: ru.kyamshanov.notepen.reflow.api.ReflowRect,
        widthPt: Float,
        heightPt: Float,
    ): ru.kyamshanov.notepen.reflow.api.ReflowRect =
        if (widthPt <= 0f || heightPt <= 0f) {
            rect
        } else {
            ru.kyamshanov.notepen.reflow.api.ReflowRect(
                left = rect.left / widthPt,
                top = rect.top / heightPt,
                right = rect.right / widthPt,
                bottom = rect.bottom / heightPt,
            )
        }

    private const val SPACE_THRESHOLD = 0.3f
    private const val NEWLINE_THRESHOLD = 0.5f

    data class FootnoteSplit(
        val page: RawPage,
        val footnote: ReflowBlock.Footnote?,
    )
}
