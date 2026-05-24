package ru.kyamshanov.notepen.epub

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EpubParserTest {

    @Test
    fun `parses metadata and blocks in spine order`() {
        val book = EpubParser.parse(sampleEpubBytes())

        assertEquals("Sample Title", book.metadata.title)
        assertEquals("Jane Doe", book.metadata.author)
        assertEquals("en", book.metadata.language)

        assertTrue(book.blocks.any { it is EpubBlock.Heading && it.text == "Chapter One" })
        assertTrue(book.blocks.any { it is EpubBlock.Paragraph && it.text.contains("first paragraph") })
        assertTrue(book.blocks.any { it is EpubBlock.ListItem && it.text == "Item A" })
        assertTrue(book.blocks.contains(EpubBlock.PageBreak))
        assertTrue(book.blocks.any { it is EpubBlock.Heading && it.text == "Chapter Two" })
    }

    @Test
    fun `spine order is preserved across documents`() {
        val blocks = EpubParser.parse(sampleEpubBytes()).blocks
        val chapterOne = blocks.indexOfFirst { it is EpubBlock.Heading && it.text == "Chapter One" }
        val chapterTwo = blocks.indexOfFirst { it is EpubBlock.Heading && it.text == "Chapter Two" }
        assertTrue(chapterOne in 0 until chapterTwo)
    }

    @Test
    fun `rejects a zip that is not an epub`() {
        val notEpub = ByteArrayOutputStream().also { bos ->
            ZipOutputStream(bos).use { zip ->
                zip.putNextEntry(ZipEntry("hello.txt"))
                zip.write("hi".toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()

        val error = runCatching { EpubParser.parse(notEpub) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
}
