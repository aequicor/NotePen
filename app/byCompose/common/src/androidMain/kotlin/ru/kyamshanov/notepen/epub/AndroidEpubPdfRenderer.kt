package ru.kyamshanov.notepen.epub

import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import java.io.FileOutputStream

/**
 * Верстает [EpubBook] в PDF на Android.
 *
 * Текст рисуется как настоящий векторный текст PDF (через [Canvas.drawText]
 * силами [StaticLayout]) с использованием системных шрифтов — кириллица
 * поддерживается из коробки, файл компактный, текст чёткий при любом зуме.
 */
object AndroidEpubPdfRenderer {

    private const val PAGE_WIDTH = 595 // A4 in points (1/72")
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 54

    private const val BODY_SIZE = 13f
    private const val BLOCKQUOTE_INDENT = 28
    private const val LIST_INDENT_STEP = 22
    private const val LINE_SPACING_MULT = 1.25f
    private const val PARAGRAPH_GAP = 8
    private const val HEADING_GAP_BEFORE = 14
    private const val HEADING_GAP_AFTER = 6
    private const val RULE_GAP = 14
    private const val IMAGE_GAP = 10
    private const val QUOTE_COLOR = 0xFF444444.toInt()

    private val HEADING_SIZES = floatArrayOf(26f, 22f, 19f, 17f, 15f, 14f)

    /**
     * @param book книга для верстки
     * @param output файл назначения PDF (создаётся/перезаписывается)
     */
    fun render(book: EpubBook, output: File) {
        val pdf = PdfDocument()
        val composer = PageComposer(pdf)
        book.metadata.title?.takeIf { it.isNotBlank() }?.let { composer.heading(level = 1, text = it) }
        book.metadata.author?.takeIf { it.isNotBlank() }?.let { composer.paragraph(it, italic = true) }
        for (block in book.blocks) composer.render(block)
        composer.finish()

        output.parentFile?.mkdirs()
        try {
            FileOutputStream(output).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
    }

    /** Накопитель страниц PDF с курсором верстки сверху вниз. */
    private class PageComposer(private val pdf: PdfDocument) {
        private val contentWidth = PAGE_WIDTH - 2 * MARGIN
        private val contentBottom = PAGE_HEIGHT - MARGIN
        private var pageNumber = 1
        private var page = startPage(pageNumber)
        private var canvas: Canvas = page.canvas
        private var cursorY = MARGIN

        fun finish() {
            pdf.finishPage(page)
        }

        fun render(block: EpubBlock) {
            when (block) {
                is EpubBlock.Heading -> heading(block.level, block.text)
                is EpubBlock.Paragraph -> paragraph(block.text)
                is EpubBlock.Blockquote -> paragraph(block.text, italic = true, indent = BLOCKQUOTE_INDENT)
                is EpubBlock.ListItem ->
                    paragraph(markerFor(block) + block.text, indent = (block.level + 1) * LIST_INDENT_STEP)
                is EpubBlock.Table -> block.rows.forEach { row -> paragraph(row.joinToString("   |   ")) }
                is EpubBlock.Image -> image(block)
                EpubBlock.HorizontalRule -> rule()
                EpubBlock.PageBreak -> if (cursorY > MARGIN) newPage()
            }
        }

        fun heading(level: Int, text: String) {
            cursorY += HEADING_GAP_BEFORE
            val size = HEADING_SIZES[(level - 1).coerceIn(0, HEADING_SIZES.lastIndex)]
            val paint = textPaint(size, Typeface.create(Typeface.SERIF, Typeface.BOLD), Color.BLACK)
            drawLayout(text, paint, indent = 0)
            cursorY += HEADING_GAP_AFTER
        }

        fun paragraph(text: String, italic: Boolean = false, indent: Int = 0) {
            val typeface = Typeface.create(Typeface.SERIF, if (italic) Typeface.ITALIC else Typeface.NORMAL)
            val color = if (italic) QUOTE_COLOR else Color.BLACK
            drawLayout(text, textPaint(BODY_SIZE, typeface, color), indent)
            cursorY += PARAGRAPH_GAP
        }

        private fun textPaint(size: Float, typeface: Typeface, color: Int): TextPaint =
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                this.textSize = size
                this.typeface = typeface
                this.color = color
                isSubpixelText = true
            }

        private fun drawLayout(text: String, paint: TextPaint, indent: Int) {
            val left = MARGIN + indent
            val width = (contentWidth - indent).coerceAtLeast(1)
            val layout = StaticLayout.Builder
                .obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, LINE_SPACING_MULT)
                .setIncludePad(false)
                .build()
            drawLayoutPaginated(layout, left)
        }

        private fun drawLayoutPaginated(layout: StaticLayout, left: Int) {
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

        private fun image(block: EpubBlock.Image) {
            val bitmap = runCatching { BitmapFactory.decodeByteArray(block.data, 0, block.data.size) }
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

        private fun markerFor(item: EpubBlock.ListItem): String =
            if (item.ordered) "${item.ordinal}. " else "•  "
    }
}
