package ru.kyamshanov.notepen

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import ru.kyamshanov.notepen.drawing.api.ToolMode
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.HostMessage
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.model.PairingState
import ru.kyamshanov.notepen.sync.domain.model.PeerMessage
import ru.kyamshanov.notepen.sync.domain.model.ServerLifecycleState
import ru.kyamshanov.notepen.sync.domain.port.PeerServer
import ru.kyamshanov.notepen.sync.domain.port.SyncClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BroadcastPresentationTest {
    private val testPageTops = floatArrayOf(0f, 1000f, 2000f, 3000f, 4000f)
    private val testPageHeights = floatArrayOf(1000f, 1000f, 1000f, 1000f, 1000f)

    @Test
    fun `broadcast tool mode is used only for matching document`() {
        val frame =
            NetworkMessage.ProjectionFrame(
                documentId = "doc-1",
                page = 0,
                viewportOffsetY = 0f,
                viewportScale = 1f,
                toolMode = ToolMode.ERASER,
            )

        assertEquals(ToolMode.ERASER, broadcastToolModeForDocument(frame, "doc-1"))
        assertNull(broadcastToolModeForDocument(frame, "doc-2"))
        assertNull(broadcastToolModeForDocument(frame, ""))
        assertNull(broadcastToolModeForDocument(null, "doc-1"))
    }

    @Test
    fun `none is a valid remote tool mode`() {
        val frame =
            NetworkMessage.ProjectionFrame(
                documentId = "doc-1",
                page = 0,
                viewportOffsetY = 0f,
                viewportScale = 1f,
                toolMode = ToolMode.NONE,
            )

        assertEquals(ToolMode.NONE, broadcastToolModeForDocument(frame, "doc-1"))
    }

    @Test
    fun `displayed tool mode uses broadcast none instead of local tool`() {
        assertEquals(
            ToolMode.NONE,
            displayedToolMode(localToolMode = ToolMode.PEN, broadcastToolMode = ToolMode.NONE),
        )
        assertEquals(
            ToolMode.MARKER,
            displayedToolMode(localToolMode = ToolMode.PEN, broadcastToolMode = ToolMode.MARKER),
        )
        assertEquals(
            ToolMode.PEN,
            displayedToolMode(localToolMode = ToolMode.PEN, broadcastToolMode = null),
        )
    }

    @Test
    fun `projected tool mode uses eraser override`() {
        assertEquals(
            ToolMode.ERASER,
            projectedToolMode(localToolMode = ToolMode.PEN, eraserOverride = true),
        )
        assertEquals(
            ToolMode.MARKER,
            projectedToolMode(localToolMode = ToolMode.MARKER, eraserOverride = false),
        )
    }

    @Test
    fun `broadcast viewport command maps matching frame to page zoom and pan`() {
        val frame =
            NetworkMessage.ProjectionFrame(
                documentId = "doc-1",
                page = 4,
                viewportOffsetX = -30f,
                viewportOffsetY = 320.6f,
                viewportScale = 1.75f,
            )

        val command =
            broadcastViewportCommandForDocument(
                frame = frame,
                documentId = "doc-1",
                currentPage = 0,
                currentPageOffsetPx = 0,
                currentScalePercent = 100,
                currentPanX = -10f,
                currentPanY = -20f,
                currentViewportWidthPx = 1200f,
                currentViewportHeightPx = 900f,
                currentRowWidthPx = 800f,
                currentPageTopsPx = testPageTops,
                currentPageHeightsPx = testPageHeights,
            )

        assertEquals(
            BroadcastViewportCommand(
                page = 4,
                pageOffsetPx = 321,
                shouldScroll = true,
                targetScalePercent = 175,
                targetPanX = null,
                targetPanY = null,
            ),
            command,
        )
    }

    @Test
    fun `broadcast viewport command with empty page layout does not crash on viewport center y`() {
        val frame =
            NetworkMessage.ProjectionFrame(
                documentId = "doc-1",
                page = 1,
                viewportOffsetY = 0f,
                viewportScale = 1f,
                viewportCenterY = 1.5f, // between pages -> fractional center
            )

        val command =
            broadcastViewportCommandForDocument(
                frame = frame,
                documentId = "doc-1",
                currentPage = 0,
                currentPageOffsetPx = 0,
                currentScalePercent = 100,
                currentPanX = 0f,
                currentPanY = 0f,
                currentViewportWidthPx = 1200f,
                currentViewportHeightPx = 900f,
                currentRowWidthPx = 800f,
                currentPageTopsPx = floatArrayOf(), // empty layout: the crash trigger
                currentPageHeightsPx = floatArrayOf(),
            )

        assertNotNull(command)
        assertNull(command.targetPanY) // no center-Y pan until local layout is ready
        assertFalse(command.shouldScroll)
    }

    @Test
    fun `broadcast viewport command ignores mismatched document and no-op deltas`() {
        val frame =
            NetworkMessage.ProjectionFrame(
                documentId = "doc-1",
                page = 2,
                viewportOffsetX = 10.2f,
                viewportOffsetY = -10f,
                viewportScale = 1f,
            )

        assertNull(
            broadcastViewportCommandForDocument(
                frame = frame,
                documentId = "doc-2",
                currentPage = 2,
                currentPageOffsetPx = 0,
                currentScalePercent = 100,
                currentPanX = 10f,
                currentPanY = -20f,
                currentViewportWidthPx = 1200f,
                currentViewportHeightPx = 900f,
                currentRowWidthPx = 800f,
                currentPageTopsPx = testPageTops,
                currentPageHeightsPx = testPageHeights,
            ),
        )
        assertEquals(
            BroadcastViewportCommand(
                page = 2,
                pageOffsetPx = 0,
                shouldScroll = false,
                targetScalePercent = null,
                targetPanX = null,
                targetPanY = null,
            ),
            broadcastViewportCommandForDocument(
                frame = frame,
                documentId = "doc-1",
                currentPage = 2,
                currentPageOffsetPx = 0,
                currentScalePercent = 100,
                currentPanX = 10f,
                currentPanY = -20f,
                currentViewportWidthPx = 1200f,
                currentViewportHeightPx = 900f,
                currentRowWidthPx = 800f,
                currentPageTopsPx = testPageTops,
                currentPageHeightsPx = testPageHeights,
            ),
        )
    }

    @Test
    fun `broadcast viewport command sanitizes invalid network values`() {
        val frame =
            NetworkMessage.ProjectionFrame(
                documentId = "doc-1",
                page = -3,
                viewportOffsetX = Float.NaN,
                viewportOffsetY = Float.NaN,
                viewportScale = Float.NaN,
            )

        assertEquals(
            BroadcastViewportCommand(
                page = 0,
                pageOffsetPx = 0,
                shouldScroll = false,
                targetScalePercent = null,
                targetPanX = null,
                targetPanY = null,
            ),
            broadcastViewportCommandForDocument(
                frame = frame,
                documentId = "doc-1",
                currentPage = 0,
                currentPageOffsetPx = 0,
                currentScalePercent = 100,
                currentPanX = 10f,
                currentPanY = -20f,
                currentViewportWidthPx = 1200f,
                currentViewportHeightPx = 900f,
                currentRowWidthPx = 800f,
                currentPageTopsPx = testPageTops,
                currentPageHeightsPx = testPageHeights,
            ),
        )
    }

    @Test
    fun `broadcast viewport command preserves local horizontal pan when zoom changes`() {
        val frame =
            NetworkMessage.ProjectionFrame(
                documentId = "doc-1",
                page = 1,
                viewportOffsetX = 42f,
                viewportOffsetY = 10f,
                viewportScale = 1.5f,
            )

        assertEquals(
            BroadcastViewportCommand(
                page = 1,
                pageOffsetPx = 10,
                shouldScroll = true,
                targetScalePercent = 150,
                targetPanX = null,
                targetPanY = null,
            ),
            broadcastViewportCommandForDocument(
                frame = frame,
                documentId = "doc-1",
                currentPage = 1,
                currentPageOffsetPx = 10,
                currentScalePercent = 100,
                currentPanX = 42f,
                currentPanY = -20f,
                currentViewportWidthPx = 1200f,
                currentViewportHeightPx = 900f,
                currentRowWidthPx = 800f,
                currentPageTopsPx = testPageTops,
                currentPageHeightsPx = testPageHeights,
            ),
        )
    }

    @Test
    fun `broadcast viewport command maps normalized horizontal center to local viewport`() {
        val frame =
            NetworkMessage.ProjectionFrame(
                documentId = "doc-1",
                page = 1,
                viewportOffsetX = -300f,
                viewportOffsetY = 10f,
                viewportScale = 2f,
                viewportCenterX = 0.75f,
            )

        assertEquals(
            BroadcastViewportCommand(
                page = 1,
                pageOffsetPx = 10,
                shouldScroll = false,
                targetScalePercent = null,
                targetPanX = -400f,
                targetPanY = null,
            ),
            broadcastViewportCommandForDocument(
                frame = frame,
                documentId = "doc-1",
                currentPage = 1,
                currentPageOffsetPx = 10,
                currentScalePercent = 200,
                currentPanX = 0f,
                currentPanY = -20f,
                currentViewportWidthPx = 1600f,
                currentViewportHeightPx = 900f,
                currentRowWidthPx = 800f,
                currentPageTopsPx = testPageTops,
                currentPageHeightsPx = testPageHeights,
            ),
        )
    }

    @Test
    fun `broadcast viewport command defers vertical pan during zoom frame`() {
        val frame =
            NetworkMessage.ProjectionFrame(
                documentId = "doc-1",
                page = 1,
                viewportOffsetY = 100f,
                viewportScale = 2f,
                viewportCenterY = 1.6f,
            )

        assertEquals(
            BroadcastViewportCommand(
                page = 1,
                pageOffsetPx = 100,
                shouldScroll = false,
                targetScalePercent = 200,
                targetPanX = null,
                targetPanY = null,
            ),
            broadcastViewportCommandForDocument(
                frame = frame,
                documentId = "doc-1",
                currentPage = 1,
                currentPageOffsetPx = 100,
                currentScalePercent = 100,
                currentPanX = 0f,
                currentPanY = -100f,
                currentViewportWidthPx = 1600f,
                currentViewportHeightPx = 900f,
                currentRowWidthPx = 800f,
                currentPageTopsPx = testPageTops,
                currentPageHeightsPx = testPageHeights,
            ),
        )
    }

    @Test
    fun `broadcast viewport command maps vertical center after scale settles`() {
        val frame =
            NetworkMessage.ProjectionFrame(
                documentId = "doc-1",
                page = 1,
                viewportOffsetY = 100f,
                viewportScale = 2f,
                viewportCenterY = 1.6f,
            )

        assertEquals(
            BroadcastViewportCommand(
                page = 1,
                pageOffsetPx = 100,
                shouldScroll = false,
                targetScalePercent = null,
                targetPanX = null,
                targetPanY = -2750f,
            ),
            broadcastViewportCommandForDocument(
                frame = frame,
                documentId = "doc-1",
                previousFrame = frame,
                currentPage = 1,
                currentPageOffsetPx = 100,
                currentScalePercent = 200,
                currentPanX = 0f,
                currentPanY = -100f,
                currentViewportWidthPx = 1600f,
                currentViewportHeightPx = 900f,
                currentRowWidthPx = 800f,
                currentPageTopsPx = testPageTops,
                currentPageHeightsPx = testPageHeights,
            ),
        )
    }

    @Test
    fun `broadcast viewport command applies vertical center with settled zoom command`() {
        val frame =
            NetworkMessage.ProjectionFrame(
                documentId = "doc-1",
                page = 1,
                viewportOffsetY = 100f,
                viewportScale = 2f,
                viewportCenterY = 1.6f,
            )

        assertEquals(
            BroadcastViewportCommand(
                page = 1,
                pageOffsetPx = 100,
                shouldScroll = false,
                targetScalePercent = 200,
                targetPanX = null,
                targetPanY = -2750f,
            ),
            broadcastViewportCommandForDocument(
                frame = frame,
                documentId = "doc-1",
                previousFrame = frame,
                currentPage = 1,
                currentPageOffsetPx = 100,
                currentScalePercent = 100,
                currentPanX = 0f,
                currentPanY = -100f,
                currentViewportWidthPx = 1600f,
                currentViewportHeightPx = 900f,
                currentRowWidthPx = 800f,
                currentPageTopsPx = testPageTops,
                currentPageHeightsPx = testPageHeights,
                applyPanDuringScaleChange = true,
            ),
        )
    }

    @Test
    fun `broadcast viewport command applies horizontal center with settled zoom command`() {
        val frame =
            NetworkMessage.ProjectionFrame(
                documentId = "doc-1",
                page = 1,
                viewportOffsetY = 100f,
                viewportScale = 2f,
                viewportCenterX = 0.75f,
                viewportCenterY = 1.6f,
            )

        assertEquals(
            BroadcastViewportCommand(
                page = 1,
                pageOffsetPx = 100,
                shouldScroll = false,
                targetScalePercent = 200,
                targetPanX = -400f,
                targetPanY = -2750f,
            ),
            broadcastViewportCommandForDocument(
                frame = frame,
                documentId = "doc-1",
                previousFrame = frame,
                currentPage = 1,
                currentPageOffsetPx = 100,
                currentScalePercent = 100,
                currentPanX = 0f,
                currentPanY = -100f,
                currentViewportWidthPx = 1600f,
                currentViewportHeightPx = 900f,
                currentRowWidthPx = 800f,
                currentPageTopsPx = testPageTops,
                currentPageHeightsPx = testPageHeights,
                applyPanDuringScaleChange = true,
            ),
        )
    }

    @Test
    fun `broadcast viewport command does not drag horizontal pan during zoom frame`() {
        val frame =
            NetworkMessage.ProjectionFrame(
                documentId = "doc-1",
                page = 1,
                viewportOffsetY = 100f,
                viewportScale = 2f,
                viewportCenterX = 0.05f,
                viewportCenterY = 1.6f,
            )

        assertEquals(
            BroadcastViewportCommand(
                page = 1,
                pageOffsetPx = 100,
                shouldScroll = false,
                targetScalePercent = 200,
                targetPanX = null,
                targetPanY = null,
            ),
            broadcastViewportCommandForDocument(
                frame = frame,
                documentId = "doc-1",
                currentPage = 1,
                currentPageOffsetPx = 100,
                currentScalePercent = 100,
                currentPanX = 0f,
                currentPanY = -100f,
                currentViewportWidthPx = 1600f,
                currentViewportHeightPx = 900f,
                currentRowWidthPx = 800f,
                currentPageTopsPx = testPageTops,
                currentPageHeightsPx = testPageHeights,
            ),
        )
    }

    @Test
    fun `broadcast viewport command applies horizontal pan from remote center movement`() {
        val previousFrame =
            NetworkMessage.ProjectionFrame(
                documentId = "doc-1",
                page = 1,
                viewportOffsetY = 100f,
                viewportScale = 2f,
                viewportCenterX = 0.50f,
                viewportCenterY = 1.60f,
            )
        val frame =
            previousFrame.copy(
                viewportOffsetY = 112f,
                viewportCenterX = 0.75f,
                viewportCenterY = 1.61f,
            )

        assertEquals(
            BroadcastViewportCommand(
                page = 1,
                pageOffsetPx = 112,
                shouldScroll = false,
                targetScalePercent = null,
                targetPanX = -400f,
                targetPanY = -2770f,
            ),
            broadcastViewportCommandForDocument(
                frame = frame,
                documentId = "doc-1",
                previousFrame = previousFrame,
                currentPage = 1,
                currentPageOffsetPx = 100,
                currentScalePercent = 200,
                currentPanX = 0f,
                currentPanY = -100f,
                currentViewportWidthPx = 1600f,
                currentViewportHeightPx = 900f,
                currentRowWidthPx = 800f,
                currentPageTopsPx = testPageTops,
                currentPageHeightsPx = testPageHeights,
            ),
        )
    }

    @Test
    fun `viewport center y is encoded as page plus normalized page offset`() {
        assertEquals(
            1.6f,
            viewportCenterY(
                panY = -1150f,
                viewportHeightPx = 900f,
                pageTopsPx = testPageTops,
                pageHeightsPx = testPageHeights,
                zoom = 1f,
            ),
        )
    }

    @Test
    fun `viewport center y uses spread row height for paired pages with unequal heights`() {
        val spreadPageTops = floatArrayOf(0f, 0f, 200f)
        val spreadPageHeights = floatArrayOf(200f, 100f, 100f)

        val centerY =
            viewportCenterY(
                panY = -100f,
                viewportHeightPx = 100f,
                pageTopsPx = spreadPageTops,
                pageHeightsPx = spreadPageHeights,
                zoom = 1f,
            )

        assertEquals(0.75f, centerY)
        assertEquals(
            BroadcastViewportCommand(
                page = 0,
                pageOffsetPx = 0,
                shouldScroll = false,
                targetScalePercent = null,
                targetPanX = null,
                targetPanY = -100f,
            ),
            broadcastViewportCommandForDocument(
                frame =
                    NetworkMessage.ProjectionFrame(
                        documentId = "doc-1",
                        page = 0,
                        viewportOffsetY = 0f,
                        viewportScale = 1f,
                        viewportCenterY = centerY,
                    ),
                documentId = "doc-1",
                currentPage = 0,
                currentPageOffsetPx = 0,
                currentScalePercent = 100,
                currentPanX = 0f,
                currentPanY = 0f,
                currentViewportWidthPx = 1600f,
                currentViewportHeightPx = 100f,
                currentRowWidthPx = 800f,
                currentPageTopsPx = spreadPageTops,
                currentPageHeightsPx = spreadPageHeights,
            ),
        )
    }

    @Test
    fun `broadcast viewport command applies normalized horizontal center while remote scrolls vertically`() {
        val frame =
            NetworkMessage.ProjectionFrame(
                documentId = "doc-1",
                page = 2,
                viewportOffsetX = -240f,
                viewportOffsetY = 320f,
                viewportScale = 1f,
                viewportCenterX = 0.75f,
            )

        assertEquals(
            BroadcastViewportCommand(
                page = 2,
                pageOffsetPx = 320,
                shouldScroll = true,
                targetScalePercent = null,
                targetPanX = 200f,
                targetPanY = null,
            ),
            broadcastViewportCommandForDocument(
                frame = frame,
                documentId = "doc-1",
                currentPage = 2,
                currentPageOffsetPx = 300,
                currentScalePercent = 100,
                currentPanX = 120f,
                currentPanY = -20f,
                currentViewportWidthPx = 1600f,
                currentViewportHeightPx = 900f,
                currentRowWidthPx = 800f,
                currentPageTopsPx = testPageTops,
                currentPageHeightsPx = testPageHeights,
            ),
        )
    }

    @Test
    fun `broadcast document open action ignores blank or unresolved frame`() {
        val frame =
            NetworkMessage.ProjectionFrame(
                documentId = "",
                page = 0,
                viewportOffsetY = 0f,
                viewportScale = 1f,
            )

        assertEquals(
            BroadcastDocumentOpenAction.Ignore,
            broadcastDocumentOpenAction(frame, documentAlreadyOpen = false, resolvedUri = "/docs/a.pdf"),
        )
        assertEquals(
            BroadcastDocumentOpenAction.Ignore,
            broadcastDocumentOpenAction(frame.copy(documentId = "doc-1"), documentAlreadyOpen = false, resolvedUri = ""),
        )
        assertEquals(
            BroadcastDocumentOpenAction.Ignore,
            broadcastDocumentOpenAction(frame.copy(documentId = "doc-1"), documentAlreadyOpen = false, resolvedUri = null),
        )
    }

    @Test
    fun `broadcast document open action focuses existing document or opens resolved uri`() {
        val frame =
            NetworkMessage.ProjectionFrame(
                documentId = "doc-1",
                page = 0,
                viewportOffsetY = 0f,
                viewportScale = 1f,
            )

        assertEquals(
            BroadcastDocumentOpenAction.FocusExisting("doc-1"),
            broadcastDocumentOpenAction(frame, documentAlreadyOpen = true, resolvedUri = null),
        )
        assertEquals(
            BroadcastDocumentOpenAction.OpenResolved(documentId = "doc-1", uri = "/docs/a.pdf"),
            broadcastDocumentOpenAction(frame, documentAlreadyOpen = false, resolvedUri = "/docs/a.pdf"),
        )
    }

    @Test
    fun `active broadcast connection accepts either peers or hosts`() {
        val device = DeviceInfo(id = "peer", name = "Peer", host = "127.0.0.1", port = 1)

        assertFalse(hasActiveBroadcastConnection(connectedPeers = emptySet(), connectedHosts = emptySet()))
        assertTrue(hasActiveBroadcastConnection(connectedPeers = setOf(device), connectedHosts = emptySet()))
        assertTrue(hasActiveBroadcastConnection(connectedPeers = emptySet(), connectedHosts = setOf(device)))
    }

    @Test
    fun `active broadcast connection flow observes both transports`() =
        runTest {
            val device = DeviceInfo(id = "peer", name = "Peer", host = "127.0.0.1", port = 1)
            val server = FakePeerServer()
            val client = FakeSyncClient()

            assertFalse(activeBroadcastConnection(peerServer = server, peerClient = client).first())

            server.connectedPeersState.value = setOf(device)
            assertTrue(activeBroadcastConnection(peerServer = server, peerClient = client).first())

            server.connectedPeersState.value = emptySet()
            client.connectedHostsState.value = setOf(device)
            assertTrue(activeBroadcastConnection(peerServer = server, peerClient = client).first())

            client.connectedHostsState.value = emptySet()
            assertFalse(activeBroadcastConnection(peerServer = server, peerClient = client).first())
        }

    @Test
    fun `live sync auto enable is blocked only by explicit user pause`() {
        assertTrue(shouldAutoEnableLiveSync(hasBroadcastConnection = true, userPausedSync = false))
        assertFalse(shouldAutoEnableLiveSync(hasBroadcastConnection = false, userPausedSync = false))
        assertFalse(shouldAutoEnableLiveSync(hasBroadcastConnection = true, userPausedSync = true))
    }

    @Test
    fun `broadcast frames are published only by focused client runtime with live sync`() {
        assertTrue(
            shouldPublishBroadcastFrame(
                hasPeerClient = true,
                hasPeerServer = false,
                isFocused = true,
                liveSyncEnabled = true,
            ),
        )
        assertFalse(
            shouldPublishBroadcastFrame(
                hasPeerClient = false,
                hasPeerServer = false,
                isFocused = true,
                liveSyncEnabled = true,
            ),
        )
        assertFalse(
            shouldPublishBroadcastFrame(
                hasPeerClient = true,
                hasPeerServer = true,
                isFocused = true,
                liveSyncEnabled = true,
            ),
        )
        assertFalse(
            shouldPublishBroadcastFrame(
                hasPeerClient = true,
                hasPeerServer = false,
                isFocused = false,
                liveSyncEnabled = true,
            ),
        )
        assertFalse(
            shouldPublishBroadcastFrame(
                hasPeerClient = true,
                hasPeerServer = false,
                isFocused = true,
                liveSyncEnabled = false,
            ),
        )
    }

    @Test
    fun `broadcast frames are applied only by focused server runtime with active connection`() {
        assertTrue(
            shouldApplyBroadcastFrame(
                hasPeerServer = true,
                isFocused = true,
                hasBroadcastConnection = true,
            ),
        )
        assertFalse(
            shouldApplyBroadcastFrame(
                hasPeerServer = false,
                isFocused = true,
                hasBroadcastConnection = true,
            ),
        )
        assertFalse(
            shouldApplyBroadcastFrame(
                hasPeerServer = true,
                isFocused = false,
                hasBroadcastConnection = true,
            ),
        )
        assertFalse(
            shouldApplyBroadcastFrame(
                hasPeerServer = true,
                isFocused = true,
                hasBroadcastConnection = false,
            ),
        )
    }
}

