package ru.kyamshanov.notepen.library.impl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import ru.kyamshanov.notepen.library.api.LibraryConnection
import ru.kyamshanov.notepen.mainscreen.domain.port.LibraryFolder
import ru.kyamshanov.notepen.mainscreen.domain.port.LibraryFolderItem
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.RemoteCatalog
import ru.kyamshanov.notepen.sync.domain.model.RemoteEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultLibraryRegistryTest {
    private val rootPath = "/tmp/notepen-library-test"

    private fun item(
        id: String,
        name: String,
        size: Long? = null,
        modified: Long = 0L,
    ) = LibraryFolderItem(
        id = id,
        uri = "$rootPath/$id",
        displayName = name,
        sizeBytes = size,
        modifiedAt = modified,
    )

    /** In-memory [LibraryFolder] whose `items` can be mutated to simulate scans / adds. */
    private class FakeLibraryFolder(
        initial: List<LibraryFolderItem>,
    ) : LibraryFolder {
        private val mutableItems = MutableStateFlow(initial)
        override val items: StateFlow<List<LibraryFolderItem>> = mutableItems.asStateFlow()
        var refreshed: Int = 0

        fun emit(value: List<LibraryFolderItem>) {
            mutableItems.value = value
        }

        override suspend fun addCopy(sourceUri: String): Result<LibraryFolderItem> {
            val added = LibraryFolderItem(sourceUri, sourceUri, sourceUri, null, 0L)
            mutableItems.value = mutableItems.value + added
            return Result.success(added)
        }

        override suspend fun refresh() {
            refreshed++
        }
    }

    // Unconfined: launched coroutines (the merge job, library book mirroring) run eagerly to their
    // first suspension, so mergedBooks settles synchronously after each mutation.
    private fun TestScope.registry(
        scope: CoroutineScope,
        folder: LibraryFolder? = null,
    ): DefaultLibraryRegistry =
        DefaultLibraryRegistry(
            backends = folder?.let { listOf(LocalFolderLibraryBackend { _, _ -> it }) } ?: emptyList(),
            scope = scope,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

    @Test
    fun connectingOneLocalLibrary_flowsAllEntriesThroughMergedBooks() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val folder =
                FakeLibraryFolder(
                    listOf(
                        item("a.pdf", "a.pdf", size = 10, modified = 2),
                        item("sub/b.pdf", "b.pdf", size = 20, modified = 1),
                    ),
                )
            val registry = registry(scope, folder)

            val result = registry.connect(LibraryConnection.Local(rootPath))
            assertTrue(result.isSuccess, "connect should succeed")

            assertEquals(1, registry.libraries.value.size, "one library connected")
            val merged = registry.mergedBooks.value
            assertEquals(2, merged.size, "both folder items flow through mergedBooks")
            assertEquals(
                listOf("a.pdf", "sub/b.pdf"),
                merged.map { it.entry.libraryBookId.value },
                "libraryBookId carries the folder item id (relative path)",
            )
            assertEquals(listOf("a.pdf", "b.pdf"), merged.map { it.entry.displayName })
            assertEquals(listOf(10L, 20L), merged.map { it.entry.sizeBytes })
            assertEquals(null, merged.first().entry.identity, "identity is null in M1")
            val libId = registry.libraries.value.single().descriptor.id
            assertEquals(listOf(listOf(libId), listOf(libId)), merged.map { it.libraryIds })
            scope.cancel()
        }

    @Test
    fun connectingLocalAndPeerLan_mergedBooksContainsBothLibrariesBooks() =
        runTest {
            // M2 shelf goal: books from MULTIPLE connected libraries (local + a LAN peer) all
            // surface in the single merged listing, each tagged with its owning library.
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val folder = FakeLibraryFolder(listOf(item("local.pdf", "Local Book")))
            val peerId = "peer-42"
            val catalogs =
                MutableStateFlow(
                    mapOf(
                        DeviceInfo(id = peerId, name = "Tablet", host = "10.0.0.9", port = 0) to
                            RemoteCatalog(
                                hostName = "Tablet",
                                recent =
                                    listOf(
                                        RemoteEntry(documentId = "remote#1", displayName = "Peer Book", fileSize = 5, lastOpenedAt = 3),
                                    ),
                                folders = emptyList(),
                                folderLinks = emptyList(),
                            ),
                    ),
                )
            val registry =
                DefaultLibraryRegistry(
                    backends =
                        listOf(
                            LocalFolderLibraryBackend { _, _ -> folder },
                            PeerLanLibraryBackend(catalogs = catalogs, documentOpenerProvider = { null }),
                        ),
                    scope = scope,
                    ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                )

            registry.connect(LibraryConnection.Local(rootPath)).getOrThrow()
            registry.connect(LibraryConnection.PeerLan(peerId = peerId, host = "10.0.0.9")).getOrThrow()

            assertEquals(2, registry.libraries.value.size, "two libraries connected")
            val merged = registry.mergedBooks.value
            assertEquals(
                setOf("Local Book", "Peer Book"),
                merged.map { it.entry.displayName }.toSet(),
                "books from both the local folder and the LAN peer appear in the merged shelf",
            )
            scope.cancel()
        }

    @Test
    fun folderUpdate_propagatesToMergedBooks() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val folder = FakeLibraryFolder(listOf(item("a.pdf", "a.pdf")))
            val registry = registry(scope, folder)
            registry.connect(LibraryConnection.Local(rootPath))
            assertEquals(1, registry.mergedBooks.value.size)

            folder.emit(listOf(item("a.pdf", "a.pdf"), item("c.pdf", "c.pdf")))

            assertEquals(2, registry.mergedBooks.value.size, "new folder item appears in mergedBooks")
            scope.cancel()
        }

    @Test
    fun noLibraries_mergedBooksIsEmpty() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val registry = registry(scope)
            assertTrue(registry.libraries.value.isEmpty())
            assertTrue(registry.mergedBooks.value.isEmpty())
            assertTrue(registry.savedConnections().isEmpty())
            scope.cancel()
        }

    @Test
    fun connect_withoutMatchingBackend_fails() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val registry = registry(scope)
            val result = registry.connect(LibraryConnection.Local(rootPath))
            assertTrue(result.isFailure, "no Local backend → failure")
            scope.cancel()
        }

    @Test
    fun disconnect_removesLibraryAndClearsBooks() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val folder = FakeLibraryFolder(listOf(item("a.pdf", "a.pdf")))
            val registry = registry(scope, folder)
            val library = registry.connect(LibraryConnection.Local(rootPath)).getOrThrow()
            assertEquals(1, registry.mergedBooks.value.size)

            registry.disconnect(library.descriptor.id)

            assertTrue(registry.libraries.value.isEmpty())
            assertTrue(registry.mergedBooks.value.isEmpty())
            scope.cancel()
        }
}
