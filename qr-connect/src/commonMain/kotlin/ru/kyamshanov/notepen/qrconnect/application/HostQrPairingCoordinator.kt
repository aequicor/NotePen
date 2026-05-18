package ru.kyamshanov.notepen.qrconnect.application

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.qrconnect.domain.PairingUri
import ru.kyamshanov.notepen.qrconnect.domain.QrConnectError
import ru.kyamshanov.notepen.qrconnect.domain.port.QrEncoder
import ru.kyamshanov.notepen.qrconnect.domain.port.QrMatrix
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.ServerLifecycleState
import ru.kyamshanov.notepen.sync.domain.port.PeerServer

private val logger = KotlinLogging.logger {}

/**
 * Drives the long-lived host-side pairing flow.
 *
 * 1. Starts the [peerServer]; on success emits [State.ShowingQr] with an empty
 *    peer list and no pending approval.
 * 2. As clients connect, emits new [State.ShowingQr] snapshots reflecting
 *    [PeerServer.connectedPeers] and the current pending approval (the user
 *    advances it via [PeerServer.approve] / [PeerServer.reject]).
 * 3. Terminates with [State.Stopped] when the server lifecycle reaches
 *    [ServerLifecycleState.Stopped], or [State.Failed] on start error.
 *
 * Approval delegation: the coordinator does **not** own the approval gate —
 * gating now lives in [PeerServer]. The UI calls [PeerServer.approve] /
 * [PeerServer.reject] (typically via a ViewModel) to advance pending peers.
 */
class HostQrPairingCoordinator(
    private val peerServer: PeerServer,
    private val encoder: QrEncoder,
    private val hostDeviceName: String,
    private val qrSize: Int = DEFAULT_QR_SIZE,
) {

    /** UI-facing state machine for the QR pairing flow. */
    sealed class State {
        /** Server is starting; QR not ready yet. */
        data object Preparing : State()

        /**
         * Server is running. QR + manual-connect info are stable for the
         * lifetime of this state. [peers] mirrors currently connected clients;
         * [pendingApproval] is non-null while the user must accept/reject a
         * freshly connecting peer.
         */
        data class ShowingQr(
            val uri: PairingUri,
            val matrix: QrMatrix,
            val peers: List<DeviceInfo>,
            val pendingApproval: DeviceInfo?,
        ) : State()

        /** Server stopped (user clicked "Запретить подключения"). */
        data object Stopped : State()

        /** Server failed to start. */
        data class Failed(val error: QrConnectError) : State()
    }

    /**
     * Runs the host pairing flow as a long-lived [Flow]. Subscribe and collect
     * on a UI scope; cancelling the collector aborts the flow (caller is
     * responsible for calling [PeerServer.stop] if it wants the server stopped).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun run(): Flow<State> = channelFlow {
        send(State.Preparing)

        val startResult = peerServer.start()
        if (startResult.isFailure) {
            val msg = startResult.exceptionOrNull()?.message ?: "unknown"
            logger.warn { "PeerServer failed to start: $msg" }
            send(State.Failed(QrConnectError.ServerStartFailed(msg)))
            close()
            return@channelFlow
        }
        val running = startResult.getOrThrow()
        val uri = PairingUri(
            host = running.host,
            port = running.port,
            code = running.code,
            deviceName = hostDeviceName,
        )
        val matrix = encoder.encode(uri.encode(), qrSize)
        logger.info { "QR ready: $uri" }

        val pending = MutableStateFlow<DeviceInfo?>(null)

        // Track incoming pending-approval events.
        launch {
            peerServer.pendingApprovals.collect { peer -> pending.value = peer }
        }
        // Auto-clear pending once the peer is approved (appears in connectedPeers)
        // or replaced by a different pending peer.
        launch {
            peerServer.connectedPeers.distinctUntilChanged().collect { connected ->
                val cur = pending.value
                if (cur != null && connected.any { it.id == cur.id }) {
                    pending.value = null
                }
            }
        }
        // Watch lifecycle for terminal Stopped (Idle = server hasn't started yet
        // or has fully torn down — emit Stopped either way and close the flow).
        launch {
            peerServer.lifecycle.collect { ls ->
                if (ls is ServerLifecycleState.Stopped) {
                    pending.value = null
                    send(State.Stopped)
                    close()
                }
            }
        }

        combine(peerServer.connectedPeers, pending) { peers, p ->
            State.ShowingQr(
                uri = uri,
                matrix = matrix,
                peers = peers.toList(),
                pendingApproval = p,
            )
        }.collect { snapshot -> trySend(snapshot) }
    }

    companion object {
        const val DEFAULT_QR_SIZE: Int = 512
    }
}
