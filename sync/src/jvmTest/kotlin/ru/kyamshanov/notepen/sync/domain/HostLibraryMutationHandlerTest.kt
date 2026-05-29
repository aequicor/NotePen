package ru.kyamshanov.notepen.sync.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import ru.kyamshanov.notepen.document.domain.sha256Hex
import ru.kyamshanov.notepen.mainscreen.domain.port.FolderRepository
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.LibraryManifest
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.model.PeerMessage
import ru.kyamshanov.notepen.sync.domain.model.ServerLifecycleState
import ru.kyamshanov.notepen.sync.domain.port.LibraryManifestProvider
import ru.kyamshanov.notepen.sync.domain.port.LibraryMutationOutcome
import ru.kyamshanov.notepen.sync.domain.port.LibraryMutationTarget
import ru.kyamshanov.notepen.sync.domain.port.PeerServer
import ru.kyamshanov.notepen.sync.infrastructure.encodeBase64
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HostLibraryMutationHandlerTest {
    // Real dispatcher: the handler's single collector demuxes the inbound chunk
    // stream off the server's SharedFlow into per-request channels, so emissions
    // need to interleave with real suspension points rather than being trapped on
    // a single virtual thread.
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    private val librarian = DeviceInfo(id = "lib-1", name = "Tablet", host = "10.0.0.2", port = 1)
    private val reader = DeviceInfo(id = "read-1", name = "Other", host = "10.0.0.3", port = 1)

    @AfterTest
    fun teardown() {
        scope.coroutineContext[Job]?.cancel()
    }

    @Test
    fun librarianAddRequest_appliesAddBook_repliesOk() =
        runBlocking {
            val payload = "PDF-CONTENT-bytes".encodeToByteArray()
            val server = FakePeerServer()
            val provider = newProvider()
            provider.grantLibrarian(librarian.id)
            val target = RecordingTarget(addOutcome = Result.success(LibraryMutationOutcome("book.pdf")))

            HostLibraryMutationHandler(server, provider, target).start(scope)
            server.awaitSubscribed()

            val requestId = "req-1"
            server.emit(librarian, addRequest(requestId, payload))
            yield()
            server.emitChunks(librarian, requestId, payload)

            val result = server.awaitResultFor(requestId)
            assertTrue(result.ok)
            assertEquals("book.pdf", result.newLibraryBookId)
            assertEquals(1, target.addCalls.size)
            val (_, displayName) = target.addCalls.single()
            assertEquals("My Book.pdf", displayName)
            // The handler hands the reassembled+verified bytes to the target (captured
            // while the temp file still existed, before the handler's finally deletes it).
            assertTrue(target.lastAddedBytes.contentEquals(payload))
        }

    /**
     * Regression for the M5 BLOCKER (dropped-chunk corruption). Emits the
     * `LibraryAddRequest` and its `FileTransferStart` + `FileChunk` back-to-back
     * with **no `yield()` between them**, into a `replay = 0` flow — exactly the
     * production ordering where a client streams chunks the instant the request is
     * sent.
     *
     * Old code path (now fixed): the request handler launched a *child* coroutine
     * whose `FileTransferReceiver` only then subscribed to `incomingMessages`. With
     * `replay = 0` the chunks emitted before that late subscription were lost, so
     * the upload timed out and `awaitResultFor` returned `ok = false` (or hung past
     * the 10s timeout). This test would FAIL on that code.
     *
     * Fixed code path: the single always-subscribed collector in `start()`
     * registers the per-requestId channel *synchronously* when it processes the
     * request — before it reads the next buffered message — so the chunks it reads
     * immediately after are routed into the receiver. No chunk can be lost.
     */
    @Test
    fun chunksStreamedImmediatelyAfterRequest_areNotLost() =
        runBlocking {
            val payload = "PDF-CONTENT-streamed-without-pause".encodeToByteArray()
            val server = FakePeerServer()
            val provider = newProvider()
            provider.grantLibrarian(librarian.id)
            val target = RecordingTarget(addOutcome = Result.success(LibraryMutationOutcome("book.pdf")))

            HostLibraryMutationHandler(server, provider, target).start(scope)
            server.awaitSubscribed()

            val requestId = "req-race"
            // No yield(): request and chunks are buffered together and consumed by
            // the single collector in order. The old late-subscriber would miss them.
            server.emit(librarian, addRequest(requestId, payload))
            server.emitChunks(librarian, requestId, payload)

            val result = server.awaitResultFor(requestId)
            assertTrue(result.ok, "upload must complete — no chunk lost to the subscription race")
            assertEquals("book.pdf", result.newLibraryBookId)
            assertEquals(1, target.addCalls.size)
            assertTrue(target.lastAddedBytes.contentEquals(payload))
        }

    @Test
    fun nonLibrarianAddRequest_rejected_noMutation() =
        runBlocking {
            val payload = "anything".encodeToByteArray()
            val server = FakePeerServer()
            val provider = newProvider() // reader was never granted — default-deny
            val target = RecordingTarget()

            HostLibraryMutationHandler(server, provider, target).start(scope)
            server.awaitSubscribed()

            val requestId = "req-2"
            server.emit(reader, addRequest(requestId, payload))

            val result = server.awaitResultFor(requestId)
            assertFalse(result.ok)
            assertTrue(result.error?.contains("librarian", ignoreCase = true) == true)
            assertEquals(0, target.addCalls.size)
        }

    @Test
    fun sha256Mismatch_rejected_noMutation() =
        runBlocking {
            val payload = "real-bytes".encodeToByteArray()
            val server = FakePeerServer()
            val provider = newProvider()
            provider.grantLibrarian(librarian.id)
            val target = RecordingTarget()

            HostLibraryMutationHandler(server, provider, target).start(scope)
            server.awaitSubscribed()

            val requestId = "req-3"
            // Declare a bogus checksum; upload the real bytes → mismatch.
            val req =
                NetworkMessage.LibraryAddRequest(
                    targetLibraryId = "",
                    displayName = "X.pdf",
                    fileSize = payload.size.toLong(),
                    contentSha256 = "deadbeef".repeat(8),
                    requestId = requestId,
                )
            server.emit(librarian, req)
            yield()
            server.emitChunks(librarian, requestId, payload)

            val result = server.awaitResultFor(requestId)
            assertFalse(result.ok)
            assertTrue(result.error?.contains("checksum", ignoreCase = true) == true)
            assertEquals(0, target.addCalls.size)
        }

    @Test
    fun sizeMismatch_rejected_noMutation() =
        runBlocking {
            val payload = "twelve-bytes".encodeToByteArray()
            val server = FakePeerServer()
            val provider = newProvider()
            provider.grantLibrarian(librarian.id)
            val target = RecordingTarget()

            HostLibraryMutationHandler(server, provider, target).start(scope)
            server.awaitSubscribed()

            val requestId = "req-4"
            val req =
                NetworkMessage.LibraryAddRequest(
                    targetLibraryId = "",
                    displayName = "X.pdf",
                    fileSize = payload.size.toLong() + 999, // wrong declared size
                    contentSha256 = sha256Hex(payload),
                    requestId = requestId,
                )
            server.emit(librarian, req)
            yield()
            server.emitChunks(librarian, requestId, payload)

            val result = server.awaitResultFor(requestId)
            assertFalse(result.ok)
            assertTrue(result.error?.contains("size", ignoreCase = true) == true)
            assertEquals(0, target.addCalls.size)
        }

    @Test
    fun librarianRemoveRequest_unsupportedBackend_repliesUnsupported() =
        runBlocking {
            val server = FakePeerServer()
            val provider = newProvider()
            provider.grantLibrarian(librarian.id)
            val target =
                RecordingTarget(removeOutcome = Result.failure(UnsupportedOperationException("removal unsupported")))

            HostLibraryMutationHandler(server, provider, target).start(scope)
            server.awaitSubscribed()

            val requestId = "req-5"
            server.emit(
                librarian,
                NetworkMessage.LibraryRemoveRequest(
                    targetLibraryId = "",
                    libraryBookId = "book.pdf",
                    requestId = requestId,
                ),
            )

            val result = server.awaitResultFor(requestId)
            assertFalse(result.ok)
            assertTrue(result.error?.contains("unsupported", ignoreCase = true) == true)
            assertEquals(1, target.removeCalls.size)
        }

    @Test
    fun nonLibrarianRemoveRequest_rejected_noMutation() =
        runBlocking {
            val server = FakePeerServer()
            val provider = newProvider()
            val target = RecordingTarget()

            HostLibraryMutationHandler(server, provider, target).start(scope)
            server.awaitSubscribed()

            val requestId = "req-6"
            server.emit(
                reader,
                NetworkMessage.LibraryRemoveRequest(
                    targetLibraryId = "",
                    libraryBookId = "book.pdf",
                    requestId = requestId,
                ),
            )

            val result = server.awaitResultFor(requestId)
            assertFalse(result.ok)
            assertEquals(0, target.removeCalls.size)
        }

    private fun addRequest(
        requestId: String,
        payload: ByteArray,
    ): NetworkMessage.LibraryAddRequest =
        NetworkMessage.LibraryAddRequest(
            targetLibraryId = "",
            displayName = "My Book.pdf",
            fileSize = payload.size.toLong(),
            contentSha256 = sha256Hex(payload),
            requestId = requestId,
        )

    private fun newProvider(): RemoteCatalogProvider =
        RemoteCatalogProvider(
            hostName = "host",
            manifestProvider = EmptyManifestProvider,
            folderRepository = EmptyFolderRepository,
        )
}

