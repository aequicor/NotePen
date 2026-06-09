package ru.kyamshanov.notepen.book

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Проверяет анти-zip-бомба примитив [readEntryBounded]: ограниченное чтение
 * записи прерывается [DocumentTooLargeException] на пороге, НЕ материализуя весь
 * раздутый поток. Это та защита, на которую опираются [EpubParser],
 * [ComicArchive] и [Fb2Parser], извлекая записи недоверенных архивов.
 *
 * Тест использует маленький явный лимит, чтобы воспроизвести отказ без
 * выделения буферов размером с реальные (десятки/сотни МБ) лимиты —
 * следовательно, без риска OOM в самом тесте.
 */
class BookLimitsTest {
    @Test
    fun `bounded read returns small entries unchanged`() {
        val payload = "hello".toByteArray(Charsets.UTF_8)
        val zip = zipOf("ok.txt" to payload)
        ZipInputStream(ByteArrayInputStream(zip)).use { stream ->
            stream.nextEntry
            val data = stream.readEntryBounded("ok.txt", maxEntryBytes = SMALL_CAP)
            assertEquals("hello", String(data, Charsets.UTF_8))
        }
    }

    @Test
    fun `bounded read aborts a highly compressible bomb past the cap`() {
        // 1 МБ нулей сжимается в считаные байты, но раздулся бы в 1 МБ при
        // наивном readBytes(). С лимитом 1 КБ чтение прерывается почти сразу.
        val zip = zipOf("bomb.bin" to ByteArray(ONE_MB))
        ZipInputStream(ByteArrayInputStream(zip)).use { stream ->
            stream.nextEntry
            assertFailsWith<DocumentTooLargeException> {
                stream.readEntryBounded("bomb.bin", maxEntryBytes = SMALL_CAP)
            }
        }
    }

    @Test
    fun `epub with too many entries is rejected, not OOM`() {
        // Тысячи крошечных записей дёшевы по памяти, но без лимита заставили бы
        // парсер удержать карту из десятков тысяч массивов.
        val bomb = zipWithManyTinyEntries(BookLimits.MAX_ENTRY_COUNT + 1, prefix = "f")
        assertFailsWith<DocumentTooLargeException> { EpubParser.parse(bomb) }
    }

    @Test
    fun `cbz with too many image entries is rejected, not OOM`() {
        val bomb = zipWithManyTinyEntries(BookLimits.MAX_COMIC_IMAGES + 1, prefix = "p", suffix = ".jpg")
        assertFailsWith<DocumentTooLargeException> { ComicArchive.extract(bomb, BookFormat.CBZ) }
    }

    private fun zipWithManyTinyEntries(
        count: Int,
        prefix: String,
        suffix: String = ".txt",
    ): ByteArray =
        ByteArrayOutputStream().also { bos ->
            ZipOutputStream(bos).use { zip ->
                repeat(count) { i ->
                    zip.putNextEntry(ZipEntry("$prefix$i$suffix"))
                    zip.write(i.toByte().toInt())
                    zip.closeEntry()
                }
            }
        }.toByteArray()

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray =
        ByteArrayOutputStream().also { bos ->
            ZipOutputStream(bos).use { zip ->
                for ((name, content) in entries) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content)
                    zip.closeEntry()
                }
            }
        }.toByteArray()

    private companion object {
        private const val SMALL_CAP = 1024L
        private const val ONE_MB = 1024 * 1024
    }
}
