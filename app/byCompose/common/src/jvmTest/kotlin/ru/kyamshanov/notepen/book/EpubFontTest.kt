package ru.kyamshanov.notepen.book

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EpubFontTest {
    @Test
    fun `deobfuscates an IDPF-obfuscated font back to the original`() {
        val idpfPrefix = 1040 // IDPF obfuscates the first 1040 bytes of the font
        val original = ByteArray(idpfPrefix + 160) { (it % 251).toByte() } // crosses the prefix boundary
        val identifier = "urn:uuid:deadbeef-0000-1111-2222-333344445555"
        val key = MessageDigest.getInstance("SHA-1").digest(identifier.toByteArray(Charsets.UTF_8))
        val obfuscated =
            original.copyOf().also { bytes ->
                for (i in 0 until minOf(idpfPrefix, bytes.size)) {
                    bytes[i] = (bytes[i].toInt() xor key[i % key.size].toInt()).toByte()
                }
            }

        val fonts = EpubParser.parse(buildObfuscatedEpub(identifier, obfuscated)).fonts

        assertEquals(1, fonts.size)
        assertTrue(original.contentEquals(fonts.first()), "font must be deobfuscated back to the original bytes")
    }

    private fun buildObfuscatedEpub(
        identifier: String,
        fontBytes: ByteArray,
    ): ByteArray {
        val container =
            """
            <?xml version="1.0"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
            </container>
            """.trimIndent()
        val opf =
            """
            <?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>T</dc:title>
                <dc:identifier id="bookid">$identifier</dc:identifier>
              </metadata>
              <manifest>
                <item id="c1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
                <item id="f1" href="fonts/f.otf" media-type="application/vnd.ms-opentype"/>
              </manifest>
              <spine><itemref idref="c1"/></spine>
            </package>
            """.trimIndent()
        val ch1 = "<?xml version=\"1.0\"?><html xmlns=\"http://www.w3.org/1999/xhtml\"><body><p>x</p></body></html>"
        val encryption =
            """
            <?xml version="1.0"?>
            <encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <enc:EncryptedData xmlns:enc="http://www.w3.org/2001/04/xmlenc#">
                <enc:EncryptionMethod Algorithm="http://www.idpf.org/2008/embedding"/>
                <enc:CipherData><enc:CipherReference URI="OEBPS/fonts/f.otf"/></enc:CipherData>
              </enc:EncryptedData>
            </encryption>
            """.trimIndent()

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(
                name: String,
                bytes: ByteArray,
            ) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
            put("mimetype", "application/epub+zip".toByteArray(Charsets.US_ASCII))
            put("META-INF/container.xml", container.toByteArray(Charsets.UTF_8))
            put("META-INF/encryption.xml", encryption.toByteArray(Charsets.UTF_8))
            put("OEBPS/content.opf", opf.toByteArray(Charsets.UTF_8))
            put("OEBPS/ch1.xhtml", ch1.toByteArray(Charsets.UTF_8))
            put("OEBPS/fonts/f.otf", fontBytes)
        }
        return out.toByteArray()
    }
}
