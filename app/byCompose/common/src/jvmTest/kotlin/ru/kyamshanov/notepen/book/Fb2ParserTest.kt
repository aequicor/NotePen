package ru.kyamshanov.notepen.book

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Fb2ParserTest {
    @Test
    fun `parses metadata, structure, inline styling and image`() {
        val book = Fb2Parser.parse(sampleFb2().toByteArray(Charsets.UTF_8))

        assertEquals("Война и мир", book.metadata.title)
        assertEquals("Лев Толстой", book.metadata.author)
        assertEquals("ru", book.metadata.language)
        assertEquals("fb2-123", book.metadata.identifier)

        assertTrue(book.blocks.any { it is ContentBlock.Heading && it.text == "Глава первая" })

        val para =
            book.blocks.filterIsInstance<ContentBlock.Paragraph>()
                .first { it.text.plainText().contains("Обычный текст") }
        assertTrue(para.text.any { it.italic && it.text.trim() == "курсивом" })
        assertTrue(para.text.any { it.bold && it.text.trim() == "жирным" })

        assertTrue(book.blocks.any { it is ContentBlock.Blockquote && it.text.plainText().contains("Цитата") })
        assertTrue(book.blocks.any { it is ContentBlock.Image && it.data.isNotEmpty() })
    }

    @Test
    fun `inserts page break between top-level sections`() {
        val blocks = Fb2Parser.parse(sampleFb2().toByteArray(Charsets.UTF_8)).blocks
        val first = blocks.indexOfFirst { it is ContentBlock.Heading && it.text == "Глава первая" }
        val second = blocks.indexOfFirst { it is ContentBlock.Heading && it.text == "Глава вторая" }
        assertTrue(first in 0 until second)
        assertTrue(blocks.contains(ContentBlock.PageBreak))
    }

    @Test
    fun `decodes declared windows-1251 charset`() {
        val xml =
            "<?xml version=\"1.0\" encoding=\"windows-1251\"?>" +
                "<FictionBook><description><title-info><book-title>Тест</book-title></title-info></description>" +
                "<body><section><p>Текст главы</p></section></body></FictionBook>"
        val book = Fb2Parser.parse(xml.toByteArray(charset("windows-1251")))
        assertEquals("Тест", book.metadata.title)
        assertTrue(book.blocks.any { it is ContentBlock.Paragraph && it.text.plainText() == "Текст главы" })
    }

    @Test
    fun `unwraps fb2 inside a zip container`() {
        val zipped =
            ByteArrayOutputStream().also { bos ->
                ZipOutputStream(bos).use { zip ->
                    zip.putNextEntry(ZipEntry("book.fb2"))
                    zip.write(sampleFb2().toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }.toByteArray()
        assertEquals("Война и мир", Fb2Parser.parse(zipped).metadata.title)
    }

    private fun sampleFb2(): String =
        """
        <?xml version="1.0" encoding="utf-8"?>
        <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0" xmlns:l="http://www.w3.org/1999/xlink">
          <description>
            <title-info>
              <author><first-name>Лев</first-name><last-name>Толстой</last-name></author>
              <book-title>Война и мир</book-title>
              <lang>ru</lang>
            </title-info>
            <document-info><id>fb2-123</id></document-info>
          </description>
          <body>
            <title><p>Война и мир</p></title>
            <section>
              <title><p>Глава первая</p></title>
              <p>Обычный текст с <emphasis>курсивом</emphasis> и <strong>жирным</strong>.</p>
              <cite><p>Цитата здесь.</p></cite>
              <image l:href="#img1"/>
            </section>
            <section>
              <title><p>Глава вторая</p></title>
              <p>Второй раздел.</p>
            </section>
          </body>
          <binary id="img1" content-type="image/png">SGVsbG8=</binary>
        </FictionBook>
        """.trimIndent()
}
