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
    ): List<TocEntry> {
        val pdf = PdfDocument()
        val composer = PageComposer(pdf)
        book.metadata.title
            ?.takeIf { it.isNotBlank() }
            ?.let { composer.heading(level = 1, text = it) }
        book.metadata.author
            ?.takeIf { it.isNotBlank() }
            ?.let { composer.paragraph(listOf(InlineSpan(it)), italic = true) }
        for (block in book.blocks) composer.render(block)
        composer.finish()

        output.parentFile?.mkdirs()
        try {
            FileOutputStream(output).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
        return composer.outline()
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

        fun finish() {
            pdf.finishPage(page)
        }

        fun outline(): List<TocEntry> = tocEntries.toList()

        fun render(block: ContentBlock) {
            when (block) {
                is ContentBlock.Heading -> heading(block.level, block.text)
                is ContentBlock.Paragraph -> paragraph(block.text, firstLineIndent = PARAGRAPH_FIRST_LINE_INDENT)
                is ContentBlock.Blockquote -> paragraph(block.text, italic = true, indent = BLOCKQUOTE_INDENT)
                is ContentBlock.ListItem ->
                    paragraph(listOf(InlineSpan(markerFor(block))) + block.text, indent = (block.level + 1) * LIST_INDENT_STEP)
                is ContentBlock.Table -> block.rows.forEach { row -> paragraph(listOf(InlineSpan(row.joinToString("   |   ")))) }
                is ContentBlock.Image -> image(block)
                ContentBlock.HorizontalRule -> rule()
                ContentBlock.PageBreak -> if (cursorY > MARGIN) newPage()
            }
        }

        fun heading(
            level: Int,
            text: String,
        ) {
            cursorY += HEADING_GAP_BEFORE
            val size = HEADING_SIZES[(level - 1).coerceIn(0, HEADING_SIZES.lastIndex)]
            if (cursorY + size * LINE_SPACING_MULT > contentBottom) newPage()
            tocEntries.add(TocEntry(level = level, title = text, pageIndex = pageNumber - 1))
            val paint = textPaint(size, Typeface.create(Typeface.SERIF, Typeface.BOLD), Color.BLACK)
            drawLayout(text, paint, indent = 0)
            cursorY += HEADING_GAP_AFTER
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
        ) {
            val typeface = Typeface.create(Typeface.SERIF, if (italic) Typeface.ITALIC else Typeface.NORMAL)
            val color = if (italic) QUOTE_COLOR else Color.BLACK
            drawLayout(spannableOf(spans), textPaint(BODY_SIZE, typeface, color), indent, firstLineIndent)
            cursorY += PARAGRAPH_GAP
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
        ) {
            val left = MARGIN + indent
            val width = (contentWidth - indent).coerceAtLeast(1)
            val builder =
                StaticLayout.Builder
                    .obtain(text, 0, text.length, paint, width)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, LINE_SPACING_MULT)
                    .setIncludePad(false)
            if (firstLineIndent > 0) builder.setIndents(intArrayOf(firstLineIndent, 0), null)
            drawLayoutPaginated(builder.build(), left)
        }

        private fun drawLayoutPaginated(
            layout: StaticLayout,
            left: Int,
        ) {
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
                cursorY += drawnHeight
                line = end
                if (line < layout.lineCount) newPage()
            }
        }

        private fun image(block: ContentBlock.Image) {
            val bitmap =
                runCatching { BitmapFactory.decodeByteArray(block.data, 0, block.data.size) }
                    .getOrNull() ?: return
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
            cursorY += height + IMAGE_GAP
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
