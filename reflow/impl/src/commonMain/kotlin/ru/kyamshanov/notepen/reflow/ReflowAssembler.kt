package ru.kyamshanov.notepen.reflow

import ru.kyamshanov.notepen.reflow.api.PdfContentKind
import ru.kyamshanov.notepen.reflow.api.ReflowBlock
import ru.kyamshanov.notepen.reflow.api.ReflowDocument
import ru.kyamshanov.notepen.reflow.api.ReflowRect
import kotlin.math.abs

/**
 * Платформенно-нейтральная сборка [ReflowDocument] из сырых постраничных
 * данных ([RawPage]). Вся эвристика порядка чтения и группировки сосредоточена
 * здесь, чтобы покрываться unit-тестами и переиспользоваться обоими
 * экстракторами (PDFBox на JVM, PdfBox-Android на Android).
 *
 * Этапы:
 *  1. классификация типа содержимого ([classify]);
 *  2. для каждой страницы — детекция колонок, группировка глифов в строки,
 *     строк в абзацы/заголовки, вставка нетекстовых областей как фигур.
 */
internal object ReflowAssembler {

    /** Кегль строки относительно основного, начиная с которого она — заголовок. */
    private const val HEADING_RATIO = 1.2f

    /** Порог смены кегля (доля основного), разрывающий абзац. */
    private const val FONT_CHANGE_FRAC = 0.15f

    /** Вертикальный зазор (доля основного кегля) между строками, разрывающий абзац. */
    private const val PARA_GAP_FACTOR = 0.7f

    /** Допуск группировки глифов в одну строку (доля основного кегля). */
    private const val LINE_TOLERANCE_FRAC = 0.5f

    /** Горизонтальный зазор (доля кегля строки), трактуемый как пробел между словами. */
    private const val SPACE_FACTOR = 0.25f

    /** Минимум глифов на странице, при котором вообще ищем колонки. */
    private const val MIN_GLYPHS_FOR_COLUMNS = 40

    /** Центральная полоса страницы (доли ширины), где ищем межколоночный «ручей». */
    private const val COLUMN_BAND_START = 0.35f
    private const val COLUMN_BAND_END = 0.65f

    /** Число шагов сканирования при поиске межколоночного зазора. */
    private const val COLUMN_SCAN_STEPS = 20

    /** Максимальная доля глифов, пересекающих линию разреза, при которой это всё ещё две колонки. */
    private const val COLUMN_CROSS_FRAC = 0.02f

    /** Минимальная доля глифов с каждой стороны разреза, чтобы признать две колонки. */
    private const val COLUMN_SIDE_FRAC = 0.2f

    private const val HEADING_LEVEL_1_RATIO = 1.8f
    private const val HEADING_LEVEL_2_RATIO = 1.4f

    /**
     * Классифицирует тип содержимого по наличию текстового слоя на страницах.
     */
    fun classify(pages: List<RawPage>): PdfContentKind {
        if (pages.isEmpty()) return PdfContentKind.IMAGE_ONLY
        val textPages = pages.count { it.glyphs.isNotEmpty() }
        return when (textPages) {
            0 -> PdfContentKind.IMAGE_ONLY
            pages.size -> PdfContentKind.TEXT_BASED
            else -> PdfContentKind.HYBRID
        }
    }

    /**
     * Собирает [ReflowDocument] из всех страниц: блоки идут в порядке чтения,
     * страницы конкатенируются (постраничная вёрстка снимается).
     */
    fun assemble(pages: List<RawPage>): ReflowDocument {
        val bodyFont = medianFontSize(pages.flatMap { it.glyphs })
        val blocks = pages.flatMap { buildPageBlocks(it, bodyFont) }
        return ReflowDocument(kind = classify(pages), blocks = blocks)
    }

