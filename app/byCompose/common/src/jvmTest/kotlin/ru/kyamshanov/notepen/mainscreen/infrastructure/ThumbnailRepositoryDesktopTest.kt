package ru.kyamshanov.notepen.mainscreen.infrastructure

import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Тесты [ThumbnailRepositoryDesktop].
 * TC-47 (AC-22): инвалидация кеша при изменённом mtime
 */
class ThumbnailRepositoryDesktopTest {

    private lateinit var tmpDir: File
    private lateinit var repository: ThumbnailRepositoryDesktop

    @BeforeTest
    fun setUp() {
        tmpDir = createTempDir("notepen-test-thumbs")
        repository = ThumbnailRepositoryDesktop(cacheDir = tmpDir)
    }

    @AfterTest
    fun tearDown() {
        tmpDir.deleteRecursively()
    }

    // TC-47: put with mtime, get with same mtime returns data
    @Test
    fun `put then get with same mtime returns cached data`() = runTest {
        val uri = "/tmp/test.pdf"
        val data = byteArrayOf(1, 2, 3)
        repository.put(uri, data, fileMtime = 100L)

        val result = repository.get(uri, currentFileMtime = 100L)
        assertNotNull(result)
        assertTrue(result.contentEquals(data))
    }

    // TC-47 (AC-22): get with different mtime → cache invalidated → null
    @Test
    fun `get with different mtime invalidates cache and returns null`() = runTest {
        val uri = "/tmp/mtime_test.pdf"
        val data = byteArrayOf(4, 5, 6)
        repository.put(uri, data, fileMtime = 100L)

        val result = repository.get(uri, currentFileMtime = 200L)
        assertNull(result)
    }

    // get without mtime check when nothing cached → null
    @Test
    fun `get for uncached uri returns null`() = runTest {
        val result = repository.get("/tmp/not_cached.pdf", currentFileMtime = null)
        assertNull(result)
    }

    // totalSizeBytes grows after put
    @Test
    fun `totalSizeBytes increases after put`() = runTest {
        val before = repository.totalSizeBytes()
        repository.put("/tmp/size_test.pdf", ByteArray(1024), fileMtime = null)
        val after = repository.totalSizeBytes()
        assertTrue(after > before)
    }
}
