package ru.kyamshanov.notepen.sync.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.HostMessage
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.model.PairingState
import ru.kyamshanov.notepen.sync.domain.model.PeerMessage
import ru.kyamshanov.notepen.sync.domain.model.RemoteCatalog
import ru.kyamshanov.notepen.sync.domain.model.RemoteEntry
import ru.kyamshanov.notepen.sync.domain.model.ServerLifecycleState
import ru.kyamshanov.notepen.sync.domain.port.LocalDocumentIdRegistry
import ru.kyamshanov.notepen.sync.domain.port.PeerServer
import ru.kyamshanov.notepen.sync.domain.port.SyncClient
import ru.kyamshanov.notepen.sync.infrastructure.encodeBase64
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteDocumentOpenerTest {
    @Test
    fun openPicksHostByOpenDocumentsCatalogEntry() =
        runTest {
            val host = DeviceInfo(id = "tablet", name = "Tablet", host = "127.0.0.1", port = 1)
            val client = OpenerFakeSyncClient()
            val opener =
                RemoteDocumentOpener(
                    client = client,
                    catalogs =
                        MutableStateFlow(
                            mapOf(
                                host to
                                    RemoteCatalog(
                                        hostName = "Tablet",
                                        recent = emptyList(),
                                        folders = emptyList(),
                                        folderLinks = emptyList(),
                                        openDocuments =
                                            listOf(
                                                RemoteEntry(
                                                    documentId = "tablet-doc",
                                                    displayName = "tablet.pdf",
                                                    lastOpenedAt = 0L,
                                                ),
                                            ),
                                    ),
                            ),
                        ),
                    destDir = "/tmp",
                    requestTimeoutMs = 1_000,
                )

            val result = opener.open("tablet-doc")

            assertIs<RemoteDocumentResult.NotFound>(result)
            assertEquals("tablet", client.sent.single().first)
            assertEquals(NetworkMessage.DocumentOpenRequest("tablet-doc"), client.sent.single().second)
        }

    @Test
    fun openReceivesFileTransferAndRegistersDocumentId() =
        runTest {
            val host = DeviceInfo(id = "tablet", name = "Tablet", host = "127.0.0.1", port = 1)
            val client =
                OpenerFileSyncClient(
                    host = host,
                    documentId = "tablet-doc",
                    fileName = "tablet.pdf",
                    bytes = "pdf-bytes".encodeToByteArray(),
                )
            val registry = FakeLocalDocumentIdRegistry()
            val destDir = createTempDirectory(prefix = "notepen-opener-").toFile().absolutePath
            val opener =
                RemoteDocumentOpener(
                    client = client,
                    catalogs =
                        MutableStateFlow(
                            mapOf(
                                host to
                                    RemoteCatalog(
                                        hostName = "Tablet",
                                        recent = emptyList(),
                                        folders = emptyList(),
                                        folderLinks = emptyList(),
                                        openDocuments =
                                            listOf(
                                                RemoteEntry(
                                                    documentId = "tablet-doc",
                                                    displayName = "tablet.pdf",
                                                    lastOpenedAt = 0L,
                                                ),
                                            ),
                                    ),
                            ),
                        ),
                    destDir = destDir,
                    documentIdRegistry = registry,
                    requestTimeoutMs = 1_000,
                )

            val result = assertIs<RemoteDocumentResult.Success>(opener.open("tablet-doc"))

            assertEquals("tablet-doc", result.documentId)
            assertEquals("pdf-bytes", File(result.localPath).readText())
            assertEquals("tablet-doc", registry.lookup(result.localPath))
        }

    @Test
    fun openUsesCachedFileNamedFromCatalogWhenDisplayNameIsNotProvided() =
        runTest {
            val host = DeviceInfo(id = "tablet", name = "Tablet", host = "127.0.0.1", port = 1)
            val client = OpenerFakeSyncClient(failOnSend = true)
            val registry = FakeLocalDocumentIdRegistry()
            val destDir = createTempDirectory(prefix = "notepen-opener-cached-").toFile()
            val cached =
                File(
                    destDir,
                    documentIdToCacheFileName(
                        documentId = "tablet-doc",
                        displayName = "tablet.pdf",
                    ),
                ).apply { writeText("cached-pdf") }
            val opener =
                RemoteDocumentOpener(
                    client = client,
                    catalogs =
                        MutableStateFlow(
                            mapOf(
                                host to
                                    RemoteCatalog(
                                        hostName = "Tablet",
                                        recent = emptyList(),
                                        folders = emptyList(),
                                        folderLinks = emptyList(),
                                        openDocuments =
                                            listOf(
                                                RemoteEntry(
                                                    documentId = "tablet-doc",
                                                    displayName = "tablet.pdf",
                                                    lastOpenedAt = 0L,
                                                ),
                                            ),
                                    ),
                            ),
                        ),
                    destDir = destDir.absolutePath,
                    documentIdRegistry = registry,
                    requestTimeoutMs = 1_000,
                )

            val result = assertIs<RemoteDocumentResult.Success>(opener.open("tablet-doc"))

            assertEquals(cached.absolutePath, result.localPath)
            assertEquals("cached-pdf", File(result.localPath).readText())
            assertEquals("tablet-doc", registry.lookup(result.localPath))
            assertEquals(emptyList(), client.sent)
        }

    @Test
    fun openReceivesFileTransferFromConnectedPeerViaServerAndRegistersDocumentId() =
        runTest {
            val peer = DeviceInfo(id = "tablet-peer", name = "Tablet", host = "127.0.0.1", port = 1)
            val server =
                OpenerFilePeerServer(
                    peer = peer,
                    documentId = "tablet-doc",
                    fileName = "tablet.pdf",
                    bytes = "pdf-bytes-from-peer".encodeToByteArray(),
                )
            val registry = FakeLocalDocumentIdRegistry()
            val destDir = createTempDirectory(prefix = "notepen-opener-peer-").toFile().absolutePath
            val opener =
                RemoteDocumentOpener(
                    server = server,
                    catalogs =
                        MutableStateFlow(
                            mapOf(
                                peer to
                                    RemoteCatalog(
                                        hostName = "Tablet",
                                        recent = emptyList(),
                                        folders = emptyList(),
                                        folderLinks = emptyList(),
                                        openDocuments =
                                            listOf(
                                                RemoteEntry(
                                                    documentId = "tablet-doc",
                                                    displayName = "tablet.pdf",
                                                    lastOpenedAt = 0L,
                                                ),
                                            ),
                                    ),
                            ),
                        ),
                    destDir = destDir,
                    documentIdRegistry = registry,
                    requestTimeoutMs = 1_000,
                )

            val result = assertIs<RemoteDocumentResult.Success>(opener.open("tablet-doc"))

            assertEquals("tablet-doc", result.documentId)
            assertEquals("pdf-bytes-from-peer", File(result.localPath).readText())
            assertEquals("tablet-doc", registry.lookup(result.localPath))
            assertEquals("tablet-peer", server.sent.single().first)
            assertEquals(NetworkMessage.DocumentOpenRequest("tablet-doc"), server.sent.single().second)
        }

    @Test
    fun openCanRequestDocumentFromConnectedPeerViaServer() =
        runTest {
            val peer = DeviceInfo(id = "tablet-peer", name = "Tablet", host = "127.0.0.1", port = 1)
            val server = OpenerFakePeerServer(peer)
            val opener =
                RemoteDocumentOpener(
                    server = server,
                    catalogs =
                        MutableStateFlow(
                            mapOf(
                                peer to
                                    RemoteCatalog(
                                        hostName = "Tablet",
                                        recent = emptyList(),
                                        folders = emptyList(),
                                        folderLinks = emptyList(),
                                        openDocuments =
                                            listOf(
                                                RemoteEntry(
                                                    documentId = "tablet-doc",
                                                    displayName = "tablet.pdf",
                                                    lastOpenedAt = 0L,
                                                ),
                                            ),
                                    ),
                            ),
                        ),
                    destDir = "/tmp",
                    requestTimeoutMs = 1_000,
                )

            val result = opener.open("tablet-doc")

            assertIs<RemoteDocumentResult.NotFound>(result)
            assertEquals("tablet-peer", server.sent.single().first)
            assertEquals(NetworkMessage.DocumentOpenRequest("tablet-doc"), server.sent.single().second)
        }

    @Test
    fun openRoutesToPeerServerWhenClientAndServerAreBothPresent() =
        runTest {
            val peer = DeviceInfo(id = "tablet-peer", name = "Tablet", host = "127.0.0.1", port = 1)
            val client = OpenerFakeSyncClient(failOnSend = true)
            val server = OpenerFakePeerServer(peer)
            val opener =
                RemoteDocumentOpener(
                    client = client,
                    server = server,
                    catalogs =
                        MutableStateFlow(
                            mapOf(
                                peer to
                                    RemoteCatalog(
                                        hostName = "Tablet",
                                        recent = emptyList(),
                                        folders = emptyList(),
                                        folderLinks = emptyList(),
                                        openDocuments =
                                            listOf(
                                                RemoteEntry(
                                                    documentId = "tablet-doc",
                                                    displayName = "tablet.pdf",
                                                    lastOpenedAt = 0L,
                                                ),
                                            ),
                                    ),
                            ),
                        ),
                    destDir = "/tmp",
                    requestTimeoutMs = 1_000,
                )

            val result = opener.open("tablet-doc")

            assertIs<RemoteDocumentResult.NotFound>(result)
            assertEquals(emptyList(), client.sent)
            assertEquals("tablet-peer", server.sent.single().first)
            assertEquals(NetworkMessage.DocumentOpenRequest("tablet-doc"), server.sent.single().second)
        }

    @Test
    fun openWaitsForCatalogEntryBeforeRequestingDocument() =
        runTest {
            val host = DeviceInfo(id = "tablet", name = "Tablet", host = "127.0.0.1", port = 1)
            val client = OpenerFakeSyncClient()
            val catalogs = MutableStateFlow<Map<DeviceInfo, RemoteCatalog>>(emptyMap())
            val opener =
                RemoteDocumentOpener(
                    client = client,
                    catalogs = catalogs,
                    destDir = "/tmp",
                    requestTimeoutMs = 1_000,
                )

            val result = async { opener.open("tablet-doc") }
            runCurrent()
            assertEquals(emptyList(), client.sent)

            catalogs.value =
                mapOf(
                    host to
                        RemoteCatalog(
                            hostName = "Tablet",
                            recent = emptyList(),
                            folders = emptyList(),
                            folderLinks = emptyList(),
                            openDocuments =
                                listOf(
                                    RemoteEntry(
                                        documentId = "tablet-doc",
                                        displayName = "tablet.pdf",
                                        lastOpenedAt = 0L,
                                    ),
                                ),
                        ),
                )

            assertIs<RemoteDocumentResult.NotFound>(result.await())
            assertEquals("tablet", client.sent.single().first)
            assertEquals(NetworkMessage.DocumentOpenRequest("tablet-doc"), client.sent.single().second)
        }
}

