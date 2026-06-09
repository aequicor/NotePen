package ru.kyamshanov.notepen.sync.infrastructure

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.sendSerialized
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import ru.kyamshanov.notepen.sync.domain.Direction
import ru.kyamshanov.notepen.sync.domain.SessionCipher
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.HostMessage
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.model.PairingState
import ru.kyamshanov.notepen.sync.domain.port.SyncClient
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

private val logger = KotlinLogging.logger {}

private const val RECONNECT_DEADLINE_MS = 10_000L
private const val RECONNECT_RETRY_INTERVAL_MS = 1_000L

/**
 * Upper bound, in bytes, for a single WebSocket frame on the sync channel — a DoS
 * guard so a hostile peer (or host) can't force unbounded buffering with one giant
 * frame. Ktor's default is effectively unlimited, so this must be set explicitly on
 * **every** sync WebSocket install: the `KtorPeerServer` server side and each
 * client-side `install(WebSockets)` that backs a [KtorSyncClient].
 *
 * Sizing: file transfers travel as frames, so this must stay comfortably above the
 * largest legitimate frame. The biggest frame is a [NetworkMessage.FileChunk], whose
 * payload is `CHUNK_SIZE_BYTES` (64 KiB) of raw bytes Base64-encoded (~4/3 inflation
 * ≈ 88 KiB) plus a small JSON envelope. 8 MiB leaves ~90x headroom, so it never
 * truncates a chunk; if `CHUNK_SIZE_BYTES` is ever raised, keep this well above
 * `CHUNK_SIZE_BYTES * 4/3` to avoid breaking transfer.
 */
public const val MAX_WS_FRAME_SIZE_BYTES: Long = 8L * 1024 * 1024

/**
 * [SyncClient] backed by Ktor WebSocket — supports multiple concurrent host
 * connections, each managed by its own [HostSession].
 *
 * Engine is injected so the same class works on JVM (CIO) and Android (CIO).
 * The caller must install [WebSockets] on [client] before passing it in.
 *
 * On unexpected disconnection each host's session transparently retries for
 * [RECONNECT_DEADLINE_MS] ms, emitting [PairingState.Reconnecting] each second.
 * If the deadline elapses without success, that host's state becomes
 * [PairingState.LostConnection]; other hosts are unaffected.
 */
class KtorSyncClient(
    private val client: HttpClient,
) : SyncClient {
    private val mutex = Mutex()
    private val sessions = mutableMapOf<String, HostSession>()

    private val _pairingStates = MutableStateFlow<Map<String, PairingState>>(emptyMap())
    override val pairingStates: Flow<Map<String, PairingState>> = _pairingStates.asStateFlow()

    override val connectedHosts: Flow<Set<DeviceInfo>> =
        _pairingStates.map { snapshot ->
            snapshot.values
                .filterIsInstance<PairingState.Connected>()
                .map { it.peer }
                .toSet()
        }

    private val _incoming = MutableSharedFlow<HostMessage>(extraBufferCapacity = 64)
    override val incomingMessages: Flow<HostMessage> = _incoming.asSharedFlow()

    override suspend fun connect(
        server: DeviceInfo,
        pairingCode: String,
        selfInfo: DeviceInfo,
    ): Result<DeviceInfo> {
        // If we already have a session for this host id, return its current peer
        // info if connected; otherwise drop it so we can re-pair.
        val hostId = server.id
        mutex.withLock {
            sessions[hostId]?.let { existing ->
                if (existing.isConnected()) {
                    return Result.success(existing.peer ?: server)
                }
                existing.cancel()
                sessions.remove(hostId)
                publishStates()
            }
        }

        val session =
            HostSession(
                server = server,
                pairingCode = pairingCode,
                selfInfo = selfInfo,
                incomingFlow = _incoming,
                httpClient = client,
                onStateChange = { hostId, st -> updateState(hostId, st) },
                onTerminated = { id -> removeSession(id) },
            )
        mutex.withLock { sessions[hostId] = session }
        updateState(hostId, PairingState.Idle)
        return session.start()
    }

    override suspend fun send(
        hostId: String,
        message: NetworkMessage,
    ) {
        val session = mutex.withLock { findSessionByHostId(hostId) } ?: return
        session.send(message)
    }

    /**
     * Sessions are keyed by the placeholder `server.id` ("host:port") that the
     * caller supplied to [connect], but every public flow ([connectedHosts],
     * [pairingStates]) exposes the **real** peer id learned from `PairAccepted`.
     * Callers naturally use the real id when invoking [send] / [disconnect],
     * so we look up by either form to keep the two views reconciled.
     *
     * Must be called under [mutex].
     */
    private fun findSessionByHostId(hostId: String): HostSession? {
        sessions[hostId]?.let { return it }
        return sessions.values.firstOrNull { it.peer?.id == hostId }
    }

    override suspend fun broadcast(message: NetworkMessage) {
        val snapshot = mutex.withLock { sessions.values.toList() }
        for (s in snapshot) runCatching { s.send(message) }
    }

    override suspend fun disconnect(hostId: String) {
        val (keyHostId, session) =
            mutex.withLock {
                sessions[hostId]?.let { return@withLock hostId to it }
                val entry = sessions.entries.firstOrNull { it.value.peer?.id == hostId }
                entry?.key to entry?.value
            }
        if (session == null || keyHostId == null) return
        mutex.withLock { sessions.remove(keyHostId) }
        session.cancel()
        updateState(keyHostId, PairingState.Idle, remove = true)
    }

    override suspend fun disconnectAll() {
        val snapshot =
            mutex.withLock {
                val copy = sessions.toMap()
                sessions.clear()
                copy
            }
        for ((_, s) in snapshot) s.cancel()
        _pairingStates.value = emptyMap()
    }

    private suspend fun updateState(
        hostId: String,
        state: PairingState,
        remove: Boolean = false,
    ) {
        val updated = _pairingStates.value.toMutableMap()
        if (remove) updated.remove(hostId) else updated[hostId] = state
        _pairingStates.value = updated
    }

    private suspend fun removeSession(hostId: String) {
        mutex.withLock { sessions.remove(hostId) }
        updateState(hostId, PairingState.Idle, remove = true)
    }

    private suspend fun publishStates() {
        // Trigger a state emission with the current map (used after session map mutation).
        _pairingStates.value = _pairingStates.value.toMap()
    }
}

