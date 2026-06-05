package ru.kyamshanov.notepen.sync.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import ru.kyamshanov.notepen.drawing.api.ToolMode
import ru.kyamshanov.notepen.mainscreen.domain.model.Folder
import ru.kyamshanov.notepen.mainscreen.domain.port.FolderRepository
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.DrawingPathDto
import ru.kyamshanov.notepen.sync.domain.model.HostMessage
import ru.kyamshanov.notepen.sync.domain.model.LibraryManifest
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.model.OpenDocumentInfo
import ru.kyamshanov.notepen.sync.domain.model.PairingState
import ru.kyamshanov.notepen.sync.domain.model.PeerMessage
import ru.kyamshanov.notepen.sync.domain.model.PointDto
import ru.kyamshanov.notepen.sync.domain.model.ServerLifecycleState
import ru.kyamshanov.notepen.sync.domain.model.StrokeDelta
import ru.kyamshanov.notepen.sync.domain.port.CatalogChangeNotifier
import ru.kyamshanov.notepen.sync.domain.port.LibraryManifestProvider
import ru.kyamshanov.notepen.sync.domain.port.LocalDocumentIdRegistry
import ru.kyamshanov.notepen.sync.domain.port.PeerServer
import ru.kyamshanov.notepen.sync.domain.port.SyncClient
import ru.kyamshanov.notepen.sync.domain.projection.DocumentBroadcastController
import ru.kyamshanov.notepen.sync.infrastructure.InMemoryOpenDocumentsRegistry
import ru.kyamshanov.notepen.sync.infrastructure.InMemoryRemoteCatalogCache
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class TabletToPcBroadcastIntegrationTest {
    @Test
    fun tabletOpenDocumentTransfersFrameStrokeAndEraseToPc() =
        runTest {
            val pc = DeviceInfo(id = "pc", name = "PC", host = "127.0.0.1", port = 9000)
            val tablet = DeviceInfo(id = "tablet", name = "Tablet", host = "127.0.0.1", port = 9001)
            val lan = FakeTabletPcLan(pc = pc, tablet = tablet)
            val sourceFile = File.createTempFile("notepen-tablet-open-", ".pdf").apply { writeText("tablet-pdf") }
            val openDocuments = InMemoryOpenDocumentsRegistry()
            val tabletCatalogProvider =
                RemoteCatalogProvider(
                    hostName = tablet.name,
                    manifestProvider = BroadcastIntegrationEmptyManifestProvider,
                    folderRepository = BroadcastIntegrationEmptyFolderRepository,
                    openDocumentsProvider = openDocuments,
                )
            val pcCatalogCache = InMemoryRemoteCatalogCache()
            val pcDocumentIdRegistry = IntegrationLocalDocumentIdRegistry()

            tabletCatalogProvider.serve(client = lan.tabletClient, scope = backgroundScope)
            DocumentTransferRequestHandler(client = lan.tabletClient, provider = tabletCatalogProvider)
                .start(scope = backgroundScope)
            tabletCatalogProvider.broadcastChanges(
                notifier = SilentCatalogChangeNotifier,
                client = lan.tabletClient,
                scope = backgroundScope,
            )
            RemoteCatalogHostCoordinator(server = lan.pcServer, cache = pcCatalogCache)
                .start(scope = backgroundScope)
            runCurrent()

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

            withTimeout(1_000) {
                pcCatalogCache.catalogs.first { catalogs ->
                    catalogs.values.any { catalog ->
                        catalog.openDocuments.any { it.documentId == "tablet-doc" }
                    }
                }
            }

            val opener =
                RemoteDocumentOpener(
                    server = lan.pcServer,
                    catalogs = pcCatalogCache.catalogs,
                    destDir = createTempDirectory(prefix = "notepen-pc-received-").toFile().absolutePath,
                    documentIdRegistry = pcDocumentIdRegistry,
                    requestTimeoutMs = 1_000,
                )
            val opened = assertIs<RemoteDocumentResult.Success>(opener.open("tablet-doc"))
            assertEquals("tablet-pdf", File(opened.localPath).readText())
            assertEquals("tablet-doc", pcDocumentIdRegistry.lookup(opened.localPath))

            val pcBroadcastController =
                DocumentBroadcastController(
                    incomingMessages = lan.pcServer.incomingMessages.map { it.message },
                    sendFrame = { lan.pcServer.broadcast(it) },
                    scope = backgroundScope,
                )
            val tabletBroadcastController =
                DocumentBroadcastController(
                    incomingMessages = lan.tabletClient.incomingMessages.map { it.message },
                    sendFrame = { lan.tabletClient.broadcast(it) },
                    scope = backgroundScope,
                )
            runCurrent()

            tabletBroadcastController.updateFrame(
                documentId = "tablet-doc",
                page = 3,
                viewportOffsetX = -24f,
                viewportOffsetY = 480f,
                viewportScale = 1.75f,
                toolMode = ToolMode.ERASER,
            )

            val frame =
                withTimeout(1_000) {
                    pcBroadcastController.currentFrame.first { it?.documentId == "tablet-doc" }
                } ?: error("Expected a projection frame")
            assertEquals(3, frame.page)
            assertEquals(-24f, frame.viewportOffsetX)
            assertEquals(480f, frame.viewportOffsetY)
            assertEquals(1.75f, frame.viewportScale)
            assertEquals(ToolMode.ERASER, frame.toolMode)

            val pcRegistry =
                SyncEngineRegistry(
                    deviceId = "pc",
                    scope = backgroundScope,
                    server = lan.pcServer,
                )
            backgroundScope.launch {
                lan.pcServer.incomingMessages.collect { message ->
                    val strokeMessage = message.message as? NetworkMessage.StrokeDeltaMessage ?: return@collect
                    pcRegistry.get(strokeMessage.documentId).processPeer(strokeMessage.delta)
                }
            }
            val tabletEngine =
                SyncEngineRegistry(
                    deviceId = "tablet",
                    scope = backgroundScope,
                    client = lan.tabletClient,
                ).get("tablet-doc")
            runCurrent()

            tabletEngine.applyLocal(addedStroke())

            val added =
                withTimeout(1_000) {
                    pcRegistry.get("tablet-doc").mergedDeltas.filterIsInstance<StrokeDelta.Added>().first()
                }
            assertEquals("stroke-1", added.strokeId)
            assertEquals("tablet", added.authorDeviceId)
            assertEquals(2, added.pageIndex)

            tabletEngine.applyLocal(removedStroke())

            val removed =
                withTimeout(1_000) {
                    pcRegistry.get("tablet-doc").mergedDeltas.filterIsInstance<StrokeDelta.Removed>().first()
                }
            assertEquals("stroke-1", removed.strokeId)
            assertEquals("tablet", removed.authorDeviceId)
            assertEquals(2, removed.pageIndex)
        }

    private fun addedStroke(): StrokeDelta.Added =
        StrokeDelta.Added(
            strokeId = "stroke-1",
            pageIndex = 2,
            authorDeviceId = "tablet",
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

    private fun removedStroke(): StrokeDelta.Removed =
        StrokeDelta.Removed(
            strokeId = "stroke-1",
            pageIndex = 2,
            authorDeviceId = "tablet",
            clock = 0,
        )
}

private class FakeTabletPcLan(
    pc: DeviceInfo,
    tablet: DeviceInfo,
) {
    private val serverIncoming = MutableSharedFlow<PeerMessage>(replay = 64, extraBufferCapacity = 64)
    private val clientIncoming = MutableSharedFlow<HostMessage>(replay = 64, extraBufferCapacity = 64)

    val pcServer: PeerServer =
        object : PeerServer {
            override val lifecycle: Flow<ServerLifecycleState> = MutableStateFlow(ServerLifecycleState.Idle).asStateFlow()
            override val connectedPeers: Flow<Set<DeviceInfo>> = MutableStateFlow(setOf(tablet)).asStateFlow()
            override val pendingApprovals: Flow<DeviceInfo> = MutableSharedFlow()
            override val incomingMessages: Flow<PeerMessage> = serverIncoming.asSharedFlow()

            override suspend fun start(): Result<ServerLifecycleState.Running> = error("Not needed")

            override suspend fun send(
                peerId: String,
                message: NetworkMessage,
            ) {
                check(peerId == tablet.id)
                clientIncoming.emit(HostMessage(host = pc, message = message))
            }

            override suspend fun broadcast(message: NetworkMessage) {
                clientIncoming.emit(HostMessage(host = pc, message = message))
            }

            override suspend fun approve(peerId: String) = Unit

            override suspend fun reject(peerId: String) = Unit

            override suspend fun disconnect(peerId: String) = Unit

            override suspend fun disconnectAll() = Unit

            override suspend fun stop() = Unit
        }

    val tabletClient: SyncClient =
        object : SyncClient {
            override val pairingStates: Flow<Map<String, PairingState>> =
                MutableStateFlow(emptyMap<String, PairingState>()).asStateFlow()
            override val connectedHosts: Flow<Set<DeviceInfo>> = MutableStateFlow(setOf(pc)).asStateFlow()
            override val incomingMessages: Flow<HostMessage> = clientIncoming.asSharedFlow()

            override suspend fun connect(
                server: DeviceInfo,
                pairingCode: String,
                selfInfo: DeviceInfo,
            ): Result<DeviceInfo> = Result.success(server)

            override suspend fun send(
                hostId: String,
                message: NetworkMessage,
            ) {
                check(hostId == pc.id)
                serverIncoming.emit(PeerMessage(peer = tablet, message = message))
            }

            override suspend fun broadcast(message: NetworkMessage) {
                serverIncoming.emit(PeerMessage(peer = tablet, message = message))
            }

            override suspend fun disconnect(hostId: String) = Unit

            override suspend fun disconnectAll() = Unit
        }
}

private class IntegrationLocalDocumentIdRegistry : LocalDocumentIdRegistry {
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

private object SilentCatalogChangeNotifier : CatalogChangeNotifier {
    override val changes: Flow<Unit> = MutableSharedFlow()

    override fun notifyChanged() = Unit
}

private object BroadcastIntegrationEmptyManifestProvider : LibraryManifestProvider {
    override suspend fun current(): LibraryManifest = LibraryManifest(emptyList())

    override suspend fun resolveAbsolutePath(id: ru.kyamshanov.notepen.sync.domain.model.BookId): String? = null
}

private object BroadcastIntegrationEmptyFolderRepository : FolderRepository {
    override suspend fun create(
        name: String,
        parentId: String?,
    ): Folder = error("Not needed")

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
