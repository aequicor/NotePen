package ru.kyamshanov.notepen.mainscreen.infrastructure

import kotlinx.coroutines.test.runTest
import ru.kyamshanov.notepen.mainscreen.domain.exception.FolderNotFoundException
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Тесты [FolderRepositoryDesktop].
 *
 * TC-88 / CC-26: cascade delete of FolderFileLink on folder delete.
 * TC-89 / CC-27: empty folder remains visible in getAll with zero files.
 */
class FolderRepositoryDesktopTest {

    private lateinit var tmpDir: File
    private lateinit var repo: FolderRepositoryDesktop

    @BeforeTest
    fun setUp() {
        tmpDir = createTempDir("notepen-test-folders")
        repo = FolderRepositoryDesktop(dataDir = tmpDir)
    }

    @AfterTest
    fun tearDown() {
        tmpDir.deleteRecursively()
    }

    // TC-88 / CC-26: delete folder → associated FolderFileLinks are removed (cascade delete)
    @Test
    fun `delete folder removes associated links`() = runTest {
        val folder = repo.create("MyFolder")
        repo.addFile(folder.id, "/file.pdf")

        repo.delete(folder.id)

        // After delete, getFilesInFolder must throw FolderNotFoundException
        assertFailsWith<FolderNotFoundException>("getFilesInFolder must throw for deleted folder") {
            repo.getFilesInFolder(folder.id)
        }
        // The folder must no longer appear in getAll
        assertTrue(
            repo.getAll().none { it.id == folder.id },
            "Deleted folder must not appear in getAll",
        )
    }

    // TC-89 / CC-27: empty folder (no files added) must appear in getAll, getFilesInFolder returns empty list
    @Test
    fun `getAll includes empty folder with zero files`() = runTest {
        val folder = repo.create("EmptyFolder")

        val folders = repo.getAll()
        assertTrue(folders.any { it.id == folder.id }, "Empty folder must appear in getAll")

        val files = repo.getFilesInFolder(folder.id)
        assertEquals(0, files.size, "Empty folder must return empty file list")
    }
}
