package ru.kyamshanov.notepen.sync.infrastructure

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.sendSerialized
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.websocket.Frame
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import ru.kyamshanov.notepen.drawing.api.ToolMode
import ru.kyamshanov.notepen.mainscreen.domain.model.Folder
import ru.kyamshanov.notepen.mainscreen.domain.port.FolderRepository
import ru.kyamshanov.notepen.sync.domain.Direction
import ru.kyamshanov.notepen.sync.domain.DocumentTransferRequestHandler
import ru.kyamshanov.notepen.sync.domain.RemoteCatalogHostCoordinator
import ru.kyamshanov.notepen.sync.domain.RemoteCatalogProvider
import ru.kyamshanov.notepen.sync.domain.RemoteDocumentOpener
import ru.kyamshanov.notepen.sync.domain.RemoteDocumentResult
import ru.kyamshanov.notepen.sync.domain.SessionCipher
import ru.kyamshanov.notepen.sync.domain.SyncEngineRegistry
import ru.kyamshanov.notepen.sync.domain.model.BookId
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.DrawingPathDto
import ru.kyamshanov.notepen.sync.domain.model.LibraryManifest
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.model.OpenDocumentInfo
import ru.kyamshanov.notepen.sync.domain.model.PointDto
import ru.kyamshanov.notepen.sync.domain.model.ServerLifecycleState
import ru.kyamshanov.notepen.sync.domain.model.StrokeDelta
import ru.kyamshanov.notepen.sync.domain.port.CatalogChangeNotifier
import ru.kyamshanov.notepen.sync.domain.port.LibraryManifestProvider
import ru.kyamshanov.notepen.sync.domain.port.LocalDocumentIdRegistry
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * End-to-end DoS-control tests for [KtorPeerServer]: an approval that times out is
 * rejected and its bookkeeping is cleaned up, and a paired peer that floods the
 * channel is closed for policy violation. Both drive a real Ktor server + client
 * over loopback.
 */
class KtorPeerServerTest {
    private val selfInfo = DeviceInfo(id = "host-1", name = "Host", host = "127.0.0.1", port = 0)
    private val peerInfo = DeviceInfo(id = "peer-1", name = "Peer", host = "127.0.0.1", port = 0)
    private val wsJson = Json { classDiscriminator = "type" }

    private fun newClient(): HttpClient =
        HttpClient(CIO) {
            install(WebSockets) {
                maxFrameSize = MAX_WS_FRAME_SIZE_BYTES
                contentConverter = KotlinxWebsocketSerializationConverter(wsJson)
            }
        }

    private suspend fun KtorPeerServer.startRunning(): ServerLifecycleState.Running {
        start().getOrThrow()
        return lifecycle.filterIsInstance<ServerLifecycleState.Running>().first()
    }

    private suspend fun KtorSyncClient.pairWith(server: KtorPeerServer): DeviceInfo {
        val running = server.lifecycle.filterIsInstance<ServerLifecycleState.Running>().first()
        val discoveredHost =
            DeviceInfo(
                id = "host-endpoint",
                name = "Discovered host",
                host = "127.0.0.1",
                port = running.port,
            )
        return withTimeout(5.seconds) {
            connect(
                server = discoveredHost,
                pairingCode = running.code,
                selfInfo = peerInfo,
            )
        }.getOrThrow()
    }

    /**
     * Drives the client side of the cleartext pairing handshake plus the encrypted
     * key-confirmation, mirroring [KtorSyncClient]. Sends `PairRequest` with a fresh
     * nonce, awaits `PairAccepted`, derives the [SessionCipher], verifies the host's
     * encrypted hello and replies with the hello-ack. Returns the established cipher
     * so the test can then exchange encrypted [NetworkMessage]s, or `null` if the
     * server did not accept (caller asserts on that).
     */
    private suspend fun DefaultClientWebSocketSession.pairAndConfirm(
        code: String,
        self: DeviceInfo,
    ): SessionCipher? {
        val clientNonce = SecureChannel.newNonce()
        sendSerialized<NetworkMessage>(NetworkMessage.PairRequest(code, self, clientNonce))
        val accepted = receiveDeserialized<NetworkMessage>() as? NetworkMessage.PairAccepted ?: return null
        val cipher = SecureChannel.deriveCipher(code, clientNonce, accepted.serverNonce)
        val hello = SecureChannel.nextBinaryFrame(this) ?: return null
        SecureChannel.verifyHello(cipher, Direction.SERVER_TO_CLIENT, hello, expectedPeerNonce = accepted.serverNonce)
        send(Frame.Binary(fin = true, data = SecureChannel.buildHello(cipher, Direction.CLIENT_TO_SERVER, clientNonce)))
        return cipher
    }

