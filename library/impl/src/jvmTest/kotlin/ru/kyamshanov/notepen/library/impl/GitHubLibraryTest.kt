package ru.kyamshanov.notepen.library.impl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import ru.kyamshanov.notepen.library.api.LibraryBackendKind
import ru.kyamshanov.notepen.library.api.LibraryBookId
import ru.kyamshanov.notepen.library.api.LibraryConnection
import ru.kyamshanov.notepen.library.api.LibraryConnectionState
import ru.kyamshanov.notepen.library.api.LibraryRole
import ru.kyamshanov.notepen.library.api.NotLibrarianException
import ru.kyamshanov.notepen.sync.cloud.domain.CloudFile
import ru.kyamshanov.notepen.sync.cloud.domain.CloudStorageProvider
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GitHubLibraryTest {
    private val tempDir: File = Files.createTempDirectory("github-lib-test").toFile()
    private val cacheDir = File(tempDir, "cache").apply { mkdirs() }
    private val repo = "octocat/library"

    @AfterTest
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    /**
     * In-memory [CloudStorageProvider] over a path→bytes map (no real network). Records uploads and
     * blob-by-sha downloads so tests can assert which path was exercised.
     */
    private class FakeCloudProvider(
        private val files: MutableMap<String, ByteArray> = mutableMapOf(),
    ) : CloudStorageProvider {
        var uploads = 0
            private set
        var blobDownloads = 0
            private set
        var listCalls = 0
            private set

        private fun shaOf(path: String): String = "sha-${path.hashCode()}-${files[path]?.size}"

        override suspend fun list(directoryPath: String): List<CloudFile> {
            listCalls++
            return files
                .filterKeys { it.startsWith("$directoryPath/") }
                .map { (path, bytes) -> CloudFile(path = path, sha = shaOf(path), sizeBytes = bytes.size.toLong()) }
                .sortedBy { it.path }
        }

        override suspend fun download(path: String): ByteArray = files[path] ?: error("no file at $path")

        override suspend fun downloadBySha(
            sha: String,
            fallbackPath: String,
        ): ByteArray {
            blobDownloads++
            return files[fallbackPath] ?: error("no file at $fallbackPath")
        }

        override suspend fun upload(
            path: String,
            bytes: ByteArray,
            previousSha: String?,
        ): CloudFile {
            uploads++
            files[path] = bytes
            return CloudFile(path = path, sha = shaOf(path), sizeBytes = bytes.size.toLong())
        }
    }

    private fun backend(
        provider: CloudStorageProvider,
        token: String?,
    ) = GitHubLibraryBackend(
        providerFactory = { provider },
        cacheDir = cacheDir.absolutePath,
        ioDispatcher = UnconfinedTestDispatcher(),
    ).let { it to LibraryConnection.GitHub(repo = repo, token = token) }

    @Test
    fun books_listRepoBooksPathAsEntries() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val provider =
                FakeCloudProvider(
                    mutableMapOf(
                        "books/a.pdf" to "%PDF a".encodeToByteArray(),
                        "books/b.pdf" to "%PDF bb".encodeToByteArray(),
                        "readme.md" to "ignored".encodeToByteArray(),
                    ),
                )
            val (backend, spec) = backend(provider, token = null)

            val library = backend.connect(spec, scope).getOrThrow()

            assertEquals(LibraryBackendKind.GitHub, library.descriptor.kind)
            assertEquals(LibraryConnectionState.Connected, library.connectionState.value)
            val books = library.books.value
            assertEquals(listOf("books/a.pdf", "books/b.pdf"), books.map { it.libraryBookId.value })
            assertEquals(listOf("a.pdf", "b.pdf"), books.map { it.displayName }, "displayName is the filename")
            assertEquals(6L, books.first().sizeBytes)
            assertNull(books.first().identity, "blob sha is change-detection only, identity stays null")
            scope.cancel()
        }

    @Test
    fun role_isReaderWithoutToken_andLibrarianWithToken() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val provider = FakeCloudProvider(mutableMapOf("books/a.pdf" to "x".encodeToByteArray()))

            val (readerBackend, readerSpec) = backend(provider, token = null)
            val reader = readerBackend.connect(readerSpec, scope).getOrThrow()
            assertEquals(LibraryRole.Reader, reader.descriptor.role)
            assertFalse(reader.capabilities.canAdd)

            val (librarianBackend, librarianSpec) = backend(provider, token = "gho_write")
            val librarian = librarianBackend.connect(librarianSpec, scope).getOrThrow()
            assertEquals(LibraryRole.Librarian, librarian.descriptor.role)
            assertTrue(librarian.capabilities.canAdd)
            assertTrue(librarian.capabilities.canReplace)
            scope.cancel()
        }

    @Test
    fun open_downloadsAndCaches_readOnlyForReader() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val content = "%PDF-1.4 small".encodeToByteArray()
            val provider = FakeCloudProvider(mutableMapOf("books/a.pdf" to content))
            val (backend, spec) = backend(provider, token = null)
            val library = backend.connect(spec, scope).getOrThrow()

            val opened = library.open(LibraryBookId("books/a.pdf")).getOrThrow()

            assertTrue(opened.readOnly, "Reader-role document is read-only")
            assertNull(opened.identity)
            val cached = File(opened.localPath)
            assertTrue(cached.isFile, "downloaded bytes are cached locally")
            assertEquals(content.decodeToString(), cached.readText())
            assertEquals(0, provider.blobDownloads, "small file uses the raw Contents API, not the blob endpoint")
            scope.cancel()
        }

    @Test
    fun open_usesBlobApiForLargeFiles() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val big = ByteArray(2 * 1024 * 1024) { 1 } // 2 MB > 1 MB Contents-API ceiling
            val provider = FakeCloudProvider(mutableMapOf("books/big.pdf" to big))
            val (backend, spec) = backend(provider, token = null)
            val library = backend.connect(spec, scope).getOrThrow()

            val opened = library.open(LibraryBookId("books/big.pdf")).getOrThrow()

            assertEquals(1, provider.blobDownloads, "files over the Contents-API ceiling fetch via the blob endpoint")
            assertEquals(big.size.toLong(), File(opened.localPath).length())
            scope.cancel()
        }

    @Test
    fun addBook_uploadsAndAppendsEntry_forLibrarian() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val provider = FakeCloudProvider(mutableMapOf("books/a.pdf" to "a".encodeToByteArray()))
            val (backend, spec) = backend(provider, token = "gho_write")
            val library = backend.connect(spec, scope).getOrThrow()
            val srcFile = File(tempDir, "new.pdf").apply { writeText("%PDF new book") }

            val entry = library.addBook(srcFile.absolutePath).getOrThrow()

            assertEquals("books/new.pdf", entry.libraryBookId.value, "uploaded under the books/ path")
            assertEquals("new.pdf", entry.displayName)
            assertEquals(1, provider.uploads)
            assertTrue(library.books.value.any { it.libraryBookId.value == "books/new.pdf" }, "entry appended to books")
            scope.cancel()
        }

    @Test
    fun addBook_deniedForReader() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val provider = FakeCloudProvider(mutableMapOf("books/a.pdf" to "a".encodeToByteArray()))
            val (backend, spec) = backend(provider, token = null)
            val library = backend.connect(spec, scope).getOrThrow()
            val srcFile = File(tempDir, "new.pdf").apply { writeText("nope") }

            val result = library.addBook(srcFile.absolutePath)

            assertTrue(result.isFailure, "Reader cannot add books")
            assertTrue(result.exceptionOrNull() is NotLibrarianException)
            assertEquals(0, provider.uploads, "no upload was attempted")
            scope.cancel()
        }

    @Test
    fun replaceBook_uploadsWithPreviousSha_forLibrarian() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val provider = FakeCloudProvider(mutableMapOf("books/a.pdf" to "old".encodeToByteArray()))
            val (backend, spec) = backend(provider, token = "gho_write")
            val library = backend.connect(spec, scope).getOrThrow()
            val replacement = File(tempDir, "rep.pdf").apply { writeText("brand new content") }

            val entry = library.replaceBook(LibraryBookId("books/a.pdf"), replacement.absolutePath).getOrThrow()

            assertEquals("books/a.pdf", entry.libraryBookId.value)
            assertEquals(1, provider.uploads)
            assertEquals("brand new content".length.toLong(), entry.sizeBytes)
            scope.cancel()
        }
}