private class FakePeerServer : PeerServer {
    val connectedPeersState = MutableStateFlow<Set<DeviceInfo>>(emptySet())

    override val lifecycle: Flow<ServerLifecycleState> = MutableStateFlow(ServerLifecycleState.Idle)
    override val connectedPeers: Flow<Set<DeviceInfo>> = connectedPeersState
    override val pendingApprovals: Flow<DeviceInfo> = MutableSharedFlow()
    override val incomingMessages: Flow<PeerMessage> = MutableSharedFlow()

    override suspend fun start(): Result<ServerLifecycleState.Running> = error("Not used")

    override suspend fun send(
        peerId: String,
        message: NetworkMessage,
    ) = Unit

    override suspend fun broadcast(message: NetworkMessage) = Unit

    override suspend fun approve(peerId: String) = Unit

    override suspend fun reject(peerId: String) = Unit

    override suspend fun disconnect(peerId: String) = Unit

    override suspend fun disconnectAll() = Unit

    override suspend fun stop() = Unit
}

private class FakeSyncClient : SyncClient {
    val connectedHostsState = MutableStateFlow<Set<DeviceInfo>>(emptySet())

    override val pairingStates: Flow<Map<String, PairingState>> = MutableStateFlow(emptyMap())
    override val connectedHosts: Flow<Set<DeviceInfo>> = connectedHostsState
    override val incomingMessages: Flow<HostMessage> = MutableSharedFlow()

    override suspend fun connect(
        server: DeviceInfo,
        pairingCode: String,
        selfInfo: DeviceInfo,
    ): Result<DeviceInfo> = error("Not used")

    override suspend fun send(
        hostId: String,
        message: NetworkMessage,
    ) = Unit

    override suspend fun broadcast(message: NetworkMessage) = Unit

    override suspend fun disconnect(hostId: String) = Unit

    override suspend fun disconnectAll() = Unit
}
