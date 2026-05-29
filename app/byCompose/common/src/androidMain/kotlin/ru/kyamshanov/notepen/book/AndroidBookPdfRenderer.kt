package ru.kyamshanov.notepen.book

import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import ru.kyamshanov.notepen.reflow.api.PdfContentKind
import ru.kyamshanov.notepen.reflow.api.ReflowBlock
import ru.kyamshanov.notepen.reflow.api.ReflowDocument
import ru.kyamshanov.notepen.reflow.api.ReflowRect
import ru.kyamshanov.notepen.reflow.api.SourceSpan
import java.io.File
import java.io.FileOutputStream

/**
 * Верстает [BookContent] в PDF на Android.
 *
 * Текст рисуется как настоящий векторный текст PDF (через [Canvas.drawText]
 * силами [StaticLayout]) с использованием системных шрифтов — кириллица
 * поддерживается из коробки, файл компактный, текст чёткий при любом зуме.
 * Инлайн-начертание ([RichText]) переносится в [SpannableStringBuilder], а
 * [StaticLayout] отрисовывает спаны нативно.
 *
 * Встроенные шрифты EPUB ([BookContent.fonts]) здесь намеренно не применяются:
 * [StaticLayout] и так делает системный фолбэк по скриптам, а принудительный
 * авторский шрифт мог бы сломать это покрытие (например, кириллицу).
 */
object AndroidBookPdfRenderer {
    private const val PAGE_WIDTH = 595 // A4 in points (1/72")
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 54

    private const val BODY_SIZE = 13f

    /**
     * Втяжка первой строки абзаца ("красная строка") — 1.5 кегля основного
     * текста, конвенциональный книжный отступ (тот же em-множитель, что и в
     * JVM-рендерере, для физической согласованности). Применяется только к
     * [ContentBlock.Paragraph]; заголовки, списки, цитаты и таблицы — без неё.
     */
    private val PARAGRAPH_FIRST_LINE_INDENT = (BODY_SIZE * 1.5f).toInt()
    private const val BLOCKQUOTE_INDENT = 28
    private const val LIST_INDENT_STEP = 22
    private const val LINE_SPACING_MULT = 1.25f
    private const val PARAGRAPH_GAP = 8
    private const val HEADING_GAP_BEFORE = 14
    private const val HEADING_GAP_AFTER = 6

    /**
     * Заголовки-плейсхолдеры, которые иногда встречаются в FB2 (`<section>` без
     * `<title>`, FB2-обёртки с многоточием или звёздочками). Сам блок рисуется,
     * но в TOC не пишется — иначе боковая панель оглавления забивается
     * безымянными `…`-entries.
     */
    private val TOC_PLACEHOLDER_TITLES = setOf("...", "…", "* * *", "***", "*")
    private const val RULE_GAP = 14
    private const val IMAGE_GAP = 10
    private const val QUOTE_COLOR = 0xFF444444.toInt()
    private const val LINK_COLOR = 0xFF1A0DAB.toInt()
    private const val SUPER_SUB_SCALE = 0.75f

    private val HEADING_SIZES = floatArrayOf(26f, 22f, 19f, 17f, 15f, 14f)

