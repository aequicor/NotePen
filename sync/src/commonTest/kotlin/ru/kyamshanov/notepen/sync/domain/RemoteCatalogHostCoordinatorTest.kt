package ru.kyamshanov.notepen.sync.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.model.PeerMessage
import ru.kyamshanov.notepen.sync.domain.model.RemoteCatalog
import ru.kyamshanov.notepen.sync.domain.model.RemoteEntry
import ru.kyamshanov.notepen.sync.domain.model.ServerLifecycleState
import ru.kyamshanov.notepen.sync.domain.port.PeerServer
import ru.kyamshanov.notepen.sync.infrastructure.InMemoryRemoteCatalogCache
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteCatalogHostCoordinatorTest {
    @Test
    fun requestsCatalogWhenPeerSignalsCatalogChanged() =
        runTest {
            val peer = DeviceInfo(id = "tablet", name = "Tablet", host = "127.0.0.1", port = 1)
            val server = HostCatalogFakePeerServer(peer)
            val cache = InMemoryRemoteCatalogCache()
            RemoteCatalogHostCoordinator(server = server, cache = cache).start(backgroundScope)
            runCurrent()
            server.sent.clear()

            server.emit(peer, NetworkMessage.RemoteCatalogChanged)
            runCurrent()

            assertEquals(
                listOf<Pair<String, NetworkMessage>>("tablet" to NetworkMessage.RemoteCatalogRequest),
                server.sent,
            )
        }

    @Test
    fun cachesOpenDocumentsFromPeerCatalogResponse() =
        runTest {
            val peer = DeviceInfo(id = "tablet", name = "Tablet", host = "127.0.0.1", port = 1)
            val server = HostCatalogFakePeerServer(peer)
            val cache = InMemoryRemoteCatalogCache()
            RemoteCatalogHostCoordinator(server = server, cache = cache).start(backgroundScope)
            runCurrent()

            server.emit(
                peer,
                NetworkMessage.RemoteCatalogResponse(
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
            )
            runCurrent()

            assertEquals(
                listOf("tablet-doc"),
                cache.catalogs.value.getValue(peer).openDocuments.map { it.documentId },
            )
        }
}

private class HostCatalogFakePeerServer(
    peer: DeviceInfo,
) : PeerServer {
    private val incoming = MutableSharedFlow<PeerMessage>(replay = 8, extraBufferCapacity = 8)

    override val lifecycle: Flow<ServerLifecycleState> = MutableStateFlow(ServerLifecycleState.Idle).asStateFlow()
    override val connectedPeers: Flow<Set<DeviceInfo>> = MutableStateFlow(setOf(peer)).asStateFlow()
    override val pendingApprovals: Flow<DeviceInfo> = MutableSharedFlow()
    override val incomingMessages: Flow<PeerMessage> = incoming.asSharedFlow()
    val sent = mutableListOf<Pair<String, NetworkMessage>>()

    suspend fun emit(
        peer: DeviceInfo,
        message: NetworkMessage,
    ) {
        incoming.emit(PeerMessage(peer = peer, message = message))
    }

    override suspend fun start(): Result<ServerLifecycleState.Running> = error("unused")

    override suspend fun send(
        peerId: String,
        message: NetworkMessage,
    ) {
        sent += peerId to message
    }

    override suspend fun broadcast(message: NetworkMessage) = Unit

    override suspend fun approve(peerId: String) = Unit

    override suspend fun reject(peerId: String) = Unit

    override suspend fun disconnect(peerId: String) = Unit

    override suspend fun disconnectAll() = Unit

    override suspend fun stop() = Unit
}