/**
 * Minimal in-memory [PeerServer] used to drive the handler under test.
 *
 * **`replay = 0`** — deliberately mirrors the real [KtorPeerServer._incoming]
 * (`MutableSharedFlow(extraBufferCapacity = 128)`, no replay). An earlier version
 * used `replay = 64`, which silently buffered the request + chunks and let a
 * late subscriber still see them — masking a production data-corruption bug where
 * a per-request chunk subscriber set up *after* the request was processed dropped
 * any chunk that had already been emitted. With `replay = 0` a consumer that is
 * not subscribed at emit time loses the message, exactly like production, so the
 * race-catching test below would fail on the old (late-subscribe) code path.
 */
private class FakePeerServer : PeerServer {
    private val _incoming = MutableSharedFlow<PeerMessage>(extraBufferCapacity = 128)
    override val incomingMessages: Flow<PeerMessage> = _incoming.asSharedFlow()

    /** Suspends until at least one collector is subscribed — so no emit is lost to the replay=0 flow. */
    suspend fun awaitSubscribed() {
        _incoming.subscriptionCount.first { it > 0 }
    }

    private val sent = mutableListOf<NetworkMessage>()
    private val sentSignal = MutableSharedFlow<NetworkMessage>(replay = 64, extraBufferCapacity = 64)