    /**
     * @param book книга для верстки
     * @param output файл назначения PDF (создаётся/перезаписывается)
     */
    fun render(
        book: BookContent,
        output: File,
    ): BookRenderResult {
        val pdf = PdfDocument()
        val composer = PageComposer(pdf)
        book.metadata.title
            ?.takeIf { it.isNotBlank() }
            ?.let { title ->
                val laid = composer.heading(level = 1, text = title)
                composer.add(ReflowBlock.Heading(text = laid.text, level = 1, source = laid.spans))
            }
        book.metadata.author
            ?.takeIf { it.isNotBlank() }
            ?.let { author ->
                val laid = composer.paragraph(listOf(InlineSpan(author)), italic = true)
                composer.add(ReflowBlock.Paragraph(text = laid.text, source = laid.spans))
            }
        for (block in book.blocks) composer.render(block)
        composer.finish()

        output.parentFile?.mkdirs()
        try {
            FileOutputStream(output).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
        return BookRenderResult(toc = composer.outline(), reflow = composer.reflow())
    }

    /** Накопитель страниц PDF с курсором верстки сверху вниз. */
    private class PageComposer(
        private val pdf: PdfDocument,
    ) {
        private val contentWidth = PAGE_WIDTH - 2 * MARGIN
        private val contentBottom = PAGE_HEIGHT - MARGIN
        private val tocEntries = mutableListOf<TocEntry>()
        private var pageNumber = 1
        private var page = startPage(pageNumber)
        private var canvas: Canvas = page.canvas
        private var cursorY = MARGIN

        // Reflow-блоки копятся параллельно растровой верстке: их провенанс
        // ([SourceSpan]) берётся из геометрии StaticLayout, поэтому координаты
        // совпадают со штрихами editor'а на той же странице PDF.
        private val reflowBlocks = mutableListOf<ReflowBlock>()

        fun finish() {
            pdf.finishPage(page)
        }

        fun outline(): List<TocEntry> = tocEntries.toList()

        /** Готовый reflow-документ, собранный из тех же блоков, что легли в PDF. */
        fun reflow(): ReflowDocument = ReflowDocument(kind = PdfContentKind.TEXT_BASED, blocks = reflowBlocks.toList())

        /** Добавляет блок в reflow-поток в порядке верстки. */
        fun add(block: ReflowBlock) {
            reflowBlocks.add(block)
        }

        /** Текст блока и его провенанс, накопленные при верстке (для reflow). */
        data class BlockLayout(
            val text: String,
            val spans: List<SourceSpan>,
        )

        fun render(block: ContentBlock) {
            when (block) {
                is ContentBlock.Heading -> {
                    val laid = heading(block.level, block.text)
                    add(ReflowBlock.Heading(text = laid.text, level = block.level, source = laid.spans))
                }
                is ContentBlock.Paragraph -> {
                    val laid = paragraph(block.text, firstLineIndent = PARAGRAPH_FIRST_LINE_INDENT)
                    add(ReflowBlock.Paragraph(text = laid.text, source = laid.spans))
                }
                is ContentBlock.Blockquote -> {
                    val laid = paragraph(block.text, italic = true, indent = BLOCKQUOTE_INDENT)
                    add(ReflowBlock.Blockquote(text = laid.text, source = laid.spans))
                }
                is ContentBlock.ListItem -> {
                    val laid =
                        paragraph(listOf(InlineSpan(markerFor(block))) + block.text, indent = (block.level + 1) * LIST_INDENT_STEP)
                    add(ReflowBlock.ListItem(text = laid.text, source = laid.spans))
                }
                is ContentBlock.Table -> {
                    block.rows.forEach { row -> paragraph(listOf(InlineSpan(row.joinToString("   |   ")))) }
                    add(
                        ReflowBlock.Table(
                            rows =
                                block.rows.map { row ->
                                    ReflowBlock.TableRow(cells = row.map { ReflowBlock.TableCell(text = it) })
                                },
                        ),
                    )
                }
                is ContentBlock.Image -> image(block)?.let { add(it) }
                ContentBlock.HorizontalRule -> {
                    rule()
                    add(ReflowBlock.Divider)
                }
                ContentBlock.PageBreak -> if (cursorY > MARGIN) newPage()
            }
        }

        fun heading(
            level: Int,
            text: String,
        ): BlockLayout {
            cursorY += HEADING_GAP_BEFORE
            val size = HEADING_SIZES[(level - 1).coerceIn(0, HEADING_SIZES.lastIndex)]
            if (cursorY + size * LINE_SPACING_MULT > contentBottom) newPage()
            val tocTitle = text.trim()
            // F-4 oborona: пропускаем заголовки-плейсхолдеры из TOC (пустые,
            // «...» / «…» / «* * *»). Сам блок при этом всё ещё рисуется как
            // Heading в потоке чтения — TOC просто без шумных безымянных entries.
            if (tocTitle.isNotEmpty() && tocTitle !in TOC_PLACEHOLDER_TITLES) {
                tocEntries.add(TocEntry(level = level, title = tocTitle, pageIndex = pageNumber - 1))
            }
            val paint = textPaint(size, Typeface.create(Typeface.SERIF, Typeface.BOLD), Color.BLACK)
            val spans = drawLayout(text, paint, indent = 0, defaultBold = true)
            cursorY += HEADING_GAP_AFTER
            return BlockLayout(text = text, spans = spans)
        }

        /**
         * @param indent сдвиг всего блока вправо (цитаты, втяжка списков)
         * @param firstLineIndent дополнительная втяжка только первой строки
         *   ("красная строка"); для не-абзацных блоков — 0
         */
        fun paragraph(
            spans: RichText,
            italic: Boolean = false,
            indent: Int = 0,
            firstLineIndent: Int = 0,
        ): BlockLayout {
            val typeface = Typeface.create(Typeface.SERIF, if (italic) Typeface.ITALIC else Typeface.NORMAL)
            val color = if (italic) QUOTE_COLOR else Color.BLACK
            val content = spannableOf(spans)
            val sourceSpans =
                drawLayout(content, textPaint(BODY_SIZE, typeface, color), indent, firstLineIndent, defaultBold = false)
            cursorY += PARAGRAPH_GAP
            return BlockLayout(text = content.toString(), spans = sourceSpans)
        }

        private fun textPaint(
            size: Float,
            typeface: Typeface,
            color: Int,
        ): TextPaint =
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                this.textSize = size
                this.typeface = typeface
                this.color = color
                isSubpixelText = true
            }

        /** Строит [SpannableStringBuilder] из [RichText]; спаны применяет [StaticLayout]. */
        private fun spannableOf(spans: RichText): CharSequence {
            val builder = SpannableStringBuilder()
            for (span in spans) {
                val start = builder.length
                builder.append(span.text)
                val end = builder.length
                if (end == start) continue
                styleOf(span)?.let { builder.setSpan(StyleSpan(it), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
                if (span.code) builder.setSpan(TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (span.superscript) {
                    builder.setSpan(SuperscriptSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    builder.setSpan(RelativeSizeSpan(SUPER_SUB_SCALE), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                if (span.subscript) {
                    builder.setSpan(SubscriptSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    builder.setSpan(RelativeSizeSpan(SUPER_SUB_SCALE), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                if (span.link) {
                    builder.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    builder.setSpan(ForegroundColorSpan(LINK_COLOR), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            return builder
        }

        private fun styleOf(span: InlineSpan): Int? =
            when {
                span.bold && span.italic -> Typeface.BOLD_ITALIC
                span.bold -> Typeface.BOLD
                span.italic -> Typeface.ITALIC
                else -> null
            }

        /**
         * @param firstLineIndent втяжка только первой строки ("красная строка").
         *   Реализуется нативно через [StaticLayout.Builder.setIndents]: массив
         *   задаёт отступ слева по строкам, для строк за последним элементом
         *   повторяется последний (AOSP `StaticLayout#getIndentAdjust`), поэтому
         *   `[firstLineIndent, 0]` сдвигает и сужает только первую строку, а все
         *   следующие — без отступа.
         */
        private fun drawLayout(
            text: CharSequence,
            paint: TextPaint,
            indent: Int,
            firstLineIndent: Int = 0,
            defaultBold: Boolean = false,
        ): List<SourceSpan> {
            val left = MARGIN + indent
            val width = (contentWidth - indent).coerceAtLeast(1)
            val builder =
                StaticLayout.Builder
                    .obtain(text, 0, text.length, paint, width)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, LINE_SPACING_MULT)
                    .setIncludePad(false)
            if (firstLineIndent > 0) builder.setIndents(intArrayOf(firstLineIndent, 0), null)
            return drawLayoutPaginated(builder.build(), left, defaultBold)
        }

        private fun drawLayoutPaginated(
            layout: StaticLayout,
            left: Int,
            defaultBold: Boolean,
        ): List<SourceSpan> {
            val spans = mutableListOf<SourceSpan>()
            var line = 0
            while (line < layout.lineCount) {
                val startTop = layout.getLineTop(line)
                val available = contentBottom - cursorY
                var end = line
                while (end < layout.lineCount && layout.getLineBottom(end) - startTop <= available) end++
                if (end == line) {
                    if (cursorY > MARGIN) {
                        newPage()
                        continue
                    }
                    end = line + 1 // single oversized line: emit on an otherwise empty page
                }
                val drawnHeight = layout.getLineTop(end) - startTop
                canvas.save()
                canvas.translate(left.toFloat(), (cursorY - startTop).toFloat())
                canvas.clipRect(0, startTop, layout.width, layout.getLineTop(end))
                layout.draw(canvas)
                canvas.restore()
                // Провенанс: слова рисуемых строк в нормализованных координатах
                // страницы. y этих строк на странице = cursorY + (lineTop − startTop).
                for (l in line until end) {
                    val pageTopPx = cursorY + (layout.getLineTop(l) - startTop)
                    spans += recordLineSpans(layout, l, left, pageTopPx, defaultBold)
                }
                cursorY += drawnHeight
                line = end
                if (line < layout.lineCount) newPage()
            }
            return spans
        }

        /**
         * Эмитит по [SourceSpan] на каждое слово строки [l]: нормализованные
         * `[0..1]` границы слова на текущей странице (та же система, что у штрихов
         * editor'а) + диапазон символов в тексте блока. x слов берётся из
         * [StaticLayout.getPrimaryHorizontal], вертикаль — из высоты строки.
         */
        private fun recordLineSpans(
            layout: StaticLayout,
            l: Int,
            left: Int,
            pageTopPx: Int,
            defaultBold: Boolean,
        ): List<SourceSpan> {
            val text = layout.text
            val lineStart = layout.getLineStart(l)
            val lineEnd = layout.getLineEnd(l)
            val top = pageTopPx.toFloat() / PAGE_HEIGHT
            val bottom = (pageTopPx + (layout.getLineBottom(l) - layout.getLineTop(l))).toFloat() / PAGE_HEIGHT
            val pageIdx = pageNumber - 1
            val out = mutableListOf<SourceSpan>()
            var i = lineStart
            while (i < lineEnd) {
                while (i < lineEnd && text[i].isWhitespace()) i++
                if (i >= lineEnd) break
                val wordStart = i
                while (i < lineEnd && !text[i].isWhitespace()) i++
                val wordEnd = i
                val leftX = (left + layout.getPrimaryHorizontal(wordStart)) / PAGE_WIDTH
                val rightX = (left + layout.getPrimaryHorizontal(wordEnd.coerceAtMost(lineEnd))) / PAGE_WIDTH
                out.add(
                    SourceSpan(
                        pageIndex = pageIdx,
                        charStart = wordStart,
                        charEnd = wordEnd,
                        bounds = ReflowRect(left = leftX, top = top, right = rightX, bottom = bottom),
                        bold = isBold(text, wordStart, wordEnd, defaultBold),
                        monospace = isMono(text, wordStart, wordEnd),
                    ),
                )
            }
            return out
        }

        private fun isBold(
            text: CharSequence,
            start: Int,
            end: Int,
            defaultBold: Boolean,
        ): Boolean {
            if (defaultBold) return true
            return text is Spanned &&
                text.getSpans(start, end, StyleSpan::class.java).any {
                    it.style == Typeface.BOLD || it.style == Typeface.BOLD_ITALIC
                }
        }

        private fun isMono(
            text: CharSequence,
            start: Int,
            end: Int,
        ): Boolean {
            if (text !is Spanned) return false
            return text.getSpans(start, end, TypefaceSpan::class.java).any { it.family == "monospace" }
        }

        private fun image(block: ContentBlock.Image): ReflowBlock.Figure? {
            val bitmap =
                runCatching { BitmapFactory.decodeByteArray(block.data, 0, block.data.size) }
                    .getOrNull() ?: return null
            val maxHeight = contentBottom - MARGIN
            var width = bitmap.width
            var height = bitmap.height
            if (width > contentWidth) {
                height = height * contentWidth / width
                width = contentWidth
            }
            if (height > maxHeight) {
                width = width * maxHeight / height
                height = maxHeight
            }
            if (cursorY + height > contentBottom) newPage()
            val x = MARGIN + (contentWidth - width) / 2
            val dst = android.graphics.Rect(x, cursorY, x + width, cursorY + height)
            canvas.drawBitmap(bitmap, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
            // Врезка для reflow: тот же прямоугольник, нормализованный к странице —
            // ридер покажет её кропом запечённой PDF-страницы (см. FigureView).
            val figure =
                ReflowBlock.Figure(
                    pageIndex = pageNumber - 1,
                    bounds =
                        ReflowRect(
                            left = x.toFloat() / PAGE_WIDTH,
                            top = cursorY.toFloat() / PAGE_HEIGHT,
                            right = (x + width).toFloat() / PAGE_WIDTH,
                            bottom = (cursorY + height).toFloat() / PAGE_HEIGHT,
                        ),
                    aspectRatio = if (height > 0) width.toFloat() / height else 1f,
                )
            cursorY += height + IMAGE_GAP
            return figure
        }

        private fun rule() {
            if (cursorY + RULE_GAP > contentBottom) newPage()
            cursorY += RULE_GAP / 2
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFBBBBBB.toInt() }
            canvas.drawLine(MARGIN.toFloat(), cursorY.toFloat(), (PAGE_WIDTH - MARGIN).toFloat(), cursorY.toFloat(), paint)
            cursorY += RULE_GAP / 2
        }

        private fun newPage() {
            pdf.finishPage(page)
            pageNumber++
            page = startPage(pageNumber)
            canvas = page.canvas
            cursorY = MARGIN
        }

        private fun startPage(number: Int): PdfDocument.Page =
            pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, number).create())

        private fun markerFor(item: ContentBlock.ListItem): String = if (item.ordered) "${item.ordinal}. " else "•  "
    }
}