    private fun buildPageBlocks(page: RawPage, bodyFont: Float): List<ReflowBlock> {
        val result = mutableListOf<ReflowBlock>()
        for (col in detectColumns(page)) {
            val colGlyphs = page.glyphs.filter { col.contains(centerX(it.rect)) }
            val colImages = page.images.filter { col.contains(centerX(it)) }
            val items = buildList {
                groupLines(colGlyphs, bodyFont).forEach { add(Item.Text(it)) }
                colImages.forEach { add(Item.Image(it)) }
            }.sortedBy { it.top }

            val builder = BlockBuilder(page.pageIndex, bodyFont)
            items.forEach { item ->
                when (item) {
                    is Item.Text -> builder.addLine(item.line)
                    is Item.Image -> builder.addImage(item.rect)
                }
            }
            result += builder.build()
        }
        return result
    }

    private fun groupLines(glyphs: List<RawGlyph>, bodyFont: Float): List<Line> {
        if (glyphs.isEmpty()) return emptyList()
        val reference = if (bodyFont > 0f) bodyFont else medianFontSize(glyphs)
        val tolerance = reference * LINE_TOLERANCE_FRAC
        val sorted = glyphs.sortedBy { it.rect.top }
        val lines = mutableListOf<MutableList<RawGlyph>>()
        var lineTop = sorted.first().rect.top
        for (glyph in sorted) {
            if (lines.isEmpty() || glyph.rect.top - lineTop > tolerance) {
                lines += mutableListOf(glyph)
                lineTop = glyph.rect.top
            } else {
                lines.last() += glyph
            }
        }
        return lines.map { buildLine(it) }
    }

    private fun buildLine(glyphs: List<RawGlyph>): Line {
        val sorted = glyphs.sortedBy { it.rect.left }
        val fontSize = medianFontSize(sorted)
        val sb = StringBuilder()
        var prevRight: Float? = null
        for (glyph in sorted) {
            val gap = prevRight?.let { glyph.rect.left - it }
            if (gap != null && gap > fontSize * SPACE_FACTOR && sb.isNotEmpty() && !sb.last().isWhitespace()) {
                sb.append(' ')
            }
            sb.append(glyph.text)
            prevRight = glyph.rect.right
        }
        return Line(
            text = sb.toString(),
            top = sorted.minOf { it.rect.top },
            bottom = sorted.maxOf { it.rect.bottom },
            fontSize = fontSize,
        )
    }

    private fun detectColumns(page: RawPage): List<ColumnRange> {
        val full = listOf(ColumnRange(0f, page.widthPt + 1f))
        val glyphs = page.glyphs
        if (glyphs.size < MIN_GLYPHS_FOR_COLUMNS) return full

        val minX = glyphs.minOf { it.rect.left }
        val maxX = glyphs.maxOf { it.rect.right }
        val step = ((maxX - minX) / COLUMN_SCAN_STEPS).coerceAtLeast(1f)

        var cut = page.widthPt * COLUMN_BAND_START
        var bestCross = Int.MAX_VALUE
        var x = page.widthPt * COLUMN_BAND_START
        while (x <= page.widthPt * COLUMN_BAND_END) {
            val crossing = glyphs.count { it.rect.left < x && it.rect.right > x }
            if (crossing < bestCross) {
                bestCross = crossing
                cut = x
            }
            x += step
        }

        val crossFraction = bestCross.toFloat() / glyphs.size
        val leftCount = glyphs.count { centerX(it.rect) < cut }
        val rightCount = glyphs.size - leftCount
        val sidesBalanced = leftCount >= glyphs.size * COLUMN_SIDE_FRAC &&
            rightCount >= glyphs.size * COLUMN_SIDE_FRAC

        return if (crossFraction <= COLUMN_CROSS_FRAC && sidesBalanced) {
            listOf(ColumnRange(minX - 1f, cut), ColumnRange(cut, maxX + 1f))
        } else {
            full
        }
    }

    private fun medianFontSize(glyphs: List<RawGlyph>): Float {
        if (glyphs.isEmpty()) return 0f
        val sizes = glyphs.map { it.fontSizePt }.sorted()
        return sizes[sizes.size / 2]
    }