    @Test
    fun approvalTimeoutRejectsThePeerAndCleansUp() =
        runBlocking {
            // Short real-clock approval window: withTimeoutOrNull uses the dispatcher's
            // delay (not the injected `now`), so a small Duration makes the wait fire fast.
            val server =
                KtorPeerServer(
                    selfInfo = selfInfo,
                    ioDispatcher = Dispatchers.IO,
                    approvalTimeout = 300.milliseconds,
                )
            // Count how many times THIS peer is announced for approval. After the first
            // attempt times out and is cleaned up, a reconnect must produce a SECOND
            // announcement (proving pendingPeers/approvalDeferreds were cleared, not stuck
            // in "approval in progress").
            val approvals = CompletableDeferred<Int>()
            val collectorReady = CompletableDeferred<Unit>()
            val scope = this
            val collector =
                scope.launch {
                    var seen = 0
                    server.pendingApprovals
                        .onSubscription { collectorReady.complete(Unit) }
                        .collect {
                            if (it.id == peerInfo.id) {
                                seen += 1
                                if (seen == 2) approvals.complete(seen)
                            }
                        }
                }
            val client = newClient()
            try {
                val running = server.startRunning()
                // Don't connect until the approval collector is actually subscribed, else the
                // first (replay-less) pendingApprovals emission could be missed.
                withTimeout(5.seconds) { collectorReady.await() }

                // First attempt: open a session, present the valid code, then never approve.
                // The server's bounded approval wait elapses and rejects us with a clear reason.
                val firstReply = CompletableDeferred<NetworkMessage>()
                client.webSocket(host = "127.0.0.1", port = running.port, path = "/ws") {
                    sendSerialized<NetworkMessage>(NetworkMessage.PairRequest(running.code, peerInfo))
                    firstReply.complete(receiveDeserialized<NetworkMessage>())
                }
                val reply = withTimeout(5.seconds) { firstReply.await() }
                assertTrue(
                    reply is NetworkMessage.PairRejected,
                    "a peer that is never approved must be rejected on timeout, got $reply",
                )
                assertEquals("approval timed out", reply.reason)

                // Second attempt with the same device id: if cleanup worked, the server prompts
                // again (a fresh pending approval) rather than refusing with "approval in progress".
                scope.launch {
                    runCatching {
                        client.webSocket(host = "127.0.0.1", port = running.port, path = "/ws") {
                            sendSerialized<NetworkMessage>(NetworkMessage.PairRequest(running.code, peerInfo))
                            receiveDeserialized<NetworkMessage>()
                        }
                    }
                }
                val seenCount = withTimeout(5.seconds) { approvals.await() }
                assertEquals(2, seenCount, "reconnect after timeout must yield a second approval prompt")
            } finally {
                collector.cancel()
                runCatching { client.close() }
                server.stop()
            }
        }

    @Test
    fun messageFloodClosesTheSession() =
        runBlocking {
            // Tiny per-session budget so a short burst trips the flood guard.
            val server =
                KtorPeerServer(
                    selfInfo = selfInfo,
                    ioDispatcher = Dispatchers.IO,
                    maxMessagesPerSecond = 5,
                )
            val scope = this
            val approverReady = CompletableDeferred<Unit>()
            // Auto-approve the peer as soon as it is announced so we reach the receive loop.
            val approver =
                scope.launch {
                    server.pendingApprovals
                        .onSubscription { approverReady.complete(Unit) }
                        .collect { server.approve(it.id) }
                }
            val client = newClient()
            try {
                val running = server.startRunning()
                withTimeout(5.seconds) { approverReady.await() }
                // Distinguishes "paired, flooded, then closed by the server" (the signal we want)
                // from a handshake that never paired (which must fail the test, not pass it).
                val paired = CompletableDeferred<Boolean>()
                val closedAfterFlood = CompletableDeferred<Boolean>()
                scope.launch {
                    runCatching {
                        client.webSocket(host = "127.0.0.1", port = running.port, path = "/ws") {
                            // Full pair + key confirmation; the flood guard lives past it.
                            val cipher = pairAndConfirm(running.code, peerInfo)
                            paired.complete(cipher != null)
                            if (cipher == null) return@webSocket
                            // Flood well past the budget of 5 messages/second, now over the
                            // encrypted channel exactly as a real client would.
                            repeat(50) {
                                SecureChannel.sendEncrypted(this, cipher, Direction.CLIENT_TO_SERVER, wsJson, NetworkMessage.Ping)
                            }
                            // The server closes for VIOLATED_POLICY; our next receive throws and ends the loop.
                            while (true) {
                                SecureChannel.receiveEncrypted(this, cipher, Direction.SERVER_TO_CLIENT, wsJson) ?: break
                            }
                        }
                    }
                    if (!paired.isCompleted) paired.complete(false)
                    closedAfterFlood.complete(true)
                }
                assertTrue(withTimeout(5.seconds) { paired.await() }, "peer should pair before flooding")
                assertTrue(
                    withTimeout(10.seconds) { closedAfterFlood.await() },
                    "a flooding peer must have its session closed by the server",
                )
            } finally {
                approver.cancel()
                runCatching { client.close() }
                server.stop()
            }
        }

