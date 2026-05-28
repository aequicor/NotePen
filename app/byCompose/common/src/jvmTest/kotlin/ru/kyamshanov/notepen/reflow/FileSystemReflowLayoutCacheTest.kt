package ru.kyamshanov.notepen.reflow

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import ru.kyamshanov.notepen.reflow.ui.CachedLayout
import ru.kyamshanov.notepen.reflow.ui.LayoutCacheKey
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileSystemReflowLayoutCacheTest {
    private fun key(
        docFp: Long = 0x1234abcdL,
        widthPx: Int = 1080,
        font: String = "serif",
        fontSize: Float = 16f,
    ): LayoutCacheKey =
        LayoutCacheKey(
            docFingerprint = docFp,
            contentWidthPx = widthPx,
            fontFamilyId = font,
            fontSizeSp = fontSize,
            lineHeightMultiplier = 1.5f,
            letterSpacingSp = 0f,
            wordSpacingSp = 0f,
            hyphenation = true,
            align = "JUSTIFY",
            bionic = false,
            columnChars = 60,
            contentPaddingDp = 16f,
        )

    private val sampleLayout =
        CachedLayout(
            textHeights = mapOf(0 to 60, 1 to 120, 5 to 240),
            textLineBottoms =
                mapOf(
                    1 to listOf(60f, 120f),
                    5 to listOf(60f, 120f, 180f, 240f),
                ),
            figureHeights = mapOf(3 to 800, 7 to 1200),
        )

    @Test
    fun missOnEmptyCache() =
        runTest {
            val dir = Files.createTempDirectory("reflow-layout-cache").toFile()
            val cache = FileSystemReflowLayoutCache(ioDispatcher = Dispatchers.Unconfined, cacheDir = dir)
            assertNull(cache.read(key()))
        }

    @Test
    fun writeThenReadReturnsSameLayout() =
        runTest {
            val dir = Files.createTempDirectory("reflow-layout-cache").toFile()
            val cache = FileSystemReflowLayoutCache(ioDispatcher = Dispatchers.Unconfined, cacheDir = dir)
            val k = key()
            cache.write(k, sampleLayout)
            assertEquals(sampleLayout, cache.read(k))
        }

    @Test
    fun differentDocFingerprintsAreIndependent() =
        runTest {
            val dir = Files.createTempDirectory("reflow-layout-cache").toFile()
            val cache = FileSystemReflowLayoutCache(ioDispatcher = Dispatchers.Unconfined, cacheDir = dir)
            cache.write(key(docFp = 0x1L), sampleLayout)
            assertNull(cache.read(key(docFp = 0x2L)))
        }

    @Test
    fun differentTypographyKeysAreIndependent() =
        runTest {
            val dir = Files.createTempDirectory("reflow-layout-cache").toFile()
            val cache = FileSystemReflowLayoutCache(ioDispatcher = Dispatchers.Unconfined, cacheDir = dir)
            cache.write(key(widthPx = 1000), sampleLayout)
            assertNull(cache.read(key(widthPx = 1200)))
            assertNull(cache.read(key(font = "sans")))
            assertNull(cache.read(key(fontSize = 18f)))
        }

    @Test
    fun corruptedFileTreatedAsMiss() =
        runTest {
            val dir = Files.createTempDirectory("reflow-layout-cache").toFile()
            val cache = FileSystemReflowLayoutCache(ioDispatcher = Dispatchers.Unconfined, cacheDir = dir)
            val k = key()
            cache.write(k, sampleLayout)
            val file = dir.listFiles().orEmpty().single { it.name.endsWith(".layout.bin") }
            file.writeBytes(ByteArray(4) { 0 })
            assertNull(cache.read(k))
            assertTrue(!file.exists())
        }
}