    private fun headingLevelForRatio(ratio: Float): Int = when {
        ratio >= HEADING_LEVEL_1_RATIO -> 1
        ratio >= HEADING_LEVEL_2_RATIO -> 2
        else -> 3
    }

    private fun centerX(rect: ReflowRect): Float = (rect.left + rect.right) / 2f

    /**
     * Накопитель блоков одной колонки: преобразует поток строк/изображений в
     * [ReflowBlock.Heading] / [ReflowBlock.Paragraph] / [ReflowBlock.Figure],
     * склеивая строки в абзацы и снимая переносы по дефису.
     */
    private class BlockBuilder(private val pageIndex: Int, private val bodyFont: Float) {

        private val blocks = mutableListOf<ReflowBlock>()
        private val pending = mutableListOf<Line>()

        /** 0 — в накоплении абзац основного текста; >0 — заголовок этого уровня. */
        private var pendingHeadingLevel = 0

        fun addImage(rect: ReflowRect) {
            flush()
            blocks += ReflowBlock.Figure(pageIndex, rect)
        }

        fun addLine(line: Line) {
            val level = headingLevelOf(line)
            if (level > 0) {
                if (pending.isNotEmpty() && pendingHeadingLevel == level) {
                    pending += line
                } else {
                    flush()
                    pendingHeadingLevel = level
                    pending += line
                }
                return
            }
            if (pending.isNotEmpty() && breaksParagraph(line)) flush()
            pendingHeadingLevel = 0
            pending += line
        }

        fun build(): List<ReflowBlock> {
            flush()
            return blocks
        }

        private fun headingLevelOf(line: Line): Int =
            if (bodyFont > 0f && line.fontSize >= bodyFont * HEADING_RATIO) {
                headingLevelForRatio(line.fontSize / bodyFont)
            } else {
                0
            }

        private fun breaksParagraph(line: Line): Boolean {
            if (pendingHeadingLevel > 0) return true
            val last = pending.last()
            val gap = line.top - last.bottom
            val fontJump = bodyFont > 0f && abs(line.fontSize - last.fontSize) > bodyFont * FONT_CHANGE_FRAC
            return gap > bodyFont * PARA_GAP_FACTOR || fontJump
        }

        private fun flush() {
            if (pending.isEmpty()) return
            if (pendingHeadingLevel > 0) {
                val text = pending.joinToString(" ") { it.text.trim() }.trim()
                if (text.isNotEmpty()) blocks += ReflowBlock.Heading(text, pendingHeadingLevel)
            } else {
                val text = joinParagraph(pending)
                if (text.isNotEmpty()) blocks += ReflowBlock.Paragraph(text)
            }
            pending.clear()
            pendingHeadingLevel = 0
        }

        private fun joinParagraph(lines: List<Line>): String {
            val sb = StringBuilder()
            for (line in lines) {
                val text = line.text.trim()
                if (text.isEmpty()) continue
                if (sb.isEmpty()) {
                    sb.append(text)
                    continue
                }
                if (isSoftHyphen(sb) && text.first().isLowerCase()) {
                    sb.deleteAt(sb.length - 1)
                    sb.append(text)
                } else {
                    sb.append(' ').append(text)
                }
            }
            return sb.toString()
        }

        private fun isSoftHyphen(sb: StringBuilder): Boolean =
            sb.length >= 2 && sb.last() == '-' && sb[sb.length - 2].isLetter()
    }

    private data class Line(
        val text: String,
        val top: Float,
        val bottom: Float,
        val fontSize: Float,
    )

    private class ColumnRange(private val left: Float, private val right: Float) {
        fun contains(x: Float): Boolean = x >= left && x < right
    }

    private sealed interface Item {
        val top: Float

        data class Text(val line: Line) : Item {
            override val top: Float get() = line.top
        }

        data class Image(val rect: ReflowRect) : Item {
            override val top: Float get() = rect.top
        }
    }
}
