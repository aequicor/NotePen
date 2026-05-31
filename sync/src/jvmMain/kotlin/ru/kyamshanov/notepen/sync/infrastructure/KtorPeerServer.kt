package ru.kyamshanov.notepen.sync.infrastructure

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.receiveDeserialized
import io.ktor.server.websocket.sendSerialized
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.model.PeerMessage
import ru.kyamshanov.notepen.sync.domain.model.ServerLifecycleState
import ru.kyamshanov.notepen.sync.domain.port.PeerServer
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

/**
 * Ktor CIO [PeerServer] implementation supporting **multiple concurrent
 * clients** behind a single shared pairing code.
 *
 * Sessions are keyed by [DeviceInfo.id] in [sessions]. Each newly connecting
 * client emits to [pendingApprovals] and suspends until the UI calls
 * [approve]/[reject]. Approved peers move into [connectedPeers] and may
 * remain until [disconnect], [disconnectAll], or [stop].
 *
 * @param selfInfo description of this device sent to peers after pairing
 * @param ioDispatcher dispatcher for Ktor engine I/O
 */
class KtorPeerServer(
    private val selfInfo: DeviceInfo,
    private val ioDispatcher: CoroutineDispatcher,
) : PeerServer {
    private val _lifecycle = MutableStateFlow<ServerLifecycleState>(ServerLifecycleState.Idle)
    override val lifecycle: Flow<ServerLifecycleState> = _lifecycle.asStateFlow()

    private val _connectedPeers = MutableStateFlow<Set<DeviceInfo>>(emptySet())
    override val connectedPeers: Flow<Set<DeviceInfo>> = _connectedPeers.asStateFlow()

    private val _pendingApprovals = MutableSharedFlow<DeviceInfo>(extraBufferCapacity = 16)
    override val pendingApprovals: Flow<DeviceInfo> = _pendingApprovals.asSharedFlow()

    private val _incoming = MutableSharedFlow<PeerMessage>(extraBufferCapacity = 128)
    override val incomingMessages: Flow<PeerMessage> = _incoming.asSharedFlow()

    private val pairing = PairingManager()
    private val json = Json { classDiscriminator = "type" }

    private val sessionMutex = Mutex()
    private val sessions = mutableMapOf<String, DefaultWebSocketServerSession>()
    private val approvalDeferreds = mutableMapOf<String, CompletableDeferred<Boolean>>()
    private val pendingPeers = mutableMapOf<String, DeviceInfo>()

    // Peer ids approved once in this server cycle. A reconnect from such a peer
    // is auto-accepted (no second approval prompt), so the client's silent
    // reconnect loop can re-pair without user interaction. Cleared on teardown.
    private val approvedPeerIds = mutableSetOf<String>()

    private var engine: EmbeddedServer<*, *>? = null
    private val serverScope = CoroutineScope(ioDispatcher + Job())

    // Serialises start() so concurrent callers (the QR-pairing panel + per-library "Share via QR")
    // can't both bind, and so a second start() while running is a safe no-op (see start()).
    private val startMutex = Mutex()

    override suspend fun start(): Result<ServerLifecycleState.Running> =
        startMutex.withLock {
            // Idempotent: a second start() while already running — e.g. the QR-pairing panel opening
            // after a per-library "Share via QR" already started the server (or vice-versa) — must NOT
            // rebind. Rebinding would mint a new code/port, orphan the old Ktor engine, and invalidate
            // the QR already on screen. Return the existing Running state instead. The mutex also closes
            // the concurrent-double-start race (the two entry points share one server instance).
            (_lifecycle.value as? ServerLifecycleState.Running)?.let { return@withLock Result.success(it) }
            runCatching {
                val code = pairing.generateCode()
                val host = pickLanIpv4Address() ?: InetAddress.getLocalHost().hostAddress ?: "127.0.0.1"
                val port = findFreePort()

                engine =
                    embeddedServer(CIO, port = port) {
                        install(ContentNegotiation) { json(json) }
                        install(WebSockets) {
                            pingPeriod = 30.seconds
                            contentConverter = KotlinxWebsocketSerializationConverter(json)
                        }
                        routing {
                            webSocket("/ws") { handleSession(this) }
                        }
                    }.also { it.start(wait = false) }

                val running = ServerLifecycleState.Running(host = host, port = port, code = code)
                _lifecycle.value = running
                logger.info { "PeerServer started on $host:$port (code=$code)" }
                running
            }
        }

    private fun findFreePort(): Int = java.net.ServerSocket(0).use { it.localPort }

    /**
     * Picks the most likely LAN-facing IPv4 address, skipping loopback, link-local,
     * down interfaces, and common VPN / virtual adapters (TAP/TUN, WireGuard,
     * Hyper-V, WSL, VirtualBox). Falls back to `null` when nothing matches —
     * caller then uses [InetAddress.getLocalHost] as a last resort.
     */
    @Suppress("ReturnCount")
    private fun pickLanIpv4Address(): String? {
        val vpnHints = listOf("tun", "tap", "wg", "ppp", "wsl", "vmnet", "vbox", "hyper-v", "vethernet", "loopback")
        val candidates = mutableListOf<Pair<Int, String>>()
        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces() }.getOrNull() ?: return null
        for (nif in interfaces) {
            if (!nif.isUp || nif.isLoopback || nif.isVirtual || nif.isPointToPoint) continue
            val displayLower = (nif.displayName ?: "").lowercase()
            val nameLower = (nif.name ?: "").lowercase()
            val looksVpn = vpnHints.any { it in displayLower || it in nameLower }
            for (addr in nif.inetAddresses) {
                if (addr !is Inet4Address) continue
                if (addr.isLoopbackAddress || addr.isLinkLocalAddress || addr.isAnyLocalAddress) continue
                val host = addr.hostAddress ?: continue
                val rank =
                    when {
                        looksVpn -> 100
                        host.startsWith("192.168.") -> 0
                        host.startsWith("10.") -> 1
                        host.startsWith("172.") -> 2
                        else -> 50
                    }
                candidates += rank to host
            }
        }
        return candidates.minByOrNull { it.first }?.second
    }

    @Suppress("ReturnCount", "LongMethod")
    private suspend fun handleSession(session: DefaultWebSocketServerSession) {
        val first = runCatching { session.receiveDeserialized<NetworkMessage>() }.getOrNull()
        if (first !is NetworkMessage.PairRequest) {
            session.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "expected pair_request"))
            return
        }
        if (!pairing.validate(first.code)) {
            session.sendSerialized<NetworkMessage>(NetworkMessage.PairRejected("invalid code"))
            session.close()
            return
        }
        val peer = first.device
        // Reconnect handling: a new connection from a device id that already has
        // a (possibly stale, half-open) session REPLACES the old one rather than
        // being rejected — otherwise the client's silent reconnect was refused
        // with "already connected" until the dead TCP timed out. A peer approved
        // earlier this cycle is auto-accepted (no second prompt), so reconnects
        // are seamless.
        val replaced: DefaultWebSocketServerSession?
        val autoApprove: Boolean
        sessionMutex.withLock {
            // A first-time approval is mid-decision — don't disrupt it; the legit
            // client will retry and succeed once the user approves.
            if (pendingPeers.containsKey(peer.id)) {
                session.sendSerialized<NetworkMessage>(NetworkMessage.PairRejected("approval in progress"))
                session.close()
                return
            }
            replaced = sessions.remove(peer.id)
            autoApprove = peer.id in approvedPeerIds
            if (!autoApprove) pendingPeers[peer.id] = peer
        }
        // Evict the old session outside the lock (close can block on a half-open
        // socket). Plain graceful close — no `Disconnect` message, the old client
        // must not tear down its own document state on a mere replacement.
        replaced?.let { old -> runCatching { old.close(CloseReason(CloseReason.Codes.NORMAL, "replaced")) } }

        if (!autoApprove) {
            val approvalDeferred = CompletableDeferred<Boolean>()
            sessionMutex.withLock { approvalDeferreds[peer.id] = approvalDeferred }
            _pendingApprovals.emit(peer)

            val approved = approvalDeferred.await()
            sessionMutex.withLock {
                approvalDeferreds.remove(peer.id)
                pendingPeers.remove(peer.id)
                if (approved) approvedPeerIds.add(peer.id)
            }
            if (!approved) {
                runCatching { session.sendSerialized<NetworkMessage>(NetworkMessage.Disconnect) }
                session.close(CloseReason(CloseReason.Codes.NORMAL, "rejected by user"))
                return
            }
        }

        session.sendSerialized<NetworkMessage>(NetworkMessage.PairAccepted(selfInfo))
        sessionMutex.withLock {
            sessions[peer.id] = session
            peerInfoById[peer.id] = peer
        }
        publishConnectedPeers()
        logger.info { "Paired with ${peer.name} (${peer.id})" }

        try {
            while (true) {
                val msg = session.receiveDeserialized<NetworkMessage>()
                if (msg is NetworkMessage.Disconnect) break
                _incoming.emit(PeerMessage(peer, msg))
            }
        } catch (e: Exception) {
            logger.warn { "Session for ${peer.name} ended: ${e.message}" }
        } finally {
            // Identity guard: only tear down the maps if THIS session is still the
            // registered one. A reconnect may have already replaced us — without
            // this check the old session's teardown would wipe the new entry.
            sessionMutex.withLock {
                if (sessions[peer.id] === session) {
                    sessions.remove(peer.id)
                    peerInfoById.remove(peer.id)
                }
            }
            publishConnectedPeers()
        }
    }

    private val peerInfoById = mutableMapOf<String, DeviceInfo>()

    private suspend fun publishConnectedPeers() {
        val snapshot = sessionMutex.withLock { peerInfoById.values.toSet() }
        _connectedPeers.value = snapshot
    }

    override suspend fun send(
        peerId: String,
        message: NetworkMessage,
    ) {
        val session = sessionMutex.withLock { sessions[peerId] } ?: return
        runCatching { session.sendSerialized<NetworkMessage>(message) }
            .onFailure { logger.warn { "send to $peerId failed: ${it.message}" } }
    }

    override suspend fun broadcast(message: NetworkMessage) {
        val snapshot = sessionMutex.withLock { sessions.values.toList() }
        for (session in snapshot) {
            runCatching { session.sendSerialized<NetworkMessage>(message) }
        }
    }

    override suspend fun approve(peerId: String) {
        val deferred = sessionMutex.withLock { approvalDeferreds[peerId] } ?: return
        deferred.complete(true)
    }

    override suspend fun reject(peerId: String) {
        val deferred = sessionMutex.withLock { approvalDeferreds[peerId] } ?: return
        deferred.complete(false)
    }

    override suspend fun disconnect(peerId: String) {
        val session = sessionMutex.withLock { sessions[peerId] }
        if (session != null) {
            runCatching { session.sendSerialized<NetworkMessage>(NetworkMessage.Disconnect) }
            runCatching { session.close(CloseReason(CloseReason.Codes.NORMAL, "disconnected by host")) }
        }
        sessionMutex.withLock {
            sessions.remove(peerId)
            peerInfoById.remove(peerId)
            // Deliberate host-side disconnect revokes the auto-approve grant: a
            // later fresh connection from this device must prompt again.
            approvedPeerIds.remove(peerId)
        }
        publishConnectedPeers()
    }

    override suspend fun disconnectAll() {
        val snapshot = sessionMutex.withLock { sessions.toMap() }
        for ((_, session) in snapshot) {
            runCatching { session.sendSerialized<NetworkMessage>(NetworkMessage.Disconnect) }
            runCatching { session.close(CloseReason(CloseReason.Codes.NORMAL, "disconnected by host")) }
        }
        sessionMutex.withLock {
            sessions.clear()
            peerInfoById.clear()
            approvedPeerIds.clear()
        }
        publishConnectedPeers()
    }

    override suspend fun stop() {
        pairing.invalidate()
        // Reject any pending approvals so suspended handleSession coroutines exit.
        val pendings = sessionMutex.withLock { approvalDeferreds.toMap() }
        for ((_, d) in pendings) {
            d.complete(false)
        }
        disconnectAll()
        _lifecycle.value = ServerLifecycleState.Stopped
        engine?.stop()
        engine = null
        _lifecycle.value = ServerLifecycleState.Idle
        logger.info { "PeerServer stopped" }
    }
}
