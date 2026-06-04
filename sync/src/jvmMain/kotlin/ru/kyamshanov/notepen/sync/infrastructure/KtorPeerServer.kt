package ru.kyamshanov.notepen.sync.infrastructure

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.origin
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.receiveDeserialized
import io.ktor.server.websocket.sendSerialized
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import ru.kyamshanov.notepen.sync.domain.Direction
import ru.kyamshanov.notepen.sync.domain.SessionCipher
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.model.PeerMessage
import ru.kyamshanov.notepen.sync.domain.model.ServerLifecycleState
import ru.kyamshanov.notepen.sync.domain.port.PeerServer
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
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
 * Brute-force defence: the pairing code is high-entropy, but a failed code
 * validation is still rate-limited per remote IP via [rateLimiter]. Once a
 * remote crosses the failure threshold it is rejected immediately, before the
 * code is even compared, so the handshake never leaks whether a guess was close.
 *
 * @param selfInfo description of this device sent to peers after pairing
 * @param ioDispatcher dispatcher for Ktor engine I/O
 * @param approvalTimeout how long a connecting peer may wait for the user to
 *   approve/reject before the server gives up, rejects it, and closes the session.
 *   Bounds the otherwise-unbounded approval wait so a peer can't pin a coroutine and
 *   a `pendingPeers`/`approvalDeferreds` slot open forever. Defaults to 5 minutes.
 * @param maxMessagesPerSecond per-session ceiling on inbound messages: once a paired
 *   peer exceeds this many messages inside a one-second window the session is closed
 *   with `VIOLATED_POLICY`. Generous by default so normal stroke/delta/file sync never
 *   trips it; lowered by tests to exercise the flood path.
 * @param now epoch-millis time source for the handshake and per-session message rate
 *   limiters; injectable for tests, defaults to the system wall clock.
 */