    @Test
    fun encryptedProjectionFrameFromPeerReachesIncomingMessages() =
        runBlocking {
            val server =
                KtorPeerServer(
                    selfInfo = selfInfo,
                    ioDispatcher = Dispatchers.IO,
                )
            val scope = this
            val approverReady = CompletableDeferred<Unit>()
            val approver =
                scope.launch {
                    server.pendingApprovals
                        .onSubscription { approverReady.complete(Unit) }
                        .collect { server.approve(it.id) }
                }
            val client = newClient()
            try {
                val running = server.startRunning()
                withTimeout(5.seconds) { approverReady.await() }

                val incoming =
                    async {
                        withTimeout(5.seconds) {
                            server.incomingMessages.first { it.peer.id == peerInfo.id }
                        }
                    }
                client.webSocket(host = "127.0.0.1", port = running.port, path = "/ws") {
                    val cipher = pairAndConfirm(running.code, peerInfo)
                    assertTrue(cipher != null, "peer should pair before sending projection frame")
                    SecureChannel.sendEncrypted(
                        session = this,
                        cipher = cipher,
                        direction = Direction.CLIENT_TO_SERVER,
                        json = wsJson,
                        message =
                            NetworkMessage.ProjectionFrame(
                                documentId = "tablet-doc",
                                page = 3,
                                viewportOffsetX = -12f,
                                viewportOffsetY = 240f,
                                viewportScale = 1.5f,
                                toolMode = ToolMode.MARKER,
                            ),
                    )
                }

                val frame = assertIs<NetworkMessage.ProjectionFrame>(incoming.await().message)
                assertEquals("tablet-doc", frame.documentId)
                assertEquals(3, frame.page)
                assertEquals(-12f, frame.viewportOffsetX)
                assertEquals(240f, frame.viewportOffsetY)
                assertEquals(1.5f, frame.viewportScale)
                assertEquals(ToolMode.MARKER, frame.toolMode)
            } finally {
                approver.cancel()
                runCatching { client.close() }
                server.stop()
            }
        }

    @Test
    fun realSyncClientBroadcastsProjectionFrameToServer() =
        runBlocking {
            val server =
                KtorPeerServer(
                    selfInfo = selfInfo,
                    ioDispatcher = Dispatchers.IO,
                )
            val scope = this
            val approverReady = CompletableDeferred<Unit>()
            val approver =
                scope.launch {
                    server.pendingApprovals
                        .onSubscription { approverReady.complete(Unit) }
                        .collect { server.approve(it.id) }
                }
            val httpClient = newClient()
            val syncClient = KtorSyncClient(httpClient)
            try {
                val running = server.startRunning()
                withTimeout(5.seconds) { approverReady.await() }

                val pairedHost = syncClient.pairWith(server)
                assertEquals(selfInfo.id, pairedHost.id)

                val incoming =
                    async {
                        withTimeout(5.seconds) {
                            server.incomingMessages.first {
                                it.peer.id == peerInfo.id && it.message is NetworkMessage.ProjectionFrame
                            }
                        }
                    }
                syncClient.broadcast(
                    NetworkMessage.ProjectionFrame(
                        documentId = "tablet-doc",
                        page = 4,
                        viewportOffsetX = 18f,
                        viewportOffsetY = 360f,
                        viewportScale = 2.25f,
                        toolMode = ToolMode.ERASER,
                    ),
                )

                val frame = assertIs<NetworkMessage.ProjectionFrame>(incoming.await().message)
                assertEquals("tablet-doc", frame.documentId)
                assertEquals(4, frame.page)
                assertEquals(18f, frame.viewportOffsetX)
                assertEquals(360f, frame.viewportOffsetY)
                assertEquals(2.25f, frame.viewportScale)
                assertEquals(ToolMode.ERASER, frame.toolMode)
            } finally {
                syncClient.disconnectAll()
                approver.cancel()
                runCatching { httpClient.close() }
                server.stop()
            }
        }

