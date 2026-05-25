package ru.kyamshanov.notepen.book

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class ComicArchiveTest {

    @Test
    fun `extracts cbz images in natural page order, skipping non-images`() {
        val cbz = cbzOf(
            "page10.jpg" to "J",
            "page2.jpg" to "B",
            "page1.jpg" to "A",
            "notes.txt" to "ignored",
        )
        val pages = ComicArchive.extract(cbz, BookFormat.CBZ).map { String(it, Charsets.UTF_8) }
        assertEquals(listOf("A", "B", "J"), pages)
    }

    private fun cbzOf(vararg entries: Pair<String, String>): ByteArray =
        ByteArrayOutputStream().also { bos ->
            ZipOutputStream(bos).use { zip ->
                for ((name, content) in entries) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }
        }.toByteArray()
}