    override val lifecycle: Flow<ServerLifecycleState> = MutableStateFlow(ServerLifecycleState.Idle).asStateFlow()
    override val connectedPeers: Flow<Set<DeviceInfo>> = MutableStateFlow<Set<DeviceInfo>>(emptySet()).asStateFlow()
    override val pendingApprovals: Flow<DeviceInfo> = MutableSharedFlow()

    suspend fun emit(
        peer: DeviceInfo,
        message: NetworkMessage,
    ) {
        _incoming.emit(PeerMessage(peer, message))
    }

    suspend fun emitChunks(
        peer: DeviceInfo,
        transferId: String,
        bytes: ByteArray,
    ) {
        emit(
            peer,
            NetworkMessage.FileTransferStart(
                transferId = transferId,
                fileName = "upload.pdf",
                totalChunks = 1,
                totalSize = bytes.size.toLong(),
                sha256 = "",
            ),
        )
        emit(
            peer,
            NetworkMessage.FileChunk(
                transferId = transferId,
                fileName = "upload.pdf",
                chunkIndex = 0,
                totalChunks = 1,
                dataBase64 = encodeBase64(bytes),
            ),
        )
    }

    suspend fun awaitResultFor(requestId: String): NetworkMessage.LibraryMutationResult =
        withTimeout(10_000) {
            sentSignal.first { it is NetworkMessage.LibraryMutationResult && it.requestId == requestId }
                as NetworkMessage.LibraryMutationResult
        }