class KtorPeerServer(
    private val selfInfo: DeviceInfo,
    private val ioDispatcher: CoroutineDispatcher,
    private val approvalTimeout: Duration = DEFAULT_APPROVAL_TIMEOUT,
    private val maxMessagesPerSecond: Int = DEFAULT_MAX_MESSAGES_PER_SECOND,
    private val now: () -> Long = { System.currentTimeMillis() },
) : PeerServer {
    private val _lifecycle = MutableStateFlow<ServerLifecycleState>(ServerLifecycleState.Idle)
    override val lifecycle: Flow<ServerLifecycleState> = _lifecycle.asStateFlow()

    private val _connectedPeers = MutableStateFlow<Set<DeviceInfo>>(emptySet())
    override val connectedPeers: Flow<Set<DeviceInfo>> = _connectedPeers.asStateFlow()

    private val _pendingApprovals = MutableSharedFlow<DeviceInfo>(extraBufferCapacity = 16)
    override val pendingApprovals: SharedFlow<DeviceInfo> = _pendingApprovals.asSharedFlow()

    private val _incoming = MutableSharedFlow<PeerMessage>(extraBufferCapacity = 128)
    override val incomingMessages: Flow<PeerMessage> = _incoming.asSharedFlow()

    private val pairing = PairingManager()
    private val rateLimiter = HandshakeRateLimiter(now = now)
    private val json = Json { classDiscriminator = "type" }

    private val sessionMutex = Mutex()

    /**
     * A paired peer's live socket together with the [SessionCipher] that secures it.
     * Only populated **after** the encrypted key-confirmation succeeds, so an entry
     * here always denotes a peer that proved knowledge of the pairing code — not a
     * bare, unauthenticated device id.
     *
     * [sendLock] serialises outbound seals: [SessionCipher] is not thread-safe and
     * several coroutines (sync broadcast, projection, catalog push, file transfer)
     * may call [send]/[broadcast] for the same peer at once. Sealing under the lock
     * keeps the per-direction nonce counter monotonic so no `(key, nonce)` pair is
     * ever reused — the invariant AES-GCM depends on.
     */
    private class EncryptedSession(
        val session: DefaultWebSocketServerSession,
        val cipher: SessionCipher,
    ) {
        val sendLock = Mutex()
    }

    private val sessions = mutableMapOf<String, EncryptedSession>()
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
                            // Cap a single inbound frame so a hostile peer can't force unbounded
                            // buffering; sized well above the largest legitimate frame (a Base64
                            // FileChunk). See MAX_WS_FRAME_SIZE_BYTES.
                            maxFrameSize = MAX_WS_FRAME_SIZE_BYTES
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
        // Identify the caller by remote IP for rate limiting. Direct LAN connections
        // have no proxy, so origin falls back to the real peer address; keying on the
        // address (not the ephemeral source port) stops a guesser cycling ports.
        val remoteKey = session.call.request.remoteKey()
        // Reject a remote that's already over its failure budget before reading the
        // body, so a brute-forcer gets a uniform fast close with no validity signal.
        if (rateLimiter.isLockedOut(remoteKey)) {
            session.close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "too many attempts"))
            return
        }
        val first = runCatching { session.receiveDeserialized<NetworkMessage>() }.getOrNull()
        if (first !is NetworkMessage.PairRequest) {
            session.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "expected pair_request"))
            return
        }
        if (!pairing.validate(first.code)) {
            // Count the failure; if it tips the remote over the threshold, close hard
            // (no PairRejected) so repeat offenders stop getting a clean "invalid code".
            val lockedOut = rateLimiter.recordFailure(remoteKey)
            if (lockedOut) {
                session.close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "too many attempts"))
            } else {
                session.sendSerialized<NetworkMessage>(NetworkMessage.PairRejected("invalid code"))
                session.close()
            }
            return
        }
        // Valid code: clear this remote's failure history so an earlier mistype by a
        // legitimate client doesn't count against its later successful attempts.
        rateLimiter.reset(remoteKey)
        val peer = first.device
        // Reconnect handling: a new connection from a device id that already has
        // a (possibly stale, half-open) session REPLACES the old one rather than
        // being rejected — otherwise the client's silent reconnect was refused
        // with "already connected" until the dead TCP timed out. A peer approved
        // earlier this cycle is auto-accepted (no second prompt), so reconnects
        // are seamless.
        val replaced: EncryptedSession?
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
        replaced?.let { old -> runCatching { old.session.close(CloseReason(CloseReason.Codes.NORMAL, "replaced")) } }

        if (!autoApprove) {
            val approvalDeferred = CompletableDeferred<Boolean>()
            sessionMutex.withLock { approvalDeferreds[peer.id] = approvalDeferred }
            _pendingApprovals.emit(peer)

            // Bound the wait: a peer that opens the handshake but is never approved/rejected
            // must not pin this coroutine and its pendingPeers/approvalDeferreds slots forever.
            // null = the user never decided in time; treat as a rejection.
            val approved = withTimeoutOrNull(approvalTimeout) { approvalDeferred.await() }
            sessionMutex.withLock {
                approvalDeferreds.remove(peer.id)
                pendingPeers.remove(peer.id)
                if (approved == true) approvedPeerIds.add(peer.id)
            }
            if (approved == null) {
                logger.warn { "Approval for ${peer.name} (${peer.id}) timed out after $approvalTimeout; rejecting" }
                runCatching { session.sendSerialized<NetworkMessage>(NetworkMessage.PairRejected("approval timed out")) }
                runCatching { session.close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "approval timed out")) }
                return
            }
            if (!approved) {
                runCatching { session.sendSerialized<NetworkMessage>(NetworkMessage.Disconnect) }
                session.close(CloseReason(CloseReason.Codes.NORMAL, "rejected by user"))
                return
            }
        }

        // Approval done — accept in cleartext (the user-facing bootstrap), then upgrade
        // the link to the authenticated-encryption channel before doing anything else.
        val serverNonce = SecureChannel.newNonce()
        session.sendSerialized<NetworkMessage>(NetworkMessage.PairAccepted(selfInfo, serverNonce))

        // Key-confirmation gate (F2/F3): derive the session cipher from the pairing
        // code + both handshake nonces, then prove mutual key knowledge over the
        // encrypted channel BEFORE the peer is added to `sessions`. A peer that knows
        // `peer.id` but not the code cannot complete this, so it never reaches the
        // message loop and never gets routed any data. Fail closed on any error.
        val cipher =
            establishSecureSession(
                session = session,
                peer = peer,
                pairingCode = first.code,
                clientNonce = first.clientNonce,
                serverNonce = serverNonce,
            ) ?: return

        sessionMutex.withLock {
            sessions[peer.id] = EncryptedSession(session, cipher)
            peerInfoById[peer.id] = peer
        }
        publishConnectedPeers()
        logger.info { "Paired with ${peer.name} (${peer.id})" }

        // Per-session flood guard: a paired peer that streams messages far faster than
        // any real stroke/delta/file sync is closed for policy violation. Limits are
        // generous (see SessionMessageRateLimiter), so honest clients never trip it.
        val messageLimiter = SessionMessageRateLimiter(maxMessages = maxMessagesPerSecond, now = now)
        try {
            while (true) {
                // Inbound application frames are encrypted (client→server). A null means
                // the socket closed; a thrown SessionCipherException means a forged or
                // tampered frame — both end the loop and tear the session down.
                val msg = SecureChannel.receiveEncrypted(session, cipher, Direction.CLIENT_TO_SERVER, json) ?: break
                if (!messageLimiter.allow()) {
                    logger.warn { "Closing session for ${peer.name} (${peer.id}): inbound message rate exceeded" }
                    session.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "message rate exceeded"))
                    break
                }
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
                if (sessions[peer.id]?.session === session) {
                    sessions.remove(peer.id)
                    peerInfoById.remove(peer.id)
                }
            }
            publishConnectedPeers()
        }
    }

    /**
     * Performs the post-handshake key confirmation for [session] against [peer].
     *
     * Derives the [SessionCipher] from [pairingCode] + the two handshake nonces,
     * sends this host's encrypted hello, then reads and verifies the client's
     * encrypted hello-ack. Returns the cipher on success; on any failure — wrong
     * code (hello won't open), malformed/missing nonce, marker/nonce mismatch, or a
     * dropped socket — it closes the session and returns `null` so the caller bails
     * out (fail-closed). The peer is never added to `sessions` unless this succeeds.
     */
    private suspend fun establishSecureSession(
        session: DefaultWebSocketServerSession,
        peer: DeviceInfo,
        pairingCode: String,
        clientNonce: String,
        serverNonce: String,
    ): SessionCipher? {
        val cipher =
            runCatching { SecureChannel.deriveCipher(pairingCode, clientNonce, serverNonce) }
                .getOrElse {
                    logger.warn { "Key derivation failed for ${peer.name} (${peer.id}): ${it.message}" }
                    runCatching { session.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "key setup failed")) }
                    return null
                }
        return runCatching {
            session.send(Frame.Binary(fin = true, data = SecureChannel.buildHello(cipher, Direction.SERVER_TO_CLIENT, serverNonce)))
            val ack = SecureChannel.nextBinaryFrame(session) ?: error("connection closed before encrypted hello-ack")
            SecureChannel.verifyHello(cipher, Direction.CLIENT_TO_SERVER, ack, expectedPeerNonce = clientNonce)
            cipher
        }.getOrElse {
            logger.warn { "Key confirmation failed for ${peer.name} (${peer.id}): ${it.message}" }
            runCatching { session.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "key confirmation failed")) }
            null
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
        val entry = sessionMutex.withLock { sessions[peerId] } ?: return
        runCatching { entry.sendEncrypted(message) }
            .onFailure { logger.warn { "send to $peerId failed: ${it.message}" } }
    }

    override suspend fun broadcast(message: NetworkMessage) {
        val snapshot = sessionMutex.withLock { sessions.values.toList() }
        for (entry in snapshot) {
            runCatching { entry.sendEncrypted(message) }
        }
    }

    /**
     * Seals [message] for this peer and writes it as one binary frame. The host
     * always seals in [Direction.SERVER_TO_CLIENT]; the client opens it on the
     * matching direction. File-transfer and every other [NetworkMessage] go through
     * here, so the entire post-handshake conversation is encrypted. Held under
     * [EncryptedSession.sendLock] so concurrent senders can't reuse a GCM nonce.
     */
    private suspend fun EncryptedSession.sendEncrypted(message: NetworkMessage) {
        sendLock.withLock {
            SecureChannel.sendEncrypted(session, cipher, Direction.SERVER_TO_CLIENT, json, message)
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
        val entry = sessionMutex.withLock { sessions[peerId] }
        if (entry != null) {
            runCatching { entry.sendEncrypted(NetworkMessage.Disconnect) }
            runCatching { entry.session.close(CloseReason(CloseReason.Codes.NORMAL, "disconnected by host")) }
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
        for ((_, entry) in snapshot) {
            runCatching { entry.sendEncrypted(NetworkMessage.Disconnect) }
            runCatching { entry.session.close(CloseReason(CloseReason.Codes.NORMAL, "disconnected by host")) }
        }
        sessionMutex.withLock {
            sessions.clear()
            peerInfoById.clear()
            approvedPeerIds.clear()
        }
        publishConnectedPeers()
    }

    /**
     * Remote address used as the rate-limit key. Falls back to a constant when the
     * engine can't report one, so a missing address can't slip past the limiter (it
     * shares one bucket with other address-less callers — fail closed, not open).
     */
    private fun ApplicationRequest.remoteKey(): String = runCatching { origin.remoteAddress }.getOrNull() ?: "unknown"

    override suspend fun stop() {
        pairing.invalidate()
        rateLimiter.clear()
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

    private companion object {
        /** Default ceiling on how long a peer may wait for the user's approval decision. */
        val DEFAULT_APPROVAL_TIMEOUT: Duration = 5.minutes

        /** Default per-second inbound-message budget per session. */
        const val DEFAULT_MAX_MESSAGES_PER_SECOND = SessionMessageRateLimiter.DEFAULT_MAX_MESSAGES
    }
}