    @Test
    fun realSyncClientBroadcastsStrokeAndEraseToServerSyncEngine() =
        runBlocking {
            val server =
                KtorPeerServer(
                    selfInfo = selfInfo,
                    ioDispatcher = Dispatchers.IO,
                )
            val scope = this
            val approverReady = CompletableDeferred<Unit>()
            val approver =
                scope.launch {
                    server.pendingApprovals
                        .onSubscription { approverReady.complete(Unit) }
                        .collect { server.approve(it.id) }
                }
            val httpClient = newClient()
            val syncClient = KtorSyncClient(httpClient)
            try {
                server.startRunning()
                withTimeout(5.seconds) { approverReady.await() }

                val pairedHost = syncClient.pairWith(server)
                assertEquals(selfInfo.id, pairedHost.id)

                val pcRegistry =
                    SyncEngineRegistry(
                        deviceId = selfInfo.id,
                        scope = scope,
                        server = server,
                    )
                val serverCollector =
                    scope.launch {
                        server.incomingMessages.collect { peerMessage ->
                            val message = peerMessage.message as? NetworkMessage.StrokeDeltaMessage ?: return@collect
                            pcRegistry.get(message.documentId).processPeer(message.delta)
                        }
                    }
                val tabletEngine =
                    SyncEngineRegistry(
                        deviceId = peerInfo.id,
                        scope = scope,
                        client = syncClient,
                    ).get("tablet-doc")

                tabletEngine.applyLocal(testAddedStroke())

                val added =
                    withTimeout(5.seconds) {
                        pcRegistry.get("tablet-doc").mergedDeltas.filterIsInstance<StrokeDelta.Added>().first()
                    }
                assertEquals("stroke-1", added.strokeId)
                assertEquals(peerInfo.id, added.authorDeviceId)
                assertEquals(2, added.pageIndex)

                tabletEngine.applyLocal(testRemovedStroke())

                val removed =
                    withTimeout(5.seconds) {
                        pcRegistry.get("tablet-doc").mergedDeltas.filterIsInstance<StrokeDelta.Removed>().first()
                    }
                assertEquals("stroke-1", removed.strokeId)
                assertEquals(peerInfo.id, removed.authorDeviceId)
                assertEquals(2, removed.pageIndex)
                serverCollector.cancel()
            } finally {
                syncClient.disconnectAll()
                approver.cancel()
                runCatching { httpClient.close() }
                server.stop()
            }
        }