/**
 * One independent WebSocket session against a single host. Owns its own scope,
 * outgoing channel, and reconnect loop. Lifetime ends when the caller invokes
 * [cancel] or when the host's reconnect deadline elapses.
 */
@Suppress("TooManyFunctions")
private class HostSession(
    val server: DeviceInfo,
    private val pairingCode: String,
    private val selfInfo: DeviceInfo,
    private val incomingFlow: MutableSharedFlow<HostMessage>,
    private val httpClient: HttpClient,
    private val onStateChange: suspend (hostId: String, PairingState) -> Unit,
    private val onTerminated: suspend (hostId: String) -> Unit,
) {
    private val scope = CoroutineScope(Job())
    private val outgoingChannel = Channel<NetworkMessage>(Channel.BUFFERED)
    private val firstPairingResult = CompletableDeferred<Result<DeviceInfo>>()

    // Manual (de)serialization is used for the encrypted post-handshake frames, so we
    // need our own Json with the same `type` discriminator the cleartext converter uses.
    private val json = Json { classDiscriminator = "type" }

    @Volatile var peer: DeviceInfo? = null
        private set

    @Volatile private var connectedFlag: Boolean = false

    fun isConnected(): Boolean = connectedFlag

    /**
     * Starts the connect+reconnect loop in [scope] and suspends until the
     * **first** pairing attempt produces a definitive result. On success
     * subsequent reconnects are silent; on first-attempt rejection the session
     * tears down and returns the failure.
     */
    suspend fun start(): Result<DeviceInfo> {
        scope.launch {
            var firstAttempt = true
            while (scope.isActive) {
                val outcome = runSession(firstAttempt)
                if (firstAttempt) {
                    when (outcome) {
                        is SessionOutcome.Paired -> firstPairingResult.complete(Result.success(outcome.peer))
                        // Paired then immediately closed by the host — still a
                        // successful first pair for the caller; termination is
                        // handled by the RemoteClosed check below.
                        SessionOutcome.RemoteClosed ->
                            firstPairingResult.complete(Result.success(peer ?: server))
                        is SessionOutcome.PairingFailed -> {
                            firstPairingResult.complete(Result.failure(IllegalStateException(outcome.reason)))
                            return@launch
                        }
                        SessionOutcome.Disconnected -> {
                            // first attempt never even paired — treat as failure
                            firstPairingResult.complete(Result.failure(IllegalStateException("connect failed")))
                        }
                    }
                }
                firstAttempt = false
                if (!scope.isActive) return@launch
                // Host-initiated disconnect is terminal — do not reconnect, else
                // the PC could never drop the link (it would auto-rejoin).
                if (outcome is SessionOutcome.RemoteClosed) {
                    onStateChange(server.id, PairingState.LostConnection)
                    onTerminated(server.id)
                    return@launch
                }
                // Try to reconnect within deadline
                val reconnected = attemptReconnect()
                if (!reconnected) {
                    onStateChange(server.id, PairingState.LostConnection)
                    onTerminated(server.id)
                    return@launch
                }
            }
        }
        return firstPairingResult.await()
    }

    suspend fun send(message: NetworkMessage) {
        outgoingChannel.send(message)
    }

    fun cancel() {
        scope.cancel()
        outgoingChannel.close()
    }

    private suspend fun runSession(firstAttempt: Boolean): SessionOutcome {
        var outcome: SessionOutcome = SessionOutcome.Disconnected
        // Fresh per-session client nonce so every (re)connection derives a distinct
        // AEAD key from the long-lived pairing code, and so the key-confirmation
        // hello is bound to this specific session.
        val clientNonce = SecureChannel.newNonce()
        try {
            httpClient.webSocket(host = server.host, port = server.port, path = "/ws") {
                sendSerialized<NetworkMessage>(
                    NetworkMessage.PairRequest(code = pairingCode, device = selfInfo, clientNonce = clientNonce),
                )
                val pairReply = receivePairReply(firstAttempt)
                outcome = pairReply.outcome
                val accepted = pairReply.accepted ?: return@webSocket

                // Authenticated-encryption upgrade (F2/F3): derive the session cipher
                // from the pairing code + both nonces and confirm the host holds the
                // same key BEFORE treating the link as paired. A host (or MITM) that
                // lacks the code cannot produce a hello that opens, so we fail closed
                // and never expose this client's data on the channel.
                val cipher =
                    confirmKeysOrFail(this, accepted, clientNonce) ?: run {
                        outcome = SessionOutcome.PairingFailed("secure channel setup failed")
                        return@webSocket
                    }

                // Confirmed: now we are genuinely paired with an authenticated host.
                peer = accepted.serverDevice
                connectedFlag = true
                onStateChange(server.id, PairingState.Connected(accepted.serverDevice))
                outcome = SessionOutcome.Paired(accepted.serverDevice)
                if (firstAttempt) {
                    firstPairingResult.complete(Result.success(accepted.serverDevice))
                }
                logger.info { "Paired with ${accepted.serverDevice.name} (host=${server.id})" }

                val session = this
                val forwarder = launchForwarder(sessionScope = this, session = session, cipher = cipher)
                try {
                    val hostPeer = peer ?: server
                    outcome = consumeEncryptedIncoming(session, cipher, hostPeer)
                } finally {
                    forwarder.cancel()
                    connectedFlag = false
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn { "Session for ${server.id} ended: ${e.message}" }
            connectedFlag = false
        }
        return outcome
    }

    private suspend fun DefaultClientWebSocketSession.receivePairReply(firstAttempt: Boolean): PairReply =
        when (val reply = receiveDeserialized<NetworkMessage>()) {
            is NetworkMessage.PairAccepted -> PairReply(accepted = reply, outcome = SessionOutcome.Paired(reply.serverDevice))
            is NetworkMessage.PairRejected -> {
                onStateChange(server.id, PairingState.Error("Pairing rejected: ${reply.reason}"))
                PairReply(
                    accepted = null,
                    outcome =
                        if (firstAttempt) {
                            SessionOutcome.PairingFailed(reply.reason)
                        } else {
                            SessionOutcome.Disconnected
                        },
                )
            }
            else -> {
                onStateChange(server.id, PairingState.Error("Unexpected reply: $reply"))
                PairReply(accepted = null, outcome = SessionOutcome.PairingFailed("unexpected reply"))
            }
        }

    private suspend fun confirmKeysOrFail(
        session: WebSocketSession,
        accepted: NetworkMessage.PairAccepted,
        clientNonce: String,
    ): SessionCipher? =
        runCatching {
            confirmKeys(session = session, accepted = accepted, clientNonce = clientNonce)
        }.getOrElse {
            logger.warn { "Key confirmation with ${server.id} failed: ${it.message}" }
            onStateChange(server.id, PairingState.Error("Secure channel setup failed"))
            null
        }

    private fun launchForwarder(
        sessionScope: CoroutineScope,
        session: WebSocketSession,
        cipher: SessionCipher,
    ): Job =
        sessionScope.launch {
            try {
                while (true) {
                    val msg = outgoingChannel.receive()
                    SecureChannel.sendEncrypted(session, cipher, Direction.CLIENT_TO_SERVER, json, msg)
                }
            } catch (_: kotlinx.coroutines.channels.ClosedReceiveChannelException) {
                // Channel closed by [HostSession.cancel] — normal shutdown path.
            }
        }

    private suspend fun consumeEncryptedIncoming(
        session: WebSocketSession,
        cipher: SessionCipher,
        hostPeer: DeviceInfo,
    ): SessionOutcome {
        var outcome: SessionOutcome = SessionOutcome.Paired(hostPeer)
        var running = true
        while (running) {
            // Inbound application frames are encrypted (server→client). null =
            // socket closed; a thrown SessionCipherException = forged/tampered
            // frame, caught by the outer handler which ends the session.
            val msg = SecureChannel.receiveEncrypted(session, cipher, Direction.SERVER_TO_CLIENT, json)
            when {
                msg == null -> running = false
                msg is NetworkMessage.Disconnect -> {
                    // Host deliberately closed us — terminal, do not reconnect.
                    outcome = SessionOutcome.RemoteClosed
                    running = false
                }
                else -> incomingFlow.emit(HostMessage(hostPeer, msg))
            }
        }
        return outcome
    }

    /**
     * Runs the client side of the key-confirmation handshake on an open [session].
     *
     * Derives the [SessionCipher] from [pairingCode] + the two nonces, reads the
     * host's encrypted hello and verifies it echoes [NetworkMessage.PairAccepted.serverNonce]
     * (proves the host holds the key — i.e. knows the pairing code), then sends this
     * client's encrypted hello-ack echoing [clientNonce]. Throws on any
     * derivation/decrypt/marker/nonce failure so the caller fails the session closed.
     */
    private suspend fun confirmKeys(
        session: WebSocketSession,
        accepted: NetworkMessage.PairAccepted,
        clientNonce: String,
    ): SessionCipher {
        val cipher = SecureChannel.deriveCipher(pairingCode, clientNonce = clientNonce, serverNonce = accepted.serverNonce)
        val helloFrame = SecureChannel.nextBinaryFrame(session) ?: error("connection closed before encrypted hello")
        SecureChannel.verifyHello(cipher, Direction.SERVER_TO_CLIENT, helloFrame, expectedPeerNonce = accepted.serverNonce)
        session.send(Frame.Binary(fin = true, data = SecureChannel.buildHello(cipher, Direction.CLIENT_TO_SERVER, clientNonce)))
        return cipher
    }

    private suspend fun attemptReconnect(): Boolean {
        val start = TimeSource.Monotonic.markNow()
        val deadline = RECONNECT_DEADLINE_MS.milliseconds
        while (scope.isActive) {
            val remainingMs = (deadline - start.elapsedNow()).inWholeMilliseconds
            if (remainingMs <= 0) return false
            onStateChange(
                server.id,
                PairingState.Reconnecting(secondsRemaining = ((remainingMs + 999) / 1000).toInt()),
            )
            val result = runCatching { runSession(firstAttempt = false) }.getOrNull()
            // Reconnected, then the host deliberately closed us — terminal: stop
            // retrying so the host can drop the link for good.
            if (result is SessionOutcome.RemoteClosed) return false
            if (result is SessionOutcome.Paired) return true
            delay(RECONNECT_RETRY_INTERVAL_MS)
        }
        return false
    }
}

private data class PairReply(
    val accepted: NetworkMessage.PairAccepted?,
    val outcome: SessionOutcome,
)

private sealed class SessionOutcome {
    data class Paired(
        val peer: DeviceInfo,
    ) : SessionOutcome()

    /**
     * The host sent an explicit [NetworkMessage.Disconnect] (user pressed
     * "disconnect" on the PC, or the server stopped). This is TERMINAL — the
     * client must NOT silently reconnect, otherwise the connection cannot be
     * torn down from the host (it would auto-reconnect immediately).
     */
    data object RemoteClosed : SessionOutcome()

    data class PairingFailed(
        val reason: String,
    ) : SessionOutcome()

    data object Disconnected : SessionOutcome()
}