private class OpenerFakeSyncClient(
    private val failOnSend: Boolean = false,
) : SyncClient {
    private val incoming = MutableSharedFlow<HostMessage>(replay = 8, extraBufferCapacity = 8)

    override val pairingStates: Flow<Map<String, PairingState>> = MutableStateFlow(emptyMap<String, PairingState>()).asStateFlow()
    override val connectedHosts: Flow<Set<DeviceInfo>> = MutableStateFlow(emptySet<DeviceInfo>()).asStateFlow()
    override val incomingMessages: Flow<HostMessage> = incoming.asSharedFlow()
    val sent = mutableListOf<Pair<String, NetworkMessage>>()

    override suspend fun connect(
        server: DeviceInfo,
        pairingCode: String,
        selfInfo: DeviceInfo,
    ): Result<DeviceInfo> = Result.success(server)

    override suspend fun send(
        hostId: String,
        message: NetworkMessage,
    ) {
        check(!failOnSend) { "SyncClient.send must not be used in this test" }
        sent += hostId to message
        if (message is NetworkMessage.DocumentOpenRequest) {
            incoming.emit(
                HostMessage(
                    host = DeviceInfo(id = hostId, name = "Tablet", host = "127.0.0.1", port = 1),
                    message = NetworkMessage.DocumentNotFound(message.documentId, "test"),
                ),
            )
        }
    }

    override suspend fun broadcast(message: NetworkMessage) = Unit

    override suspend fun disconnect(hostId: String) = Unit

    override suspend fun disconnectAll() = Unit
}

