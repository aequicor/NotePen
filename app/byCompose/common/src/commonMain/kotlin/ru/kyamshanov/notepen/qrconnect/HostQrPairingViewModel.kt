package ru.kyamshanov.notepen.qrconnect

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.qrconnect.application.HostQrPairingCoordinator
import ru.kyamshanov.notepen.qrconnect.domain.port.QrEncoder
import ru.kyamshanov.notepen.sync.domain.port.PeerServer

/**
 * Drives [HostQrPairingPanel].
 *
 * Owns the [HostQrPairingCoordinator] lifecycle and forwards per-peer actions
 * (approve / reject / disconnect) to the underlying [PeerServer]. The view
 * subscribes to [state] and calls action methods on user input.
 *
 * The [PeerServer] is fetched lazily via [peerServerProvider] so the heavy
 * sync stack can stay un-instantiated until the user actually clicks
 * «Разрешить подключение по QR». [onStop], if supplied, tears that stack
 * down again when the server is stopped.
 *
 * @param grantLibrarian optional hook (M5b) called when the operator approves a
 *   peer as a **Librarian** rather than a plain reader: it adds the peer to the
 *   host's persisted write allow-set so the peer may add/replace books over LAN.
 *   Null (e.g. a build without a host library) leaves only the reader path.
 */
class HostQrPairingViewModel(
    private val peerServerProvider: suspend () -> PeerServer,
    private val qrEncoder: QrEncoder,
    private val hostDeviceName: String,
    private val scope: CoroutineScope,
    private val onStop: (suspend () -> Unit)? = null,
    private val grantLibrarian: (suspend (peerId: String) -> Unit)? = null,
) {
    private val _state = MutableStateFlow<HostQrPairingCoordinator.State?>(null)
    val state: StateFlow<HostQrPairingCoordinator.State?> = _state.asStateFlow()

    // Notifies the coordinator that a pending approval was resolved by the user
    // (reject) so it can dismiss the dialog — reject leaves no other signal.
    private val approvalResolutions = MutableSharedFlow<String>(extraBufferCapacity = 8)

    private var runJob: Job? = null

    /** Starts the server and begins emitting [state] updates. No-op if already running. */
    fun start() {
        if (runJob?.isActive == true) return
        _state.value = null
        runJob =
            scope.launch {
                val peerServer = peerServerProvider()
                val coordinator =
                    HostQrPairingCoordinator(
                        peerServer = peerServer,
                        encoder = qrEncoder,
                        hostDeviceName = hostDeviceName,
                        approvalResolutions = approvalResolutions,
                    )
                coordinator.run().collect { s -> _state.value = s }
            }
    }

    /**
     * Stops the server entirely. All connected peers receive
     * [ru.kyamshanov.notepen.sync.domain.model.NetworkMessage.Disconnect] and
     * the QR code becomes invalid. Resets [state] back to `null` so the next
     * time the panel is opened the user sees the initial "Разрешить
     * подключение" button instead of a stale "stopped" view.
     */
    fun stopServer() {
        runJob?.cancel()
        runJob = null
        _state.value = null
        scope.launch {
            peerServerProvider().stop()
            onStop?.invoke()
        }
    }

    /** Approves the pending peer as a read-only reader; no-op if none. */
    fun approve(peerId: String) {
        scope.launch { peerServerProvider().approve(peerId) }
    }

    /**
     * Approves the pending peer **and** grants it the Librarian role over this
     * host's library (M5b): the peer may then add/replace books over LAN. The
     * grant is persisted via [grantLibrarian]; falls back to a plain [approve]
     * when no librarian hook is wired (no host library).
     */
    fun approveAsLibrarian(peerId: String) {
        scope.launch {
            peerServerProvider().approve(peerId)
            grantLibrarian?.invoke(peerId)
        }
    }

    /** True when the host can grant the Librarian role (a host library is present). */
    val canGrantLibrarian: Boolean get() = grantLibrarian != null

    /** Rejects the pending peer with the given id; no-op if none. */
    fun reject(peerId: String) {
        scope.launch {
            peerServerProvider().reject(peerId)
            // Dismiss the approval dialog — reject produces no connectedPeers signal.
            approvalResolutions.emit(peerId)
        }
    }

    /** Disconnects one connected peer; the server keeps running for the others. */
    fun disconnect(peerId: String) {
        scope.launch { peerServerProvider().disconnect(peerId) }
    }

    /** Disconnects every connected peer; the server keeps running. */
    fun disconnectAll() {
        scope.launch { peerServerProvider().disconnectAll() }
    }
}
