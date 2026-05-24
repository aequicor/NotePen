package ru.kyamshanov.notepen.epub

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Собирает минимальный, но валидный EPUB в память: `mimetype`,
 * `META-INF/container.xml`, OPF с двумя документами spine и две XHTML-главы
 * (заголовки, абзацы, список). Используется тестами парсера и конвертера.
 */
internal fun sampleEpubBytes(): ByteArray {
    val container =
        """
        <?xml version="1.0"?>
        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
          <rootfiles>
            <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
          </rootfiles>
        </container>
        """.trimIndent()

    val opf =
        """
        <?xml version="1.0"?>
        <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
            <dc:title>Sample Title</dc:title>
            <dc:creator>Jane Doe</dc:creator>
            <dc:language>en</dc:language>
            <dc:identifier id="bookid">urn:uuid:1234</dc:identifier>
          </metadata>
          <manifest>
            <item id="c1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
            <item id="c2" href="ch2.xhtml" media-type="application/xhtml+xml"/>
          </manifest>
          <spine>
            <itemref idref="c1"/>
            <itemref idref="c2"/>
          </spine>
        </package>
        """.trimIndent()

    val ch1 =
        """
        <?xml version="1.0" encoding="utf-8"?>
        <html xmlns="http://www.w3.org/1999/xhtml"><body>
          <h1>Chapter One</h1>
          <p>This is the first paragraph.</p>
          <ul><li>Item A</li><li>Item B</li></ul>
        </body></html>
        """.trimIndent()

    val ch2 =
        """
        <?xml version="1.0" encoding="utf-8"?>
        <html xmlns="http://www.w3.org/1999/xhtml"><body>
          <h1>Chapter Two</h1>
          <p>Second chapter text.</p>
        </body></html>
        """.trimIndent()

    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        zip.putNextEntry(ZipEntry("mimetype"))
        zip.write("application/epub+zip".toByteArray(Charsets.US_ASCII))
        zip.closeEntry()
        fun put(name: String, content: String) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        put("META-INF/container.xml", container)
        put("OEBPS/content.opf", opf)
        put("OEBPS/ch1.xhtml", ch1)
        put("OEBPS/ch2.xhtml", ch2)
    }
    return out.toByteArray()
}