private class OpenerFileSyncClient(
    private val host: DeviceInfo,
    private val documentId: String,
    private val fileName: String,
    private val bytes: ByteArray,
) : SyncClient {
    private val incoming = MutableSharedFlow<HostMessage>(replay = 8, extraBufferCapacity = 8)

    override val pairingStates: Flow<Map<String, PairingState>> = MutableStateFlow(emptyMap<String, PairingState>()).asStateFlow()
    override val connectedHosts: Flow<Set<DeviceInfo>> = MutableStateFlow(setOf(host)).asStateFlow()
    override val incomingMessages: Flow<HostMessage> = incoming.asSharedFlow()
    val sent = mutableListOf<Pair<String, NetworkMessage>>()

    override suspend fun connect(
        server: DeviceInfo,
        pairingCode: String,
        selfInfo: DeviceInfo,
    ): Result<DeviceInfo> = Result.success(server)

    override suspend fun send(
        hostId: String,
        message: NetworkMessage,
    ) {
        sent += hostId to message
        if (message is NetworkMessage.DocumentOpenRequest) {
            val transferId = "tx-test"
            incoming.emit(
                HostMessage(
                    host = host,
                    message =
                        NetworkMessage.FileTransferStart(
                            transferId = transferId,
                            fileName = fileName,
                            totalChunks = 1,
                            totalSize = bytes.size.toLong(),
                            sha256 = "",
                            documentId = documentId,
                        ),
                ),
            )
            incoming.emit(
                HostMessage(
                    host = host,
                    message =
                        NetworkMessage.FileChunk(
                            transferId = transferId,
                            fileName = fileName,
                            chunkIndex = 0,
                            totalChunks = 1,
                            dataBase64 = encodeBase64(bytes),
                            documentId = documentId,
                        ),
                ),
            )
        }
    }

    override suspend fun broadcast(message: NetworkMessage) = Unit

    override suspend fun disconnect(hostId: String) = Unit

    override suspend fun disconnectAll() = Unit
}

