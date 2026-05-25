package ru.kyamshanov.notepen.mainscreen.infrastructure

import kotlinx.coroutines.test.runTest
import ru.kyamshanov.notepen.mainscreen.domain.exception.FileDuplicateInFolderException
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
 * TC-14 / drag-drop AC-2, AC-10: addFile → success → getFilesInFolder contains URI.
 * TC-15 / drag-drop AC-5: addFile → duplicate → FileDuplicateInFolderException.
 * TC-16 / drag-drop EC-1: addFile with unknown folderId → FolderNotFoundException.
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

    @Test
    fun create_withParent_persistsParentId() =
        runTest {
            val parent = repo.create("Parent")
            val child = repo.create("Child", parentId = parent.id)

            assertEquals(parent.id, child.parentId, "child must carry the parent's id")
            val reloaded = repo.getAll().first { it.id == child.id }
            assertEquals(parent.id, reloaded.parentId, "parentId must survive a reload")
        }

    @Test
    fun create_withUnknownParent_throwsFolderNotFoundException() =
        runTest {
            assertFailsWith<FolderNotFoundException> {
                repo.create("Orphan", parentId = "does-not-exist")
            }
        }

    @Test
    fun delete_cascadesNestedSubfoldersAndTheirLinks() =
        runTest {
            val root = repo.create("Root")
            val mid = repo.create("Mid", parentId = root.id)
            val leaf = repo.create("Leaf", parentId = mid.id)
            repo.addFile(leaf.id, "file:///docs/in-leaf.pdf")
            val sibling = repo.create("Sibling")

            repo.delete(root.id)

            val remaining = repo.getAll().map { it.id }
            assertEquals(listOf(sibling.id), remaining, "only the unrelated sibling must remain")
            assertFailsWith<FolderNotFoundException> { repo.getFilesInFolder(leaf.id) }
        }

    // TC-14 / drag-drop: addFile → success → getFilesInFolder contains URI (AC-2, AC-10)
    @Test
    fun addFile_success_fileAppearsInGetFilesInFolder() =
        runTest {
            val folder = repo.create("MyDocs")
            val fileUri = "file:///home/user/docs/report.pdf"

            repo.addFile(folder.id, fileUri)

            val files = repo.getFilesInFolder(folder.id)
            assertTrue(files.contains(fileUri), "After addFile the URI must appear in getFilesInFolder")
        }

    // TC-15 / drag-drop: addFile duplicate → FileDuplicateInFolderException (AC-5)
    @Test
    fun addFile_duplicate_throwsFileDuplicateInFolderException() =
        runTest {
            val folder = repo.create("MyDocs2")
            val fileUri = "file:///home/user/docs/report2.pdf"

            repo.addFile(folder.id, fileUri) // first add — must succeed

            assertFailsWith<FileDuplicateInFolderException>(
                "Second addFile for the same URI must throw FileDuplicateInFolderException",
            ) {
                repo.addFile(folder.id, fileUri)
            }
        }

    // TC-16 / drag-drop: addFile with non-existent folderId → FolderNotFoundException (EC-1)
    @Test
    fun addFile_unknownFolderId_throwsFolderNotFoundException() =
        runTest {
            assertFailsWith<FolderNotFoundException>(
                "addFile with unknown folderId must throw FolderNotFoundException",
            ) {
                repo.addFile("non-existent-folder-id", "file:///some.pdf")
            }
        }

    // TC-88 / CC-26: delete folder → associated FolderFileLinks are removed (cascade delete)
    @Test
    fun `delete folder removes associated links`() =
        runTest {
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
    fun `getAll includes empty folder with zero files`() =
        runTest {
            val folder = repo.create("EmptyFolder")

            val folders = repo.getAll()
            assertTrue(folders.any { it.id == folder.id }, "Empty folder must appear in getAll")

            val files = repo.getFilesInFolder(folder.id)
            assertEquals(0, files.size, "Empty folder must return empty file list")
        }
}