    override suspend fun send(
        peerId: String,
        message: NetworkMessage,
    ) {
        sent += message
        sentSignal.emit(message)
    }

    override suspend fun broadcast(message: NetworkMessage) = Unit

    override suspend fun start(): Result<ServerLifecycleState.Running> = Result.success(ServerLifecycleState.Running("h", 1, "c"))

    override suspend fun approve(peerId: String) = Unit

    override suspend fun reject(peerId: String) = Unit

    override suspend fun disconnect(peerId: String) = Unit

    override suspend fun disconnectAll() = Unit

    override suspend fun stop() = Unit
}

/** Records mutation invocations and returns canned outcomes. */
private class RecordingTarget(
    private val addOutcome: Result<LibraryMutationOutcome> = Result.success(LibraryMutationOutcome("added")),
    private val replaceOutcome: Result<LibraryMutationOutcome> = Result.success(LibraryMutationOutcome("replaced")),
    private val removeOutcome: Result<LibraryMutationOutcome> = Result.success(LibraryMutationOutcome(null)),
) : LibraryMutationTarget {
    val addCalls = mutableListOf<Pair<String, String>>() // localPath to displayName
    val replaceCalls = mutableListOf<Triple<String, String, String>>()
    val removeCalls = mutableListOf<Pair<String, String>>()
    var lastAddedBytes: ByteArray = ByteArray(0)

    override suspend fun addBook(
        targetLibraryId: String,
        localPath: String,
        displayName: String,
    ): Result<LibraryMutationOutcome> {
        addCalls += localPath to displayName
        // Capture content here, while the handler's temp file still exists.
        lastAddedBytes = runCatching { java.io.File(localPath).readBytes() }.getOrDefault(ByteArray(0))
        return addOutcome
    }

    override suspend fun removeBook(
        targetLibraryId: String,
        libraryBookId: String,
    ): Result<LibraryMutationOutcome> {
        removeCalls += targetLibraryId to libraryBookId
        return removeOutcome
    }

    override suspend fun replaceBook(
        targetLibraryId: String,
        libraryBookId: String,
        localPath: String,
        displayName: String,
    ): Result<LibraryMutationOutcome> {
        replaceCalls += Triple(libraryBookId, localPath, displayName)
        return replaceOutcome
    }
}

private object EmptyManifestProvider : LibraryManifestProvider {
    override suspend fun current(): LibraryManifest = LibraryManifest(books = emptyList())

    override suspend fun resolveAbsolutePath(id: ru.kyamshanov.notepen.sync.domain.model.BookId): String? = null
}

private object EmptyFolderRepository : FolderRepository {
    override suspend fun getAll() = emptyList<ru.kyamshanov.notepen.mainscreen.domain.model.Folder>()

    override suspend fun getFilesInFolder(folderId: String) = emptyList<String>()

    override suspend fun create(
        name: String,
        parentId: String?,
    ): ru.kyamshanov.notepen.mainscreen.domain.model.Folder = error("unused")

    override suspend fun rename(
        id: String,
        newName: String,
    ) = Unit

    override suspend fun delete(id: String) = Unit

    override suspend fun addFile(
        folderId: String,
        uri: String,
    ) = Unit

    override suspend fun removeFile(
        folderId: String,
        uri: String,
    ) = Unit
}
