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
import ru.kyamshanov.notepen.document.domain.model.CanonicalBookId
import ru.kyamshanov.notepen.library.api.Library
import ru.kyamshanov.notepen.library.api.LibraryBackend
import ru.kyamshanov.notepen.library.api.LibraryBackendKind
import ru.kyamshanov.notepen.library.api.LibraryBookId
import ru.kyamshanov.notepen.library.api.LibraryCapabilities
import ru.kyamshanov.notepen.library.api.LibraryConnection
import ru.kyamshanov.notepen.library.api.LibraryConnectionState
import ru.kyamshanov.notepen.library.api.LibraryDescriptor
import ru.kyamshanov.notepen.library.api.LibraryEntry
import ru.kyamshanov.notepen.library.api.LibraryId
import ru.kyamshanov.notepen.library.api.LibraryRole
import ru.kyamshanov.notepen.library.api.OpenableDocument
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

    // --- M6: cross-library dedup by canonical identity --------------------------------------------

    /** Bare [Library] over a fixed book list — lets a test drive [DefaultLibraryRegistry.mergedBooks]
     *  with entries that carry canonical identities. */
    private class FakeLibrary(
        private val libId: String,
        private val kind: LibraryBackendKind,
        books: List<LibraryEntry>,
    ) : Library {
        override val descriptor =
            LibraryDescriptor(id = LibraryId(libId), displayName = libId, kind = kind, role = LibraryRole.Reader)
        override val capabilities = LibraryCapabilities.fromRole(LibraryRole.Reader)
        override val books = MutableStateFlow(books)
        override val connectionState = MutableStateFlow<LibraryConnectionState>(LibraryConnectionState.Connected)

        override suspend fun refresh() = Unit

        override suspend fun open(id: LibraryBookId): Result<OpenableDocument> = Result.failure(UnsupportedOperationException())
    }

    /** Backend that hands back a pre-built [FakeLibrary] for a given connection kind. */
    private class FakeBackend(
        override val kind: LibraryBackendKind,
        private val library: Library,
    ) : LibraryBackend {
        override suspend fun connect(
            spec: LibraryConnection,
            scope: CoroutineScope,
        ): Result<Library> = Result.success(library)

        override suspend fun probe(spec: LibraryConnection): Result<LibraryDescriptor> = Result.success(library.descriptor)
    }

    private fun entry(
        bookId: String,
        name: String,
        identityHex: String?,
    ) = LibraryEntry(
        libraryBookId = LibraryBookId(bookId),
        displayName = name,
        identity = identityHex?.let { CanonicalBookId(it) },
    )

    @Test
    fun mergedBooks_dedupsSameIdentityAcrossLibraries_intoOneEntryWithBothLibraryIds() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val sharedHex = "a".repeat(64)
            // The SAME book (same canonical id) lives in the local folder AND is served by a LAN peer.
            val local = FakeLibrary("local:root", LibraryBackendKind.Local, listOf(entry("local/book.pdf", "Book", sharedHex)))
            val peer = FakeLibrary("peer:42", LibraryBackendKind.PeerLan, listOf(entry("remote#1", "Book", sharedHex)))
            val registry =
                DefaultLibraryRegistry(
                    backends =
                        listOf(
                            FakeBackend(LibraryBackendKind.Local, local),
                            FakeBackend(LibraryBackendKind.PeerLan, peer),
                        ),
                    scope = scope,
                    ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                )

            registry.connect(LibraryConnection.Local(rootPath)).getOrThrow()
            registry.connect(LibraryConnection.PeerLan(peerId = "42")).getOrThrow()

            val merged = registry.mergedBooks.value
            assertEquals(1, merged.size, "same canonical id collapses to ONE merged entry")
            assertEquals(
                listOf(LibraryId("local:root"), LibraryId("peer:42")),
                merged.single().libraryIds,
                "the merged entry carries every library that holds the book, in registry order",
            )
            scope.cancel()
        }

    @Test
    fun mergedBooks_nullIdentityEntriesStayDistinct_noFalseMerge() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            // Two different books, both WITHOUT a canonical id — must never collapse together.
            val local =
                FakeLibrary(
                    "local:root",
                    LibraryBackendKind.Local,
                    listOf(entry("a.pdf", "A", identityHex = null), entry("b.pdf", "B", identityHex = null)),
                )
            val peer =
                FakeLibrary("peer:42", LibraryBackendKind.PeerLan, listOf(entry("c.pdf", "C", identityHex = null)))
            val registry =
                DefaultLibraryRegistry(
                    backends =
                        listOf(
                            FakeBackend(LibraryBackendKind.Local, local),
                            FakeBackend(LibraryBackendKind.PeerLan, peer),
                        ),
                    scope = scope,
                    ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                )

            registry.connect(LibraryConnection.Local(rootPath)).getOrThrow()
            registry.connect(LibraryConnection.PeerLan(peerId = "42")).getOrThrow()

            val merged = registry.mergedBooks.value
            assertEquals(3, merged.size, "null-identity entries never merge")
            assertEquals(listOf("A", "B", "C"), merged.map { it.entry.displayName }, "stable first-appearance order")
            assertTrue(merged.all { it.libraryIds.size == 1 }, "each distinct entry belongs to one library")
            scope.cancel()
        }

    @Test
    fun mergedBooks_orderIsStableFirstAppearance_duplicateOnlyWidensExistingSlot() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val xHex = "1".repeat(64)
            val yHex = "2".repeat(64)
            val zHex = "3".repeat(64)
            // local: [X, Y]; peer also holds Y plus a new Z. Expected order: X, Y, Z — Y is placed at
            // its local slot (index 1) and the peer copy only widens its libraryIds, not the order.
            val local =
                FakeLibrary(
                    "local:root",
                    LibraryBackendKind.Local,
                    listOf(entry("x.pdf", "X", xHex), entry("y.pdf", "Y", yHex)),
                )
            val peer =
                FakeLibrary(
                    "peer:42",
                    LibraryBackendKind.PeerLan,
                    listOf(entry("y2.pdf", "Y", yHex), entry("z.pdf", "Z", zHex)),
                )
            val registry =
                DefaultLibraryRegistry(
                    backends =
                        listOf(
                            FakeBackend(LibraryBackendKind.Local, local),
                            FakeBackend(LibraryBackendKind.PeerLan, peer),
                        ),
                    scope = scope,
                    ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                )

            registry.connect(LibraryConnection.Local(rootPath)).getOrThrow()
            registry.connect(LibraryConnection.PeerLan(peerId = "42")).getOrThrow()

            val merged = registry.mergedBooks.value
            assertEquals(listOf("X", "Y", "Z"), merged.map { it.entry.displayName }, "stable first-appearance order")
            assertEquals(listOf(LibraryId("local:root")), merged[0].libraryIds, "X only in local")
            assertEquals(
                listOf(LibraryId("local:root"), LibraryId("peer:42")),
                merged[1].libraryIds,
                "Y held by both, deduped to its first (local) slot",
            )
            assertEquals(listOf(LibraryId("peer:42")), merged[2].libraryIds, "Z only on the peer")
            scope.cancel()
        }
}
