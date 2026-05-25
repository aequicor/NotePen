package ru.kyamshanov.notepen.sync.infrastructure

import kotlinx.coroutines.test.runTest
import ru.kyamshanov.notepen.sync.domain.model.BookId
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileSystemLibraryManifestProviderTest {
    private val root: File = Files.createTempDirectory("notepen-lib").toFile()

    @AfterTest
    fun cleanup() {
        root.deleteRecursively()
    }

    private fun provider() =
        FileSystemLibraryManifestProvider(
            root = root,
            isBook = { it.name.lowercase().endsWith(".pdf") },
        )

    @Test
    fun publishesOnlyBookFilesUnderRoot() =
        runTest {
            File(root, "book1.pdf").writeText("a")
            // Annotation sidecar next to a book — must NOT be published.
            File(root, "book1.pdf.notepen.json").writeText("{}")
            // Unrelated file — filtered out by isBook.
            File(root, "notes.txt").writeText("x")
            // Nested book in a subdirectory — included, with a normalised path.
            File(root, "sub").mkdirs()
            File(root, "sub/book2.pdf").writeText("bb")

            val manifest = provider().current()

            val relativePaths = manifest.books.map { it.relativePath }.toSet()
            assertEquals(setOf("book1.pdf", "sub/book2.pdf"), relativePaths)
        }

    @Test
    fun resolvesIdsToPathsUnderRootAndRejectsUnknown() =
        runTest {
            File(root, "book1.pdf").writeText("a")
            val provider = provider()

            val book = provider.current().books.single()
            val resolved = provider.resolveAbsolutePath(book.id)

            assertTrue(resolved != null && File(resolved).isFile)
            assertTrue(File(resolved!!).canonicalPath.startsWith(root.canonicalPath))
            assertNull(provider.resolveAbsolutePath(BookId("nope#deadbeef")))
        }

    @Test
    fun absentRootYieldsEmptyManifest() =
        runTest {
            val provider =
                FileSystemLibraryManifestProvider(
                    root = File(root, "does-not-exist"),
                    isBook = { true },
                )
            assertTrue(provider.current().books.isEmpty())
        }
}
