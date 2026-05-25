package ru.kyamshanov.notepen.book

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.apache.pdfbox.Loader
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
}
