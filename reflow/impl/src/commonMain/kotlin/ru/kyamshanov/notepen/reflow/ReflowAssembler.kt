package ru.kyamshanov.notepen.reflow

import ru.kyamshanov.notepen.reflow.api.PdfContentKind
import ru.kyamshanov.notepen.reflow.api.ReflowBlock
import ru.kyamshanov.notepen.reflow.api.ReflowDocument
import ru.kyamshanov.notepen.reflow.api.ReflowRect
import ru.kyamshanov.notepen.reflow.api.SourceSpan
import kotlin.math.abs

/**
 * Платформенно-нейтральная сборка [ReflowDocument] из сырых постраничных
 * данных ([RawPage]). Вся эвристика порядка чтения и группировки сосредоточена
 * здесь, чтобы покрываться unit-тестами и переиспользоваться обоими
 * экстракторами (PDFBox на JVM, PdfBox-Android на Android).
 *
 * Геометрия группировки (зазоры строк/абзацев) считается в **пунктах PDF**
 * (как и `bodyFont`), а итоговые [SourceSpan.bounds] / [ReflowBlock.Figure]
 * нормализуются в `[0..1]` относительно страницы — одно и то же из глифа, но в
 * разных системах координат.
 *
 * Этапы:
 *  1. классификация типа содержимого ([classify]);
 *  2. для каждой страницы — группировка глифов в строки, строк в абзацы и
 *     заголовки, вставка нетекстовых областей как фигур;
 *  3. провенанс: на каждый исходный ран глифов — [SourceSpan] с диапазоном
 *     символов в итоговом тексте блока.
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

    /** Горизонтальный зазор (доля кегля строки), трактуемый как пробел между словами (когда ширина пробела неизвестна). */
    private const val SPACE_FACTOR = 0.25f

    /** Доля ширины пробела шрифта, начиная с которой зазор считается межсловным (при известной ширине). */
    private const val SPACE_WIDTH_FRACTION = 0.5f

    /** Дефисы, снимаемые при переносе слова на конце строки: ASCII, типографский (‐), мягкий. */
    private const val HYPHEN_CHARS = "-‐­"

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
        val items = buildList {
            groupLines(page.glyphs, bodyFont, page).forEach { add(Item.Text(it)) }
            page.images.forEach { add(Item.Image(it)) }
        }.sortedBy { it.top }

        val builder = BlockBuilder(page.pageIndex, page.widthPt, page.heightPt, bodyFont)
        items.forEach { item ->
            when (item) {
                is Item.Text -> builder.addLine(item.line)
                is Item.Image -> builder.addImage(item.rect)
            }
        }
        return builder.build()
    }

    private fun groupLines(glyphs: List<RawGlyph>, bodyFont: Float, page: RawPage): List<Line> {
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
        return lines.map { buildLine(it, page) }
    }

    /**
     * Строит [Line] из глифов одной строки: каждый глиф становится
     * [SourcePiece] с нормализованным прямоугольником; [Line.spaceBefore]
     * помечает позиции, перед которыми по горизонтальному зазору нужен пробел.
     * Геометрия строки ([Line.top]/[Line.bottom]/[Line.fontSize]) остаётся в
     * пунктах для последующей группировки в абзацы.
     */
    private fun buildLine(glyphs: List<RawGlyph>, page: RawPage): Line {
        val sorted = glyphs.sortedBy { it.rect.left }
        val fontSize = medianFontSize(sorted)
        val pieces = ArrayList<SourcePiece>(sorted.size)
        val spaceBefore = ArrayList<Boolean>(sorted.size)
        var prevRight: Float? = null
        var pendingSpace = false
        for (glyph in sorted) {
            // Пробел PDFBox отдаёт отдельным глифом — это надёжная граница слова;
            // держим его как разделитель, но не как фрагмент-провенанс.
            if (glyph.text.isBlank()) {
                pendingSpace = true
                prevRight = glyph.rect.right
                continue
            }
            val gap = prevRight?.let { glyph.rect.left - it }
            // Порог пробела — по ширине пробела шрифта (если известна), иначе по кеглю.
            // Так настоящий межсловный зазор отличается от широкого трекинга букв
            // (в сканах буквы внутри слова могут стоять с заметными зазорами).
            val spaceThreshold =
                if (glyph.spaceWidthPt > 0f) glyph.spaceWidthPt * SPACE_WIDTH_FRACTION else fontSize * SPACE_FACTOR
            val needsSpace = pieces.isNotEmpty() &&
                (pendingSpace || (gap != null && gap > spaceThreshold))
            spaceBefore += needsSpace
            pieces += SourcePiece(
                text = glyph.text,
                pageIndex = page.pageIndex,
                bounds = glyph.rect.normalised(page.widthPt, page.heightPt),
            )
            pendingSpace = false
            prevRight = glyph.rect.right
        }
        return Line(
            pieces = pieces,
            spaceBefore = spaceBefore,
            top = sorted.minOf { it.rect.top },
            bottom = sorted.maxOf { it.rect.bottom },
            fontSize = fontSize,
        )
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

    /** Нормализует прямоугольник из пунктов в доли `[0..1]` страницы. */
    private fun ReflowRect.normalised(widthPt: Float, heightPt: Float): ReflowRect =
        if (widthPt <= 0f || heightPt <= 0f) {
            this
        } else {
            ReflowRect(left / widthPt, top / heightPt, right / widthPt, bottom / heightPt)
        }

    /**
     * Накопитель блоков одной колонки: преобразует поток строк/изображений в
     * [ReflowBlock.Heading] / [ReflowBlock.Paragraph] / [ReflowBlock.Figure],
     * склеивая строки в абзацы, снимая переносы по дефису и собирая провенанс
     * ([SourceSpan]) в координатах итогового текста блока.
     */
    private class BlockBuilder(
        private val pageIndex: Int,
        private val widthPt: Float,
        private val heightPt: Float,
        private val bodyFont: Float,
    ) {

        private val blocks = mutableListOf<ReflowBlock>()
        private val pending = mutableListOf<Line>()

        /** 0 — в накоплении абзац основного текста; >0 — заголовок этого уровня. */
        private var pendingHeadingLevel = 0

        fun addImage(rect: ReflowRect) {
            flush()
            blocks += ReflowBlock.Figure(pageIndex, rect.normalised(widthPt, heightPt))
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
            val built = if (pendingHeadingLevel > 0) buildHeading(pending) else buildParagraph(pending)
            if (built.text.isNotEmpty()) {
                blocks += if (pendingHeadingLevel > 0) {
                    ReflowBlock.Heading(built.text, pendingHeadingLevel, built.source)
                } else {
                    ReflowBlock.Paragraph(built.text, built.source)
                }
            }
            pending.clear()
            pendingHeadingLevel = 0
        }

        /** Абзац: межстрочный пробел, со снятием переноса по дефису. */
        private fun buildParagraph(lines: List<Line>): BuiltText {
            val sb = StringBuilder()
            val spans = mutableListOf<SourceSpan>()
            for (line in lines) {
                if (line.pieces.isEmpty()) continue
                if (sb.isNotEmpty()) {
                    if (isSoftHyphen(sb) && line.startsLowercase()) {
                        sb.deleteAt(sb.length - 1)
                        shrinkLastSpan(spans)
                    } else {
                        sb.append(' ')
                    }
                }
                appendPieces(line, sb, spans)
            }
            return BuiltText(sb.toString(), spans.filter { it.charStart < it.charEnd })
        }

        /** Заголовок: всегда межстрочный пробел, без логики переноса. */
        private fun buildHeading(lines: List<Line>): BuiltText {
            val sb = StringBuilder()
            val spans = mutableListOf<SourceSpan>()
            for (line in lines) {
                if (line.pieces.isEmpty()) continue
                if (sb.isNotEmpty()) sb.append(' ')
                appendPieces(line, sb, spans)
            }
            return BuiltText(sb.toString(), spans.filter { it.charStart < it.charEnd })
        }

        /**
         * Дописывает раны строки в [sb], вставляя внутристрочные пробелы по
         * [Line.spaceBefore], и фиксирует [SourceSpan] на каждый ран. Разделители
         * (пробелы) не покрываются ни одним спаном.
         */
        private fun appendPieces(line: Line, sb: StringBuilder, spans: MutableList<SourceSpan>) {
            line.pieces.forEachIndexed { index, piece ->
                if (index > 0 && line.spaceBefore[index]) sb.append(' ')
                val start = sb.length
                sb.append(piece.text)
                spans += SourceSpan(piece.pageIndex, start, sb.length, piece.bounds)
            }
        }

        /** Укорачивает последний спан на 1 символ (снятый дефис в конце буфера). */
        private fun shrinkLastSpan(spans: MutableList<SourceSpan>) {
            if (spans.isEmpty()) return
            val last = spans.removeAt(spans.size - 1)
            spans += last.copy(charEnd = last.charEnd - 1)
        }

        private fun isSoftHyphen(sb: StringBuilder): Boolean =
            sb.length >= 2 && sb.last() in HYPHEN_CHARS && sb[sb.length - 2].isLetter()
    }

    private data class SourcePiece(
        val text: String,
        val pageIndex: Int,
        val bounds: ReflowRect,
    )

    private data class BuiltText(
        val text: String,
        val source: List<SourceSpan>,
    )

    private data class Line(
        val pieces: List<SourcePiece>,
        val spaceBefore: List<Boolean>,
        val top: Float,
        val bottom: Float,
        val fontSize: Float,
    ) {
        fun startsLowercase(): Boolean = pieces.firstOrNull()?.text?.firstOrNull()?.isLowerCase() == true
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
