package ru.kyamshanov.notepen.epub

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO

/**
 * Верстает [EpubBook] в PDF на JVM.
 *
 * Текст рисуется средствами Java2D в растровые страницы (логический шрифт
 * `Serif` покрывает кириллицу без встраивания TTF), которые затем
 * вкладываются в PDF через PDFBox. Это сознательный компромисс: в отличие от
 * Android, на десктопе у нас нет встроенного Unicode-шрифта для векторного
 * текста PDFBox, поэтому страницы растрируются (текст по-прежнему чёткий при
 * 150 DPI).
 */
object JvmEpubPdfRenderer {

    private const val PAGE_WIDTH = 1240 // A4 @150dpi
    private const val PAGE_HEIGHT = 1754
    private const val MARGIN = 104

    private const val BODY_SIZE = 30f
    private const val BLOCKQUOTE_INDENT = 64
    private const val LIST_INDENT_STEP = 40
    private const val LINE_FACTOR = 1.42f
    private const val PARAGRAPH_GAP = 18
    private const val HEADING_GAP_BEFORE = 30
    private const val HEADING_GAP_AFTER = 12
    private const val RULE_GAP = 26
    private const val IMAGE_GAP = 22

    private val HEADING_SIZES = floatArrayOf(58f, 48f, 42f, 38f, 34f, 32f)

    /**
     * @param book книга для верстки
     * @param output файл назначения PDF (создаётся/перезаписывается)
     */
    fun render(book: EpubBook, output: File) {
        val composer = PageComposer()
        book.metadata.title?.takeIf { it.isNotBlank() }?.let { composer.heading(level = 1, text = it) }
        book.metadata.author?.takeIf { it.isNotBlank() }?.let { composer.paragraph(it, italic = true) }
        for (block in book.blocks) composer.render(block)
        val pages = composer.finish()

        PDDocument().use { doc ->
            for (image in pages) {
                val page = PDPage(PDRectangle.A4)
                doc.addPage(page)
                val xObject = LosslessFactory.createFromImage(doc, image)
                PDPageContentStream(doc, page).use { stream ->
                    stream.drawImage(xObject, 0f, 0f, PDRectangle.A4.width, PDRectangle.A4.height)
                }
            }
            if (doc.numberOfPages == 0) doc.addPage(PDPage(PDRectangle.A4))
            output.parentFile?.mkdirs()
            doc.save(output)
        }
    }

    /** Накопитель страниц с курсором верстки сверху вниз. */
    private class PageComposer {
        private val pages = mutableListOf<BufferedImage>()
        private val contentBottom = PAGE_HEIGHT - MARGIN
        private val contentWidth = PAGE_WIDTH - 2 * MARGIN
        private lateinit var graphics: Graphics2D
        private var cursorY = MARGIN

        init {
            newPage()
        }

        fun finish(): List<BufferedImage> {
            graphics.dispose()
            return pages
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
            drawText(text, Font(Font.SERIF, Font.BOLD, size.toInt()), Color.BLACK, indent = 0)
            cursorY += HEADING_GAP_AFTER
        }

        fun paragraph(text: String, italic: Boolean = false, indent: Int = 0) {
            val style = if (italic) Font.ITALIC else Font.PLAIN
            val color = if (italic) Color(0x44, 0x44, 0x44) else Color.BLACK
            drawText(text, Font(Font.SERIF, style, BODY_SIZE.toInt()), color, indent)
            cursorY += PARAGRAPH_GAP
        }

        private fun drawText(text: String, font: Font, color: Color, indent: Int) {
            graphics.font = font
            graphics.color = color
            val metrics = graphics.fontMetrics
            val lineHeight = (font.size * LINE_FACTOR).toInt()
            val left = MARGIN + indent
            val maxWidth = contentWidth - indent
            for (line in wrap(text, maxWidth)) {
                if (cursorY + lineHeight > contentBottom) newPage()
                graphics.font = font
                graphics.color = color
                graphics.drawString(line, left, cursorY + metrics.ascent)
                cursorY += lineHeight
            }
        }

        private fun wrap(text: String, maxWidth: Int): List<String> {
            val metrics = graphics.fontMetrics
            val lines = mutableListOf<String>()
            for (rawLine in text.split('\n')) {
                var current = StringBuilder()
                for (word in rawLine.split(' ').filter { it.isNotEmpty() }) {
                    val candidate = if (current.isEmpty()) word else "$current $word"
                    if (metrics.stringWidth(candidate) <= maxWidth || current.isEmpty()) {
                        current = StringBuilder(candidate)
                    } else {
                        lines.add(current.toString())
                        current = StringBuilder(word)
                    }
                }
                lines.add(current.toString())
            }
            return lines
        }

        private fun image(block: EpubBlock.Image) {
            val decoded = runCatching { ImageIO.read(ByteArrayInputStream(block.data)) }.getOrNull() ?: return
            val maxHeight = contentBottom - MARGIN
            var width = decoded.width
            var height = decoded.height
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
            graphics.drawImage(decoded, x, cursorY, width, height, null)
            cursorY += height + IMAGE_GAP
        }

        private fun rule() {
            if (cursorY + RULE_GAP > contentBottom) newPage()
            graphics.color = Color(0xBB, 0xBB, 0xBB)
            cursorY += RULE_GAP / 2
            graphics.drawLine(MARGIN, cursorY, PAGE_WIDTH - MARGIN, cursorY)
            cursorY += RULE_GAP / 2
        }

        private fun newPage() {
            if (::graphics.isInitialized) graphics.dispose()
            val image = BufferedImage(PAGE_WIDTH, PAGE_HEIGHT, BufferedImage.TYPE_INT_RGB)
            graphics = image.createGraphics().apply {
                setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                color = Color.WHITE
                fillRect(0, 0, PAGE_WIDTH, PAGE_HEIGHT)
            }
            pages.add(image)
            cursorY = MARGIN
        }

        private fun markerFor(item: EpubBlock.ListItem): String =
            if (item.ordered) "${item.ordinal}. " else "•  "
    }
}
