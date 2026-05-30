package ru.kyamshanov.notepen.library.impl.google

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import ru.kyamshanov.notepen.library.api.CloudProviderIds
import ru.kyamshanov.notepen.library.api.DriveLikeStore
import ru.kyamshanov.notepen.library.api.LibraryBackendKind
import ru.kyamshanov.notepen.library.api.LibraryBookId
import ru.kyamshanov.notepen.library.api.LibraryConnection
import ru.kyamshanov.notepen.library.api.LibraryConnectionState
import ru.kyamshanov.notepen.library.api.LibraryRole
import ru.kyamshanov.notepen.library.api.NotLibrarianException
import ru.kyamshanov.notepen.library.api.RemoteFile
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GoogleDriveLibraryTest {
    private val tempDir: File = Files.createTempDirectory("gdrive-lib-test").toFile()
    private val cacheDir = File(tempDir, "cache").apply { mkdirs() }
    private val folderId = "folder123"

    @AfterTest
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    /** In-memory [DriveLikeStore] over an id→(name,bytes,version) map. Records create/update calls. */
    private class FakeDriveStore : DriveLikeStore {
        private data class Item(
            val name: String,
            val bytes: ByteArray,
            val version: Int,
        )

        private val items = linkedMapOf<String, Item>()
        var creates = 0
            private set
        var updates = 0
            private set
        private var nextId = 0

        fun seed(
            id: String,
            name: String,
            bytes: ByteArray,
        ) {
            items[id] = Item(name, bytes, 1)
        }

        override suspend fun listChildren(folderId: String): List<RemoteFile> =
            items.map { (id, item) ->
                RemoteFile(id = id, name = item.name, sizeBytes = item.bytes.size.toLong(), version = item.version.toString())
            }

        override suspend fun download(fileId: String): ByteArray = items[fileId]?.bytes ?: error("no file $fileId")

        override suspend fun create(
            parentFolderId: String,
            name: String,
            bytes: ByteArray,
        ): RemoteFile {
            creates++
            val id = "created-${nextId++}"
            items[id] = Item(name, bytes, 1)
            return RemoteFile(id = id, name = name, sizeBytes = bytes.size.toLong(), version = "1")
        }

        override suspend fun update(
            fileId: String,
            bytes: ByteArray,
            previousVersion: String?,
        ): RemoteFile {
            updates++
            val existing = items.getValue(fileId)
            val bumped = existing.copy(bytes = bytes, version = existing.version + 1)
            items[fileId] = bumped
            return RemoteFile(id = fileId, name = bumped.name, sizeBytes = bytes.size.toLong(), version = bumped.version.toString())
        }
    }

    private fun backend(store: DriveLikeStore) =
        GoogleDriveLibraryBackend(
            storeFactory = { store },
            cacheDir = cacheDir.absolutePath,
            ioDispatcher = UnconfinedTestDispatcher(),
        )

    private fun spec(scope: String) =
        LibraryConnection.Cloud(
            providerId = CloudProviderIds.GOOGLE_DRIVE,
            accountId = folderId,
            refreshToken = "refresh-token",
            scope = scope,
        )

    @Test
    fun books_listFolderChildrenAsEntries_withSeparateIdAndName() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val store =
                FakeDriveStore().apply {
                    seed("file-a", "Alpha.pdf", "%PDF a".encodeToByteArray())
                    seed("file-b", "Beta.pdf", "%PDF bb".encodeToByteArray())
                }
            val library = backend(store).connect(spec(GoogleOAuthConfig.SCOPE_DRIVE_READONLY), scope).getOrThrow()

            assertEquals(LibraryBackendKind.Cloud, library.descriptor.kind)
            assertEquals(LibraryConnectionState.Connected, library.connectionState.value)
            val books = library.books.value
            assertEquals(listOf("file-a", "file-b"), books.map { it.libraryBookId.value }, "book id is the opaque Drive fileId")
            assertEquals(listOf("Alpha.pdf", "Beta.pdf"), books.map { it.displayName }, "displayName is the Drive title, not the id")
            assertNull(books.first().identity, "Drive version is change-detection only; identity stays null")
            scope.cancel()
        }

    @Test
    fun role_readerForReadonlyScope_librarianForWriteScope() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val store = FakeDriveStore().apply { seed("file-a", "a.pdf", "x".encodeToByteArray()) }

            val reader = backend(store).connect(spec(GoogleOAuthConfig.SCOPE_DRIVE_READONLY), scope).getOrThrow()
            assertEquals(LibraryRole.Reader, reader.descriptor.role)
            assertFalse(reader.capabilities.canAdd)

            val librarian = backend(store).connect(spec(GoogleOAuthConfig.SCOPE_DRIVE_FILE), scope).getOrThrow()
            assertEquals(LibraryRole.Librarian, librarian.descriptor.role)
            assertTrue(librarian.capabilities.canAdd)
            assertTrue(librarian.capabilities.canReplace)
            scope.cancel()
        }

    @Test
    fun open_downloadsAndCaches_readOnlyForReader() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val content = "%PDF-1.4 hello".encodeToByteArray()
            val store = FakeDriveStore().apply { seed("file-a", "a.pdf", content) }
            val library = backend(store).connect(spec(GoogleOAuthConfig.SCOPE_DRIVE_READONLY), scope).getOrThrow()

            val opened = library.open(LibraryBookId("file-a")).getOrThrow()

            assertTrue(opened.readOnly, "Reader-role document is read-only")
            assertNull(opened.identity)
            val cached = File(opened.localPath)
            assertTrue(cached.isFile, "downloaded bytes are cached locally")
            assertEquals(content.decodeToString(), cached.readText())
            scope.cancel()
        }

    @Test
    fun addBook_createsAndAppendsEntry_forLibrarian() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val store = FakeDriveStore().apply { seed("file-a", "a.pdf", "a".encodeToByteArray()) }
            val library = backend(store).connect(spec(GoogleOAuthConfig.SCOPE_DRIVE_FILE), scope).getOrThrow()
            val srcFile = File(tempDir, "new.pdf").apply { writeText("%PDF new book") }

            val entry = library.addBook(srcFile.absolutePath).getOrThrow()

            assertEquals("new.pdf", entry.displayName)
            assertEquals(1, store.creates)
            assertTrue(library.books.value.any { it.libraryBookId.value == entry.libraryBookId.value }, "entry appended to books")
            scope.cancel()
        }

    @Test
    fun addBook_deniedForReader() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val store = FakeDriveStore().apply { seed("file-a", "a.pdf", "a".encodeToByteArray()) }
            val library = backend(store).connect(spec(GoogleOAuthConfig.SCOPE_DRIVE_READONLY), scope).getOrThrow()
            val srcFile = File(tempDir, "new.pdf").apply { writeText("nope") }

            val result = library.addBook(srcFile.absolutePath)

            assertTrue(result.isFailure, "Reader cannot add books")
            assertTrue(result.exceptionOrNull() is NotLibrarianException)
            assertEquals(0, store.creates, "no create was attempted")
            scope.cancel()
        }

    @Test
    fun replaceBook_updatesForLibrarian() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val store = FakeDriveStore().apply { seed("file-a", "a.pdf", "old".encodeToByteArray()) }
            val library = backend(store).connect(spec(GoogleOAuthConfig.SCOPE_DRIVE_FILE), scope).getOrThrow()
            val replacement = File(tempDir, "rep.pdf").apply { writeText("brand new content") }

            val entry = library.replaceBook(LibraryBookId("file-a"), replacement.absolutePath).getOrThrow()

            assertEquals("file-a", entry.libraryBookId.value)
            assertEquals(1, store.updates)
            assertEquals("brand new content".length.toLong(), entry.sizeBytes)
            assertEquals("brand new content", store.download("file-a").decodeToString())
            scope.cancel()
        }

    @Test
    fun refresh_versionChange_invalidatesCachedFile_soNextOpenReDownloads() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val store = FakeDriveStore().apply { seed("file-a", "a.pdf", "%PDF old".encodeToByteArray()) }
            val library = backend(store).connect(spec(GoogleOAuthConfig.SCOPE_DRIVE_FILE), scope).getOrThrow()

            val firstOpen = library.open(LibraryBookId("file-a")).getOrThrow()
            val cached = File(firstOpen.localPath)
            assertTrue(cached.isFile)
            assertEquals("%PDF old", cached.readText())

            // Upstream content changes → new version (FakeDriveStore.update bumps the version).
            store.update("file-a", "%PDF brand new".encodeToByteArray(), previousVersion = "1")
            library.refresh()

            assertFalse(cached.exists(), "the stale cache file is invalidated when the Drive version changed")
            val secondOpen = library.open(LibraryBookId("file-a")).getOrThrow()
            assertEquals("%PDF brand new", File(secondOpen.localPath).readText())
            scope.cancel()
        }
}
