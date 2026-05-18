package ru.kyamshanov.notepen.sync.infrastructure

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.sendSerialized
import io.ktor.client.plugins.websocket.webSocket
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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.HostMessage
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.model.PairingState
import ru.kyamshanov.notepen.sync.domain.port.SyncClient

private val logger = KotlinLogging.logger {}

private const val RECONNECT_DEADLINE_MS = 10_000L
private const val RECONNECT_RETRY_INTERVAL_MS = 1_000L

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
class KtorSyncClient(private val client: HttpClient) : SyncClient {

    private val mutex = Mutex()
    private val sessions = mutableMapOf<String, HostSession>()

    private val _pairingStates = MutableStateFlow<Map<String, PairingState>>(emptyMap())
    override val pairingStates: Flow<Map<String, PairingState>> = _pairingStates.asStateFlow()

    override val connectedHosts: Flow<Set<DeviceInfo>> = _pairingStates.map { snapshot ->
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

        val session = HostSession(
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

    override suspend fun send(hostId: String, message: NetworkMessage) {
        val session = mutex.withLock { sessions[hostId] } ?: return
        session.send(message)
    }

    override suspend fun broadcast(message: NetworkMessage) {
        val snapshot = mutex.withLock { sessions.values.toList() }
        for (s in snapshot) runCatching { s.send(message) }
    }

    override suspend fun disconnect(hostId: String) {
        val session = mutex.withLock { sessions.remove(hostId) }
        session?.cancel()
        updateState(hostId, PairingState.Idle, remove = true)
    }

    override suspend fun disconnectAll() {
        val snapshot = mutex.withLock {
            val copy = sessions.toMap()
            sessions.clear()
            copy
        }
        for ((_, s) in snapshot) s.cancel()
        _pairingStates.value = emptyMap()
    }

    private suspend fun updateState(hostId: String, state: PairingState, remove: Boolean = false) {
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
        try {
            httpClient.webSocket(host = server.host, port = server.port, path = "/ws") {
                sendSerialized<NetworkMessage>(NetworkMessage.PairRequest(code = pairingCode, device = selfInfo))
                when (val reply = receiveDeserialized<NetworkMessage>()) {
                    is NetworkMessage.PairAccepted -> {
                        peer = reply.serverDevice
                        connectedFlag = true
                        onStateChange(server.id, PairingState.Connected(reply.serverDevice))
                        outcome = SessionOutcome.Paired(reply.serverDevice)
                        logger.info { "Paired with ${reply.serverDevice.name} (host=${server.id})" }
                    }
                    is NetworkMessage.PairRejected -> {
                        onStateChange(server.id, PairingState.Error("Pairing rejected: ${reply.reason}"))
                        return@webSocket Unit.also {
                            outcome = if (firstAttempt) {
                                SessionOutcome.PairingFailed(reply.reason)
                            } else {
                                SessionOutcome.Disconnected
                            }
                        }
                    }
                    else -> {
                        onStateChange(server.id, PairingState.Error("Unexpected reply: $reply"))
                        outcome = SessionOutcome.PairingFailed("unexpected reply")
                        return@webSocket
                    }
                }

                val session = this
                val forwarder = launch {
                    try {
                        while (true) {
                            val msg = outgoingChannel.receive()
                            session.sendSerialized<NetworkMessage>(msg)
                        }
                    } catch (_: kotlinx.coroutines.channels.ClosedReceiveChannelException) {
                        // Channel closed by [HostSession.cancel] — normal shutdown path.
                    }
                }
                try {
                    val hostPeer = peer ?: server
                    while (true) {
                        val msg = receiveDeserialized<NetworkMessage>()
                        if (msg is NetworkMessage.Disconnect) break
                        incomingFlow.emit(HostMessage(hostPeer, msg))
                    }
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
            val pingOk = runCatching { runSession(firstAttempt = false) }
                .getOrNull() is SessionOutcome.Paired
            if (pingOk) return true
            delay(RECONNECT_RETRY_INTERVAL_MS)
        }
        return false
    }
}

private sealed class SessionOutcome {
    data class Paired(val peer: DeviceInfo) : SessionOutcome()
    data class PairingFailed(val reason: String) : SessionOutcome()
    data object Disconnected : SessionOutcome()
}