    @Test
    fun realSyncClientTransfersTabletOpenDocumentToServerOpener() =
        runBlocking {
            val server =
                KtorPeerServer(
                    selfInfo = selfInfo,
                    ioDispatcher = Dispatchers.IO,
                )
            val scope = this
            val approverReady = CompletableDeferred<Unit>()
            val approver =
                scope.launch {
                    server.pendingApprovals
                        .onSubscription { approverReady.complete(Unit) }
                        .collect { server.approve(it.id) }
                }
            val httpClient = newClient()
            val syncClient = KtorSyncClient(httpClient)
            val sourceFile = File.createTempFile("notepen-ktor-tablet-open-", ".pdf").apply { writeText("tablet-pdf") }
            val openDocuments = InMemoryOpenDocumentsRegistry()
            val backgroundScope = CoroutineScope(coroutineContext + SupervisorJob())
            val tabletCatalogProvider =
                RemoteCatalogProvider(
                    hostName = peerInfo.name,
                    manifestProvider = EmptyLibraryManifestProvider,
                    folderRepository = EmptyFolderRepository,
                    openDocumentsProvider = openDocuments,
                )
            val pcCatalogCache = InMemoryRemoteCatalogCache()
            val registry = KtorLocalDocumentIdRegistry()
            try {
                tabletCatalogProvider.serve(client = syncClient, scope = backgroundScope)
                DocumentTransferRequestHandler(client = syncClient, provider = tabletCatalogProvider).start(backgroundScope)
                tabletCatalogProvider.broadcastChanges(
                    notifier = SilentCatalogChangeNotifier,
                    client = syncClient,
                    scope = backgroundScope,
                )
                RemoteCatalogHostCoordinator(server = server, cache = pcCatalogCache).start(backgroundScope)

                server.startRunning()
                withTimeout(5.seconds) { approverReady.await() }
                val pairedHost = syncClient.pairWith(server)
                assertEquals(selfInfo.id, pairedHost.id)

                openDocuments.publish(
                    listOf(
                        OpenDocumentInfo(
                            documentId = "tablet-doc",
                            displayName = "tablet.pdf",
                            absolutePath = sourceFile.absolutePath,
                            fileSize = sourceFile.length(),
                        ),
                    ),
                )
                withTimeout(5.seconds) {
                    pcCatalogCache.catalogs.first { catalogs ->
                        catalogs.values.any { catalog ->
                            catalog.openDocuments.any { it.documentId == "tablet-doc" }
                        }
                    }
                }

                val opener =
                    RemoteDocumentOpener(
                        server = server,
                        catalogs = pcCatalogCache.catalogs,
                        destDir = createTempDirectory(prefix = "notepen-ktor-pc-received-").toFile().absolutePath,
                        documentIdRegistry = registry,
                        requestTimeoutMs = 5_000,
                    )

                val opened = assertIs<RemoteDocumentResult.Success>(opener.open("tablet-doc"))
                assertEquals("tablet-doc", opened.documentId)
                assertEquals("tablet-pdf", File(opened.localPath).readText())
                assertEquals("tablet-doc", registry.lookup(opened.localPath))
            } finally {
                backgroundScope.cancel()
                syncClient.disconnectAll()
                approver.cancel()
                runCatching { httpClient.close() }
                server.stop()
                sourceFile.delete()
            }
        }

    private fun testAddedStroke(): StrokeDelta.Added =
        StrokeDelta.Added(
            strokeId = "stroke-1",
            pageIndex = 2,
            authorDeviceId = peerInfo.id,
            clock = 0,
            path =
                DrawingPathDto(
                    strokeId = "stroke-1",
                    colorArgb = 0xFF000000,
                    strokeWidth = 0.002f,
                    points =
                        listOf(
                            PointDto(x = 0.1f, y = 0.2f, isNewPath = true),
                            PointDto(x = 0.3f, y = 0.4f),
                        ),
                ),
        )

    private fun testRemovedStroke(): StrokeDelta.Removed =
        StrokeDelta.Removed(
            strokeId = "stroke-1",
            pageIndex = 2,
            authorDeviceId = peerInfo.id,
            clock = 0,
        )
}

private object EmptyLibraryManifestProvider : LibraryManifestProvider {
    override suspend fun current(): LibraryManifest = LibraryManifest(emptyList())

    override suspend fun resolveAbsolutePath(id: BookId): String? = null
}

private object EmptyFolderRepository : FolderRepository {
    override suspend fun create(
        name: String,
        parentId: String?,
    ): Folder = Folder(id = "folder", name = name, createdAt = 0L, parentId = parentId)

    override suspend fun delete(id: String) = Unit

    override suspend fun addFile(
        folderId: String,
        uri: String,
    ) = Unit

    override suspend fun removeFile(
        folderId: String,
        uri: String,
    ) = Unit

    override suspend fun rename(
        id: String,
        newName: String,
    ) = Unit

    override suspend fun getAll(): List<Folder> = emptyList()

    override suspend fun getFilesInFolder(folderId: String): List<String> = emptyList()
}

private object SilentCatalogChangeNotifier : CatalogChangeNotifier {
    override val changes: Flow<Unit> = emptyFlow()

    override fun notifyChanged() = Unit
}

private class KtorLocalDocumentIdRegistry : LocalDocumentIdRegistry {
    private val byPath = mutableMapOf<String, String>()

    override fun lookup(localPath: String): String? = byPath[localPath]

    override suspend fun register(
        localPath: String,
        documentId: String,
    ) {
        byPath[localPath] = documentId
    }

    override suspend fun forget(localPath: String) {
        byPath.remove(localPath)
    }
}
