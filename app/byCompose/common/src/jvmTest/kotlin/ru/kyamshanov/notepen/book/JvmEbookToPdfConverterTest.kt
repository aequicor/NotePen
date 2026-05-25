package ru.kyamshanov.notepen.book

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import ru.kyamshanov.notepen.reflow.JvmPdfReflowExtractor
import ru.kyamshanov.notepen.reflow.api.PdfContentKind
import ru.kyamshanov.notepen.reflow.api.ReflowBlock
import java.awt.image.BufferedImage
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JvmEpubToPdfConverterTest {
    private val converter = JvmEbookToPdfConverter(Dispatchers.IO)

    @Test
    fun `detects epub by extension`() {
        assertTrue(converter.canConvert("/books/sample.epub"))
        assertFalse(converter.canConvert("/books/sample.pdf"))
    }

    @Test
    fun `converts epub to a loadable pdf`() =
        runTest {
            val epub = File.createTempFile("sample", ".epub").apply { writeBytes(sampleEpubBytes()) }
            try {
                val pdfPath = converter.ensurePdf(epub.absolutePath)
                val pdf = File(pdfPath)
                assertTrue(pdf.exists() && pdf.length() > 0L, "converted PDF must be written")
                Loader.loadPDF(pdf).use { doc -> assertTrue(doc.numberOfPages >= 1) }
            } finally {
                epub.delete()
            }
        }

    @Test
    fun `reuses cache on repeated conversion`() =
        runTest {
            val epub = File.createTempFile("sample", ".epub").apply { writeBytes(sampleEpubBytes()) }
            try {
                val first = converter.ensurePdf(epub.absolutePath)
                val second = converter.ensurePdf(epub.absolutePath)
                assertEquals(first, second)
            } finally {
                epub.delete()
            }
        }

    @Test
    fun `outlineFor returns chapter headings`() =
        runTest {
            val epub = File.createTempFile("sample", ".epub").apply { writeBytes(sampleEpubBytes()) }
            try {
                val outline = converter.outlineFor(epub.absolutePath)
                assertTrue(outline.any { it.title == "Chapter One" }, "outline must include Chapter One")
                assertTrue(outline.any { it.title == "Chapter Two" }, "outline must include Chapter Two")
                assertTrue(outline.all { it.pageIndex >= 0 })
            } finally {
                epub.delete()
            }
        }

    // Один и тот же ведущий текст для абзаца и заголовка: при равном первом
    // символе разница левого края определяется ТОЛЬКО красной строкой, а не
    // боковыми полусветами разных глифов. Короткий — умещается в одну строку.
    private val singleLineText = "Word"

    @Test
    fun `body paragraph is first-line indented but heading is flush left`() {
        val paragraphLeft = leftmostInk(ContentBlock.Paragraph(listOf(InlineSpan(singleLineText))))
        val headingLeft = leftmostInk(ContentBlock.Heading(level = 2, text = singleLineText))
        // Абзац получает красную строку (1.5 кегля ≈ 45px @150dpi), заголовок —
        // нет. Порог берём с запасом ниже втяжки, но выше различий полусветов
        // полужирного и обычного начертаний (единицы пикселей).
        val delta = paragraphLeft - headingLeft
        assertTrue(
            delta > 20,
            "paragraph must be first-line indented vs flush heading (paragraph=$paragraphLeft, heading=$headingLeft)",
        )
    }

    // Кириллический заголовок и абзац с жирным/курсивным фрагментом: проверяем,
    // что десктопный рендерер кладёт НАСТОЯЩИЙ векторный текстовый слой (а не
    // растр), — тогда reflow-извлечение классифицирует PDF как TEXT_BASED и
    // вытаскивает текст со словами, разделёнными пробелами. Это и есть гарантия
    // режима чтения и чёткого текста на HiDPI.
    @Test
    fun `rendered vector text is extractable for reflow with spaces between words`() =
        runTest {
            val headingText = "Глава первая"
            val book =
                BookContent(
                    metadata = BookMetadata(),
                    blocks =
                        listOf(
                            ContentBlock.Heading(level = 1, text = headingText),
                            ContentBlock.Paragraph(
                                listOf(
                                    InlineSpan("Это "),
                                    InlineSpan("важный", bold = true),
                                    InlineSpan(" и "),
                                    InlineSpan("курсивный", italic = true),
                                    InlineSpan(" текст абзаца."),
                                ),
                            ),
                        ),
                )
            val pdf = File.createTempFile("vector", ".pdf")
            try {
                JvmBookPdfRenderer.render(book, pdf)

                val document = JvmPdfReflowExtractor(StandardTestDispatcher(testScheduler)).extract(pdf.absolutePath)

                assertEquals(PdfContentKind.TEXT_BASED, document.kind, "vector text layer must be extractable")
                val heading = document.blocks.filterIsInstance<ReflowBlock.Heading>().joinToString(" ") { it.text }
                val body = document.blocks.filterIsInstance<ReflowBlock.Paragraph>().joinToString(" ") { it.text }
                assertTrue(heading.contains(headingText), "heading text must survive (was: '$heading')")
                // Слова абзаца с пробелами между ними — доказывает, что текстовый
                // слой реальный, а границы слов сохранены.
                assertTrue(body.contains("важный и курсивный"), "paragraph words must be spaced (was: '$body')")
                assertTrue(body.contains("текст абзаца"), "paragraph tail must survive (was: '$body')")
            } finally {
                pdf.delete()
            }
        }

    /**
     * Рендерит книгу из одного блока в PDF, растрирует первую страницу в родном
     * разрешении растра рендерера (150 DPI) и возвращает левый край самого
     * левого закрашенного пикселя страницы — для однострочного блока это левый
     * край его единственной строки.
     */
    private fun leftmostInk(block: ContentBlock): Int {
        val pdf = File.createTempFile("indent", ".pdf")
        try {
            JvmBookPdfRenderer.render(BookContent(BookMetadata(), listOf(block)), pdf)
            Loader.loadPDF(pdf).use { doc ->
                val image = PDFRenderer(doc).renderImageWithDPI(0, RENDER_DPI)
                return leftmostInkColumn(image)
            }
        } finally {
            pdf.delete()
        }
    }

    /** Минимальный x закрашенного пикселя на всём растре (или ширина, если пусто). */
    private fun leftmostInkColumn(image: BufferedImage): Int {
        for (x in 0 until image.width) {
            for (y in 0 until image.height) {
                if (isInk(image.getRGB(x, y))) return x
            }
        }
        return image.width
    }

    // Пиксель считаем «чернилами», если он заметно темнее белого фона.
    private fun isInk(rgb: Int): Boolean {
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        return r + g + b < INK_THRESHOLD
    }

    private companion object {
        private const val INK_THRESHOLD = 600 // < ~200 на канал = тёмный пиксель
        private const val RENDER_DPI = 150f
    }
}
