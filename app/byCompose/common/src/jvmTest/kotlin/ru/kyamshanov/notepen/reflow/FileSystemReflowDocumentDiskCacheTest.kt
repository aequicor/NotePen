package ru.kyamshanov.notepen.reflow

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import ru.kyamshanov.notepen.reflow.api.PdfContentKind
import ru.kyamshanov.notepen.reflow.api.REFLOW_PARSER_VERSION
import ru.kyamshanov.notepen.reflow.api.ReflowBlock
import ru.kyamshanov.notepen.reflow.api.ReflowDocument
import ru.kyamshanov.notepen.reflow.api.ReflowRect
import ru.kyamshanov.notepen.reflow.api.SourceSpan
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileSystemReflowDocumentDiskCacheTest {
    private val docA =
        ReflowDocument(
            kind = PdfContentKind.TEXT_BASED,
            blocks =
                listOf(
                    ReflowBlock.Heading("H", 1),
                    ReflowBlock.Paragraph(
                        "p",
                        listOf(SourceSpan(0, 0, 1, ReflowRect(0f, 0f, 1f, 1f), bold = true)),
                    ),
                    ReflowBlock.Divider,
                ),
        )

    private fun newCache(
        dir: File,
        statByPath: Map<String, SourceStat?>,
    ) = FileSystemReflowDocumentDiskCache(
        ioDispatcher = Dispatchers.Unconfined,
        cacheDir = dir,
        stat = { statByPath[it] },
    )

    @Test
    fun missWhenNotWritten() =
        runTest {
            val dir = Files.createTempDirectory("reflow-cache").toFile()
            val cache =
                newCache(
                    dir = dir,
                    statByPath = mapOf("/x.pdf" to SourceStat(10L, 20L)),
                )
            assertNull(cache.read("/x.pdf"))
        }

    @Test
    fun writeThenReadReturnsSameDocument() =
        runTest {
            val dir = Files.createTempDirectory("reflow-cache").toFile()
            val cache =
                newCache(
                    dir = dir,
                    statByPath = mapOf("/x.pdf" to SourceStat(10L, 20L)),
                )
            cache.write("/x.pdf", docA)
            assertEquals(docA, cache.read("/x.pdf"))
        }

    @Test
    fun differentPathsDoNotShare() =
        runTest {
            val dir = Files.createTempDirectory("reflow-cache").toFile()
            val cache =
                newCache(
                    dir = dir,
                    statByPath =
                        mapOf(
                            "/x.pdf" to SourceStat(10L, 20L),
                            "/y.pdf" to SourceStat(30L, 40L),
                        ),
                )
            cache.write("/x.pdf", docA)
            assertEquals(docA, cache.read("/x.pdf"))
            assertNull(cache.read("/y.pdf"))
        }

    @Test
    fun sizeChangeInvalidatesEntry() =
        runTest {
            val dir = Files.createTempDirectory("reflow-cache").toFile()
            val writingCache =
                newCache(
                    dir = dir,
                    statByPath = mapOf("/x.pdf" to SourceStat(10L, 20L)),
                )
            writingCache.write("/x.pdf", docA)
            val readingCache =
                newCache(
                    dir = dir,
                    statByPath = mapOf("/x.pdf" to SourceStat(11L, 20L)),
                )
            assertNull(readingCache.read("/x.pdf"))
            // stale file deleted
            assertEquals(0, dir.listFiles().orEmpty().count { it.name.endsWith(".reflow.bin") })
        }

    @Test
    fun mtimeChangeInvalidatesEntry() =
        runTest {
            val dir = Files.createTempDirectory("reflow-cache").toFile()
            val writingCache =
                newCache(
                    dir = dir,
                    statByPath = mapOf("/x.pdf" to SourceStat(10L, 20L)),
                )
            writingCache.write("/x.pdf", docA)
            val readingCache =
                newCache(
                    dir = dir,
                    statByPath = mapOf("/x.pdf" to SourceStat(10L, 21L)),
                )
            assertNull(readingCache.read("/x.pdf"))
        }

    @Test
    fun parserVersionChangeInvalidatesEntry() =
        // F-9: кэш, разобранный старой версией парсера, должен браковаться даже
        // при неизменных size/mtime исходника — иначе фикс эвристик (напр. F-8
        // TableNoiseGuard) был бы невидим для уже открытой ранее книги.
        runTest {
            val dir = Files.createTempDirectory("reflow-cache").toFile()
            val cache =
                newCache(
                    dir = dir,
                    statByPath = mapOf("/x.pdf" to SourceStat(10L, 20L)),
                )
            cache.write("/x.pdf", docA)
            // Перезаписываем кэш так, будто его создал парсер ПРЕДЫДУЩЕЙ версии.
            val file = dir.listFiles().orEmpty().single { it.name.endsWith(".reflow.bin") }
            file.outputStream().use {
                ReflowBinaryFormat.write(docA, 10L, 20L, it, parserVersion = REFLOW_PARSER_VERSION - 1)
            }
            assertNull(cache.read("/x.pdf"))
            // Устаревший по версии парсера файл удалён.
            assertTrue(!file.exists())
        }

    @Test
    fun nullStatSkipsReadAndWrite() =
        runTest {
            val dir = Files.createTempDirectory("reflow-cache").toFile()
            val cache =
                newCache(
                    dir = dir,
                    statByPath = mapOf("/x.pdf" to null),
                )
            cache.write("/x.pdf", docA)
            assertTrue(dir.listFiles().orEmpty().none { it.name.endsWith(".reflow.bin") })
            assertNull(cache.read("/x.pdf"))
        }

    @Test
    fun corruptedFileTreatedAsMiss() =
        runTest {
            val dir = Files.createTempDirectory("reflow-cache").toFile()
            val cache =
                newCache(
                    dir = dir,
                    statByPath = mapOf("/x.pdf" to SourceStat(10L, 20L)),
                )
            cache.write("/x.pdf", docA)
            // Corrupt the file.
            val file =
                dir.listFiles().orEmpty().single { it.name.endsWith(".reflow.bin") }
            file.writeBytes(ByteArray(8) { 0 })
            assertNull(cache.read("/x.pdf"))
            // Corrupted file deleted.
            assertTrue(!file.exists())
        }
}
