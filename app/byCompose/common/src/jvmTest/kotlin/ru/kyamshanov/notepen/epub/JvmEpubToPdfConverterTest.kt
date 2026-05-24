package ru.kyamshanov.notepen.epub

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.apache.pdfbox.Loader
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JvmEpubToPdfConverterTest {

    private val converter = JvmEpubToPdfConverter(Dispatchers.IO)

    @Test
    fun `detects epub by extension`() {
        assertTrue(converter.isEpub("/books/sample.epub"))
        assertFalse(converter.isEpub("/books/sample.pdf"))
    }

    @Test
    fun `converts epub to a loadable pdf`() = runTest {
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
    fun `reuses cache on repeated conversion`() = runTest {
        val epub = File.createTempFile("sample", ".epub").apply { writeBytes(sampleEpubBytes()) }
        try {
            val first = converter.ensurePdf(epub.absolutePath)
            val second = converter.ensurePdf(epub.absolutePath)
            assertEquals(first, second)
        } finally {
            epub.delete()
        }
    }
}
