package ru.kyamshanov.notepen.library.impl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import ru.kyamshanov.notepen.library.api.LibraryBackendKind
import ru.kyamshanov.notepen.library.api.LibraryBookId
import ru.kyamshanov.notepen.library.api.LibraryConnection
import ru.kyamshanov.notepen.library.api.LibraryConnectionState
import ru.kyamshanov.notepen.library.api.LibraryRole
import ru.kyamshanov.notepen.library.api.NotLibrarianException
import ru.kyamshanov.notepen.mainscreen.domain.port.LibraryFolder
import ru.kyamshanov.notepen.mainscreen.domain.port.LibraryFolderItem
import ru.kyamshanov.notepen.sync.domain.LibraryMutationClient
import ru.kyamshanov.notepen.sync.domain.RemoteDocumentOpener
import ru.kyamshanov.notepen.sync.domain.documentIdToCacheFileName
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.HostMessage
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.model.PairingState
import ru.kyamshanov.notepen.sync.domain.model.RemoteCatalog
import ru.kyamshanov.notepen.sync.domain.model.RemoteEntry
import ru.kyamshanov.notepen.sync.domain.model.RemoteLibraryRole
import ru.kyamshanov.notepen.sync.domain.port.SyncClient
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PeerLanLibraryTest {
    private val peerId = "peer-1"
    private val peer = DeviceInfo(id = peerId, name = "Studio Mac", host = "10.0.0.5", port = 8080)
    private val tempDir: File = Files.createTempDirectory("peerlan-test").toFile()

    @AfterTest
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    /** A [SyncClient] stub: never used on the cache-hit path of [RemoteDocumentOpener]. */
    private class NoopSyncClient : SyncClient {
        override val pairingStates: Flow<Map<String, PairingState>> = emptyFlow()
        override val connectedHosts: Flow<Set<DeviceInfo>> = flowOf(emptySet())
        override val incomingMessages: Flow<HostMessage> = emptyFlow()

        override suspend fun connect(
            server: DeviceInfo,
            pairingCode: String,
            selfInfo: DeviceInfo,
        ): Result<DeviceInfo> = Result.failure(UnsupportedOperationException("stub"))

        override suspend fun send(
            hostId: String,
            message: NetworkMessage,
        ) = Unit

        override suspend fun broadcast(message: NetworkMessage) = Unit

        override suspend fun disconnect(hostId: String) = Unit

        override suspend fun disconnectAll() = Unit
    }

    /**
     * Captures sent messages and auto-replies a successful [NetworkMessage.LibraryMutationResult]
     * the moment a library request is sent — closing the loop the real host would close over the
     * network so [LibraryMutationClient] can resolve.
     */
    private class RecordingSyncClient(
        scope: CoroutineScope,
    ) : SyncClient {
        val sent = mutableListOf<NetworkMessage>()
        private val host = DeviceInfo(id = "peer-1", name = "Studio Mac", host = "10.0.0.5", port = 8080)
        private val outbound = MutableSharedFlow<NetworkMessage>(replay = 64, extraBufferCapacity = 64)
        private val _incoming = MutableSharedFlow<HostMessage>(replay = 64, extraBufferCapacity = 64)

        override val pairingStates: Flow<Map<String, PairingState>> = emptyFlow()
        override val connectedHosts: Flow<Set<DeviceInfo>> = flowOf(setOf(host))
        override val incomingMessages: Flow<HostMessage> = _incoming

        init {
            scope.launch {
                outbound.collect { msg ->
                    val requestId =
                        when (msg) {
                            is NetworkMessage.LibraryAddRequest -> msg.requestId
                            is NetworkMessage.LibraryReplaceRequest -> msg.requestId
                            is NetworkMessage.LibraryRemoveRequest -> msg.requestId
                            else -> null
                        }
                    if (requestId != null) {
                        _incoming.emit(
                            HostMessage(
                                host,
                                NetworkMessage.LibraryMutationResult(requestId, ok = true, newLibraryBookId = "server-id"),
                            ),
                        )
                    }
                }
            }
        }

        override suspend fun connect(
            server: DeviceInfo,
            pairingCode: String,
            selfInfo: DeviceInfo,
        ): Result<DeviceInfo> = Result.failure(UnsupportedOperationException("stub"))

        override suspend fun send(
            hostId: String,
            message: NetworkMessage,
        ) {
            sent += message
            outbound.emit(message)
        }

        override suspend fun broadcast(message: NetworkMessage) = Unit

        override suspend fun disconnect(hostId: String) = Unit

        override suspend fun disconnectAll() = Unit
    }

    private fun catalog(
        vararg entries: RemoteEntry,
        grantedRole: RemoteLibraryRole = RemoteLibraryRole.Reader,
    ): RemoteCatalog =
        RemoteCatalog(
            hostName = "Studio Mac",
            recent = entries.toList(),
            folders = emptyList(),
            folderLinks = emptyList(),
            grantedRole = grantedRole,
        )

    private fun entry(
        documentId: String,
        displayName: String,
        fileSize: Long? = null,
        lastOpenedAt: Long = 0L,
        libraryId: String = "",
    ) = RemoteEntry(documentId, displayName, fileSize, lastOpenedAt, libraryId)

    /** Real opener exercising only the offline cache-hit branch (no live transport needed). */
    private fun cacheHitOpener(catalogs: StateFlow<Map<DeviceInfo, RemoteCatalog>>): RemoteDocumentOpener =
        RemoteDocumentOpener(
            client = NoopSyncClient(),
            catalogs = catalogs,
            destDir = tempDir.absolutePath,
            documentIdRegistry = null,
        )

    @Test
    fun books_mapPeerCatalogToEntries() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val catalogs =
                MutableStateFlow(
                    mapOf(peer to catalog(entry("doc-a#aa", "a.pdf", fileSize = 11, lastOpenedAt = 5))),
                )
            val backend = PeerLanLibraryBackend(catalogs = catalogs, documentOpenerProvider = { null })

            val library = backend.connect(LibraryConnection.PeerLan(peerId), scope).getOrThrow()

            assertEquals(LibraryBackendKind.PeerLan, library.descriptor.kind)
            assertEquals(LibraryRole.Reader, library.descriptor.role, "a peer's library is read-only")
            assertEquals("Studio Mac", library.descriptor.displayName, "display name from catalog hostName")
            val books = library.books.value
            assertEquals(1, books.size)
            assertEquals("doc-a#aa", books.single().libraryBookId.value, "libraryBookId is the remote documentId")
            assertEquals("a.pdf", books.single().displayName)
            assertEquals(11L, books.single().sizeBytes)
            assertEquals(5L, books.single().modifiedAt)
            assertEquals(null, books.single().identity, "LAN catalog carries no canonical id yet")
            scope.cancel()
        }

    @Test
    fun books_reactToCatalogUpdates_andIgnoreOtherPeers() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val other = DeviceInfo(id = "peer-2", name = "Other", host = "10.0.0.6", port = 8080)
            val catalogs = MutableStateFlow(mapOf(peer to catalog(entry("doc-a#aa", "a.pdf"))))
            val backend = PeerLanLibraryBackend(catalogs = catalogs, documentOpenerProvider = { null })
            val library = backend.connect(LibraryConnection.PeerLan(peerId), scope).getOrThrow()
            assertEquals(1, library.books.value.size)

            catalogs.value =
                mapOf(
                    peer to catalog(entry("doc-a#aa", "a.pdf"), entry("doc-b#bb", "b.pdf")),
                    other to catalog(entry("doc-x#xx", "x.pdf")),
                )

            val ids = library.books.value.map { it.libraryBookId.value }
            assertEquals(listOf("doc-a#aa", "doc-b#bb"), ids, "only this peer's catalog is surfaced")
            scope.cancel()
        }

    @Test
    fun books_scopedToNamedLibrary_filterByLibraryId() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val catalogs =
                MutableStateFlow(
                    mapOf(
                        peer to
                            catalog(
                                entry("doc-a#aa", "math.pdf", libraryId = "local:/Math"),
                                entry("doc-b#bb", "phys.pdf", libraryId = "local:/Physics"),
                            ),
                    ),
                )
            val backend = PeerLanLibraryBackend(catalogs = catalogs, documentOpenerProvider = { null })

            val mathLib =
                backend
                    .connect(
                        LibraryConnection.PeerLan(peerId = peerId, libraryId = "local:/Math", libraryName = "Math"),
                        scope,
                    ).getOrThrow()

            assertEquals(
                listOf("doc-a#aa"),
                mathLib.books.value.map { it.libraryBookId.value },
                "a named-library connection projects only its own tagged entries",
            )
            assertEquals("peerlan:$peerId:local:/Math", mathLib.descriptor.id.value, "library id namespaces by (peer, library)")
            assertEquals("Math", mathLib.descriptor.displayName, "display name comes from the scanned QR / spec")
            scope.cancel()
        }

    @Test
    fun books_wholeShelf_whenLibraryIdBlank_projectsEveryEntry() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val catalogs =
                MutableStateFlow(
                    mapOf(
                        peer to
                            catalog(
                                entry("doc-a#aa", "math.pdf", libraryId = "local:/Math"),
                                entry("doc-b#bb", "phys.pdf", libraryId = "local:/Physics"),
                            ),
                    ),
                )
            val backend = PeerLanLibraryBackend(catalogs = catalogs, documentOpenerProvider = { null })

            val whole = backend.connect(LibraryConnection.PeerLan(peerId), scope).getOrThrow()

            assertEquals(
                listOf("doc-a#aa", "doc-b#bb"),
                whole.books.value.map { it.libraryBookId.value },
                "a blank library scope (mDNS / legacy) projects the peer's whole shelf",
            )
            assertEquals("peerlan:$peerId", whole.descriptor.id.value, "blank scope keeps the legacy whole-shelf id")
            scope.cancel()
        }

    @Test
    fun open_returnsReadOnlyOpenableDocument_viaOpener() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val docId = "doc-a#aa"
            val displayName = "a.pdf"
            // Seed the opener's offline cache so open() resolves without a live transport.
            val cachedFile = File(tempDir, documentIdToCacheFileName(docId, displayName))
            cachedFile.writeText("%PDF-1.4 fake")
            val catalogs =
                MutableStateFlow(mapOf(peer to catalog(entry(docId, displayName))))
            val opener = cacheHitOpener(catalogs)
            val backend = PeerLanLibraryBackend(catalogs = catalogs, documentOpenerProvider = { opener })
            val library = backend.connect(LibraryConnection.PeerLan(peerId), scope).getOrThrow()

            val opened = library.open(LibraryBookId(docId)).getOrThrow()

            assertEquals(cachedFile.absolutePath, opened.localPath, "opener cached path is returned")
            assertTrue(opened.readOnly, "a peer's document is read-only")
            assertEquals(null, opened.identity)
            scope.cancel()
        }

    @Test
    fun connectionState_followsOnlinePeerSet() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val catalogs = MutableStateFlow(mapOf(peer to catalog(entry("doc-a#aa", "a.pdf"))))
            val online = MutableStateFlow(setOf(peerId))
            val backend =
                PeerLanLibraryBackend(
                    catalogs = catalogs,
                    documentOpenerProvider = { null },
                    onlinePeerIds = online,
                )
            val library = backend.connect(LibraryConnection.PeerLan(peerId), scope).getOrThrow()
            assertEquals(LibraryConnectionState.Connected, library.connectionState.value)

            online.value = emptySet()

            assertEquals(LibraryConnectionState.Disconnected, library.connectionState.value)
            scope.cancel()
        }

    @Test
    fun librarianRole_advertised_reportsLibrarianCapabilities_andAddBookSendsRequest() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val catalogs =
                MutableStateFlow(
                    mapOf(peer to catalog(entry("doc-a#aa", "a.pdf"), grantedRole = RemoteLibraryRole.Librarian)),
                )
            val src = File(tempDir, "new.pdf").apply { writeBytes("%PDF book".encodeToByteArray()) }
            val syncClient = RecordingSyncClient(scope)
            val backend =
                PeerLanLibraryBackend(
                    catalogs = catalogs,
                    documentOpenerProvider = { null },
                    mutationClientProvider = { peerId ->
                        LibraryMutationClient(
                            client = syncClient,
                            hostId = peerId,
                            newRequestId = { "req-1" },
                            timeoutMs = 10_000L,
                        )
                    },
                )
            val library = backend.connect(LibraryConnection.PeerLan(peerId), scope).getOrThrow()

            assertEquals(LibraryRole.Librarian, library.descriptor.role, "host advertised Librarian")
            assertTrue(library.capabilities.canAdd && library.capabilities.canReplace, "librarian can add/replace")

            val result = library.addBook(src.absolutePath).getOrThrow()

            assertEquals("server-id", result.libraryBookId.value, "the host-reported book id is surfaced")
            val req = syncClient.sent.filterIsInstance<NetworkMessage.LibraryAddRequest>().single()
            assertEquals("new.pdf", req.displayName)
            assertEquals(src.length(), req.fileSize)
            val chunks = syncClient.sent.filterIsInstance<NetworkMessage.FileChunk>()
            assertTrue(chunks.isNotEmpty() && chunks.all { it.transferId == req.requestId }, "chunks correlate to request")
            scope.cancel()
        }

    @Test
    fun readerRole_addBook_throwsNotLibrarianException() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val catalogs =
                MutableStateFlow(
                    mapOf(peer to catalog(entry("doc-a#aa", "a.pdf"), grantedRole = RemoteLibraryRole.Reader)),
                )
            val syncClient = RecordingSyncClient(scope)
            val backend =
                PeerLanLibraryBackend(
                    catalogs = catalogs,
                    documentOpenerProvider = { null },
                    mutationClientProvider = { peerId ->
                        LibraryMutationClient(client = syncClient, hostId = peerId, newRequestId = { "req-1" })
                    },
                )
            val library = backend.connect(LibraryConnection.PeerLan(peerId), scope).getOrThrow()

            assertEquals(LibraryRole.Reader, library.descriptor.role)
            assertFalse(library.capabilities.canAdd, "a reader cannot add")

            val result = library.addBook(File(tempDir, "x.pdf").apply { writeText("x") }.absolutePath)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is NotLibrarianException, "reader add fails with NotLibrarianException")
            assertTrue(syncClient.sent.isEmpty(), "no request sent for a reader")
            scope.cancel()
        }

    @Test
    fun registry_mergesPeerLanLibraryAlongsideLocal() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val catalogs = MutableStateFlow(mapOf(peer to catalog(entry("doc-a#aa", "remote.pdf"))))
            val localFolder = FakeLibraryFolder(listOf(localItem("local.pdf")))
            val registry =
                DefaultLibraryRegistry(
                    backends =
                        listOf(
                            LocalFolderLibraryBackend { _, _ -> localFolder },
                            PeerLanLibraryBackend(catalogs = catalogs, documentOpenerProvider = { null }),
                        ),
                    scope = scope,
                    ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                )

            registry.connect(LibraryConnection.Local("/tmp/lib")).getOrThrow()
            registry.connect(LibraryConnection.PeerLan(peerId)).getOrThrow()

            assertEquals(2, registry.libraries.value.size, "local + peer-lan connected")
            val merged = registry.mergedBooks.value.map { it.entry.displayName }
            assertEquals(listOf("local.pdf", "remote.pdf"), merged, "both libraries' books appear in mergedBooks")
            scope.cancel()
        }

    private fun localItem(name: String) =
        LibraryFolderItem(id = name, uri = "/tmp/lib/$name", displayName = name, sizeBytes = null, modifiedAt = 0L)

    private class FakeLibraryFolder(
        initial: List<LibraryFolderItem>,
    ) : LibraryFolder {
        private val state = MutableStateFlow(initial)
        override val items: StateFlow<List<LibraryFolderItem>> = state.asStateFlow()

        override suspend fun addCopy(sourceUri: String): Result<LibraryFolderItem> =
            Result.failure(UnsupportedOperationException("not used"))

        override suspend fun refresh() = Unit
    }
}
