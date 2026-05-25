package ru.kyamshanov.notepen.qrconnect.application

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import ru.kyamshanov.notepen.qrconnect.domain.port.QrEncoder
import ru.kyamshanov.notepen.qrconnect.domain.port.QrMatrix
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.model.PeerMessage
import ru.kyamshanov.notepen.sync.domain.model.ServerLifecycleState
import ru.kyamshanov.notepen.sync.domain.port.PeerServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HostQrPairingCoordinatorTest {
    private val encoder = QrEncoder { _, size -> QrMatrix(size, BooleanArray(size * size)) }

    @Test
    fun startEmitsPreparingThenShowingQr() =
        runTest(UnconfinedTestDispatcher()) {
            val server = FakePeerServer()
            val coordinator =
                HostQrPairingCoordinator(
                    peerServer = server,
                    encoder = encoder,
                    hostDeviceName = "Desktop",
                    qrSize = 4,
                )
            val collected = mutableListOf<HostQrPairingCoordinator.State>()
            val job = launch { coordinator.run().collect { collected += it } }

            // Allow the channelFlow to run start() + initial combine emission.
            job.cancel()
            assertEquals(HostQrPairingCoordinator.State.Preparing, collected.first())
            assertTrue(collected.any { it is HostQrPairingCoordinator.State.ShowingQr })
            val showing = collected.last { it is HostQrPairingCoordinator.State.ShowingQr } as HostQrPairingCoordinator.State.ShowingQr
            assertEquals(emptyList<DeviceInfo>(), showing.peers)
            assertEquals(null, showing.pendingApproval)
        }

    @Test
    fun pendingApprovalAppearsInState() =
        runTest(UnconfinedTestDispatcher()) {
            val server = FakePeerServer()
            val coordinator =
                HostQrPairingCoordinator(
                    peerServer = server,
                    encoder = encoder,
                    hostDeviceName = "Desktop",
                    qrSize = 4,
                )
            val collected = mutableListOf<HostQrPairingCoordinator.State>()
            val job = launch { coordinator.run().collect { collected += it } }

            val peer = DeviceInfo(id = "peer-1", name = "Tablet", host = "10.0.0.99", port = 1)
            server.pendingApprovalsFlow.emit(peer)

            job.cancel()
            val withPending =
                collected.filterIsInstance<HostQrPairingCoordinator.State.ShowingQr>()
                    .lastOrNull { it.pendingApproval == peer }
            assertTrue(withPending != null, "expected ShowingQr with pendingApproval=$peer; got $collected")
        }

    @Test
    fun approvalClearsPendingAndAddsPeer() =
        runTest(UnconfinedTestDispatcher()) {
            val server = FakePeerServer()
            val coordinator =
                HostQrPairingCoordinator(
                    peerServer = server,
                    encoder = encoder,
                    hostDeviceName = "Desktop",
                    qrSize = 4,
                )
            val collected = mutableListOf<HostQrPairingCoordinator.State>()
            val job = launch { coordinator.run().collect { collected += it } }

            val peer = DeviceInfo(id = "peer-1", name = "Tablet", host = "10.0.0.99", port = 1)
            server.pendingApprovalsFlow.emit(peer)
            // Simulate server-side approval: peer transitions into connectedPeers.
            server.connectedPeersFlow.value = setOf(peer)

            job.cancel()
            val last = collected.filterIsInstance<HostQrPairingCoordinator.State.ShowingQr>().last()
            assertEquals(listOf(peer), last.peers)
            assertEquals(null, last.pendingApproval)
        }

    @Test
    fun stoppedLifecycleTerminatesFlow() =
        runTest(UnconfinedTestDispatcher()) {
            val server = FakePeerServer()
            val coordinator =
                HostQrPairingCoordinator(
                    peerServer = server,
                    encoder = encoder,
                    hostDeviceName = "Desktop",
                    qrSize = 4,
                )
            val collected = mutableListOf<HostQrPairingCoordinator.State>()
            val job = launch { coordinator.run().collect { collected += it } }

            server.lifecycleFlow.value = ServerLifecycleState.Stopped
            job.join()

            assertEquals(HostQrPairingCoordinator.State.Stopped, collected.last())
        }

    @Test
    fun startFailureEmitsFailed() =
        runTest(UnconfinedTestDispatcher()) {
            val server = FakePeerServer(startResult = Result.failure(IllegalStateException("port busy")))
            val coordinator =
                HostQrPairingCoordinator(
                    peerServer = server,
                    encoder = encoder,
                    hostDeviceName = "Desktop",
                )
            val collected = mutableListOf<HostQrPairingCoordinator.State>()
            val job = launch { coordinator.run().collect { collected += it } }
            job.join()
            assertTrue(collected.last() is HostQrPairingCoordinator.State.Failed)
        }
}

private class FakePeerServer(
    private val startResult: Result<ServerLifecycleState.Running> =
        Result.success(
            ServerLifecycleState.Running(host = "10.0.0.1", port = 42, code = "000000"),
        ),
) : PeerServer {
    val lifecycleFlow = MutableStateFlow<ServerLifecycleState>(ServerLifecycleState.Idle)
    override val lifecycle: Flow<ServerLifecycleState> = lifecycleFlow.asStateFlow()

    val connectedPeersFlow = MutableStateFlow<Set<DeviceInfo>>(emptySet())
    override val connectedPeers: Flow<Set<DeviceInfo>> = connectedPeersFlow.asStateFlow()

    val pendingApprovalsFlow = MutableSharedFlow<DeviceInfo>(extraBufferCapacity = 8)
    override val pendingApprovals: Flow<DeviceInfo> = pendingApprovalsFlow.asSharedFlow()

    override val incomingMessages: Flow<PeerMessage> =
        MutableSharedFlow<PeerMessage>().asSharedFlow()

    override suspend fun start(): Result<ServerLifecycleState.Running> {
        startResult.onSuccess { lifecycleFlow.value = it }
        return startResult
    }

    val sent = mutableListOf<Pair<String, NetworkMessage>>()

    override suspend fun send(
        peerId: String,
        message: NetworkMessage,
    ) {
        sent += peerId to message
    }

    val broadcast = mutableListOf<NetworkMessage>()

    override suspend fun broadcast(message: NetworkMessage) {
        broadcast += message
    }

    val approved = mutableListOf<String>()

    override suspend fun approve(peerId: String) {
        approved += peerId
    }

    val rejected = mutableListOf<String>()

    override suspend fun reject(peerId: String) {
        rejected += peerId
    }

    val disconnected = mutableListOf<String>()

    override suspend fun disconnect(peerId: String) {
        disconnected += peerId
    }

    var disconnectAllCalled = false

    override suspend fun disconnectAll() {
        disconnectAllCalled = true
    }

    var stopped = false

    override suspend fun stop() {
        stopped = true
        lifecycleFlow.value = ServerLifecycleState.Stopped
    }
}
