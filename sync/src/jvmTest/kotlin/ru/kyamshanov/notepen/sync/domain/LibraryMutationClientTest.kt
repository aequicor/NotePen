package ru.kyamshanov.notepen.sync.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import ru.kyamshanov.notepen.document.domain.sha256Hex
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.HostMessage
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.model.PairingState
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibraryMutationClientTest {
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private val hostId = "host-1"
    private val host = DeviceInfo(id = hostId, name = "Studio Mac", host = "10.0.0.5", port = 8080)
    private val tempDir: File = Files.createTempDirectory("libmut-client-test").toFile()

    @AfterTest
    fun teardown() {
        scope.coroutineContext[Job]?.cancel()
        tempDir.deleteRecursively()
    }

    @Test
    fun addBook_sendsRequestThenChunks_resolvesOnOkResult() =
        runBlocking {
            val payload = "%PDF-1.4 book bytes".encodeToByteArray()
            val srcFile = File(tempDir, "book.pdf").apply { writeBytes(payload) }
            // Auto-reply ok with a backend id once the request arrives, echoing requestId.
            val client = FakeSyncClient(autoReply = { req -> okResult(req, newId = "book.pdf") })
            client.start(scope)

            val result =
                client.mutationClient().addBook(
                    targetLibraryId = "",
                    localPath = srcFile.absolutePath,
                    displayName = "book.pdf",
                )

            assertTrue(result.isSuccess, "ok result resolves to success")
            assertEquals("book.pdf", result.getOrThrow().newLibraryBookId)

            // The request carried the correct size + sha; chunks were streamed with transferId == requestId.
            val req = client.sent.filterIsInstance<NetworkMessage.LibraryAddRequest>().single()
            assertEquals(payload.size.toLong(), req.fileSize)
            assertEquals(sha256Hex(payload), req.contentSha256)
            val chunks = client.sent.filterIsInstance<NetworkMessage.FileChunk>()
            assertTrue(chunks.isNotEmpty(), "at least one chunk streamed")
            assertTrue(chunks.all { it.transferId == req.requestId }, "chunks correlate to the request")
            val start = client.sent.filterIsInstance<NetworkMessage.FileTransferStart>().single()
            assertEquals(req.requestId, start.transferId)
        }

    @Test
    fun addBook_resolvesFailure_whenHostRejects() =
        runBlocking {
            val srcFile = File(tempDir, "x.pdf").apply { writeBytes("bytes".encodeToByteArray()) }
            val client =
                FakeSyncClient(
                    autoReply = { req ->
                        NetworkMessage.LibraryMutationResult(req.requestId, ok = false, error = "Not authorised")
                    },
                )
            client.start(scope)

            val result =
                client.mutationClient().addBook(targetLibraryId = "", localPath = srcFile.absolutePath, displayName = "x.pdf")

            assertTrue(result.isFailure)
            assertEquals("Not authorised", result.exceptionOrNull()?.message)
        }

    @Test
    fun removeBook_sendsRequest_noChunks_resolvesFailureOnUnsupported() =
        runBlocking {
            val client =
                FakeSyncClient(
                    autoReplyRemove = { req ->
                        NetworkMessage.LibraryMutationResult(req.requestId, ok = false, error = "removal unsupported")
                    },
                )
            client.start(scope)

            val result = client.mutationClient().removeBook(targetLibraryId = "", libraryBookId = "book.pdf")

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("unsupported", ignoreCase = true) == true)
            assertFalse(
                client.sent.any { it is NetworkMessage.FileChunk },
                "removal uploads no file",
            )
            assertEquals(1, client.sent.filterIsInstance<NetworkMessage.LibraryRemoveRequest>().size)
        }

    private fun okResult(
        req: NetworkMessage.LibraryAddRequest,
        newId: String?,
    ) = NetworkMessage.LibraryMutationResult(req.requestId, ok = true, newLibraryBookId = newId)

    private fun FakeSyncClient.mutationClient(): LibraryMutationClient {
        var counter = 0
        return LibraryMutationClient(
            client = this,
            hostId = hostId,
            newRequestId = { "req-${counter++}" },
            timeoutMs = 10_000L,
        )
    }

    /**
     * In-memory [ru.kyamshanov.notepen.sync.domain.port.SyncClient] that records sent messages and,
     * when [start]ed, watches its own outbound stream for a library request and emits the configured
     * [NetworkMessage.LibraryMutationResult] back through [incomingMessages] — closing the loop the
     * real host would close over the network.
     */
    private inner class FakeSyncClient(
        private val autoReply: (NetworkMessage.LibraryAddRequest) -> NetworkMessage.LibraryMutationResult = {
            NetworkMessage.LibraryMutationResult(it.requestId, ok = true)
        },
        private val autoReplyRemove: (NetworkMessage.LibraryRemoveRequest) -> NetworkMessage.LibraryMutationResult = {
            NetworkMessage.LibraryMutationResult(it.requestId, ok = true)
        },
    ) : ru.kyamshanov.notepen.sync.domain.port.SyncClient {
        val sent = mutableListOf<NetworkMessage>()
        private val outbound = MutableSharedFlow<NetworkMessage>(replay = 64, extraBufferCapacity = 64)
        private val _incoming = MutableSharedFlow<HostMessage>(replay = 64, extraBufferCapacity = 64)

        override val pairingStates: Flow<Map<String, PairingState>> = flowOf(emptyMap())
        override val connectedHosts: Flow<Set<DeviceInfo>> = MutableStateFlow(setOf(host))
        override val incomingMessages: Flow<HostMessage> = _incoming.asSharedFlow()

        /** Drives the auto-reply: watch the outbound stream and emit a result when the request lands. */
        fun start(scope: CoroutineScope) {
            scope.launch {
                outbound.collect { msg ->
                    when (msg) {
                        is NetworkMessage.LibraryAddRequest -> _incoming.emit(HostMessage(host, autoReply(msg)))
                        is NetworkMessage.LibraryRemoveRequest -> _incoming.emit(HostMessage(host, autoReplyRemove(msg)))
                        else -> Unit
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
}