private class OpenerFakePeerServer(
    private val peer: DeviceInfo,
) : PeerServer {
    private val incoming = MutableSharedFlow<PeerMessage>(replay = 8, extraBufferCapacity = 8)

    override val lifecycle: Flow<ServerLifecycleState> = MutableStateFlow(ServerLifecycleState.Idle).asStateFlow()
    override val connectedPeers: Flow<Set<DeviceInfo>> = MutableStateFlow(setOf(peer)).asStateFlow()
    override val pendingApprovals: Flow<DeviceInfo> = MutableSharedFlow()
    override val incomingMessages: Flow<PeerMessage> = incoming.asSharedFlow()
    val sent = mutableListOf<Pair<String, NetworkMessage>>()

    override suspend fun start(): Result<ServerLifecycleState.Running> = error("Not needed")

    override suspend fun send(
        peerId: String,
        message: NetworkMessage,
    ) {
        sent += peerId to message
        if (message is NetworkMessage.DocumentOpenRequest) {
            incoming.emit(
                PeerMessage(
                    peer = peer,
                    message = NetworkMessage.DocumentNotFound(message.documentId, "test"),
                ),
            )
        }
    }

    override suspend fun broadcast(message: NetworkMessage) = Unit

    override suspend fun approve(peerId: String) = Unit

    override suspend fun reject(peerId: String) = Unit

    override suspend fun disconnect(peerId: String) = Unit

    override suspend fun disconnectAll() = Unit

    override suspend fun stop() = Unit
}

private class OpenerFilePeerServer(
    private val peer: DeviceInfo,
    private val documentId: String,
    private val fileName: String,
    private val bytes: ByteArray,
) : PeerServer {
    private val incoming = MutableSharedFlow<PeerMessage>(replay = 8, extraBufferCapacity = 8)

    override val lifecycle: Flow<ServerLifecycleState> = MutableStateFlow(ServerLifecycleState.Idle).asStateFlow()
    override val connectedPeers: Flow<Set<DeviceInfo>> = MutableStateFlow(setOf(peer)).asStateFlow()
    override val pendingApprovals: Flow<DeviceInfo> = MutableSharedFlow()
    override val incomingMessages: Flow<PeerMessage> = incoming.asSharedFlow()
    val sent = mutableListOf<Pair<String, NetworkMessage>>()

    override suspend fun start(): Result<ServerLifecycleState.Running> = error("Not needed")

    override suspend fun send(
        peerId: String,
        message: NetworkMessage,
    ) {
        sent += peerId to message
        if (message is NetworkMessage.DocumentOpenRequest) {
            val transferId = "tx-peer-test"
            incoming.emit(
                PeerMessage(
                    peer = peer,
                    message =
                        NetworkMessage.FileTransferStart(
                            transferId = transferId,
                            fileName = fileName,
                            totalChunks = 1,
                            totalSize = bytes.size.toLong(),
                            sha256 = "",
                            documentId = documentId,
                        ),
                ),
            )
            incoming.emit(
                PeerMessage(
                    peer = peer,
                    message =
                        NetworkMessage.FileChunk(
                            transferId = transferId,
                            fileName = fileName,
                            chunkIndex = 0,
                            totalChunks = 1,
                            dataBase64 = encodeBase64(bytes),
                            documentId = documentId,
                        ),
                ),
            )
        }
    }

    override suspend fun broadcast(message: NetworkMessage) = Unit

    override suspend fun approve(peerId: String) = Unit

    override suspend fun reject(peerId: String) = Unit

    override suspend fun disconnect(peerId: String) = Unit

    override suspend fun disconnectAll() = Unit

    override suspend fun stop() = Unit
}

private class FakeLocalDocumentIdRegistry : LocalDocumentIdRegistry {
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
