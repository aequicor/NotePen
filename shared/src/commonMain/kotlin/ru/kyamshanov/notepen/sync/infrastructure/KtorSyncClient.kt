package ru.kyamshanov.notepen.sync.infrastructure

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.sendSerialized
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.model.PairingState
import ru.kyamshanov.notepen.sync.domain.port.SyncClient

private val logger = KotlinLogging.logger {}

private const val RECONNECT_DEADLINE_MS = 10_000L
private const val RECONNECT_RETRY_INTERVAL_MS = 1_000L

/**
 * [SyncClient] backed by Ktor WebSocket.
 *
 * Engine is injected so the same class works on JVM (CIO) and Android (CIO).
 * The caller must install [WebSockets] on [client] before passing it in.
 *
 * On unexpected disconnection the client transparently retries connecting for
 * [RECONNECT_DEADLINE_MS] ms, emitting [PairingState.Reconnecting] each second.
 * If the deadline elapses without success, state becomes
 * [PairingState.LostConnection].
 */
class KtorSyncClient(private val client: HttpClient) : SyncClient {

    private val _state = MutableStateFlow<PairingState>(PairingState.Idle)
    override val state: Flow<PairingState> = _state.asStateFlow()

    private val _incoming = MutableSharedFlow<NetworkMessage>(extraBufferCapacity = 64)
    override val incomingMessages: Flow<NetworkMessage> = _incoming.asSharedFlow()

    /** Buffered outgoing queue; consumed by the active WebSocket session's forward coroutine. */
    private val _outgoing = Channel<NetworkMessage>(Channel.BUFFERED)

    private var sessionScope: CoroutineScope? = null
    private var explicitlyDisconnected = false

    override suspend fun connect(server: DeviceInfo, pairingCode: String, selfInfo: DeviceInfo) {
        explicitlyDisconnected = false
        val scope = CoroutineScope(Job())
        sessionScope = scope

        scope.launch {
            var firstAttempt = true
            while (scope.isActive && !explicitlyDisconnected) {
                val ok = runSession(server, pairingCode, selfInfo, firstAttempt)
                firstAttempt = false
                if (explicitlyDisconnected) return@launch
                if (ok == SessionOutcome.PairingFailed) return@launch
                // Connection dropped; try to reconnect inside the 10 s deadline.
                if (!attemptReconnect(scope, server, pairingCode, selfInfo)) return@launch
            }
        }
    }

    private suspend fun runSession(
        server: DeviceInfo,
        pairingCode: String,
        selfInfo: DeviceInfo,
        firstAttempt: Boolean,
    ): SessionOutcome {
        var outcome: SessionOutcome = SessionOutcome.Disconnected
        try {
            client.webSocket(host = server.host, port = server.port, path = "/ws") {
                sendSerialized<NetworkMessage>(NetworkMessage.PairRequest(code = pairingCode, device = selfInfo))

                when (val reply = receiveDeserialized<NetworkMessage>()) {
                    is NetworkMessage.PairAccepted -> {
                        _state.value = PairingState.Connected(reply.serverDevice)
                        logger.info { "Paired with ${reply.serverDevice.name}" }
                    }
                    is NetworkMessage.PairRejected -> {
                        _state.value = PairingState.Error("Pairing rejected: ${reply.reason}")
                        outcome = if (firstAttempt) SessionOutcome.PairingFailed else SessionOutcome.Disconnected
                        return@webSocket
                    }
                    else -> {
                        _state.value = PairingState.Error("Unexpected reply: $reply")
                        outcome = SessionOutcome.PairingFailed
                        return@webSocket
                    }
                }

                // Forward outgoing messages from the shared channel to this session.
                val forwarder = launch {
                    for (msg in _outgoing) {
                        sendSerialized<NetworkMessage>(msg)
                    }
                }

                try {
                    while (true) {
                        val msg = receiveDeserialized<NetworkMessage>()
                        if (msg is NetworkMessage.Disconnect) break
                        _incoming.emit(msg)
                    }
                } finally {
                    forwarder.cancel()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn { "SyncClient session ended: ${e.message}" }
        }
        return outcome
    }

    private suspend fun attemptReconnect(
        scope: CoroutineScope,
        server: DeviceInfo,
        pairingCode: String,
        selfInfo: DeviceInfo,
    ): Boolean {
        val start = TimeSource.Monotonic.markNow()
        val deadline = RECONNECT_DEADLINE_MS.milliseconds
        while (scope.isActive && !explicitlyDisconnected) {
            val remainingMs = (deadline - start.elapsedNow()).inWholeMilliseconds
            if (remainingMs <= 0) {
                _state.value = PairingState.LostConnection
                return false
            }
            _state.value = PairingState.Reconnecting(
                secondsRemaining = ((remainingMs + 999) / 1000).toInt(),
            )
            val pingOk = try {
                client.webSocket(host = server.host, port = server.port, path = "/ws") {
                    sendSerialized<NetworkMessage>(NetworkMessage.PairRequest(code = pairingCode, device = selfInfo))
                    when (val reply = receiveDeserialized<NetworkMessage>()) {
                        is NetworkMessage.PairAccepted -> {
                            _state.value = PairingState.Connected(reply.serverDevice)
                        }
                        else -> {
                            _state.value = PairingState.Error("Reconnect rejected")
                            return@webSocket
                        }
                    }
                    val forwarder = launch {
                        for (msg in _outgoing) sendSerialized<NetworkMessage>(msg)
                    }
                    try {
                        while (true) {
                            val msg = receiveDeserialized<NetworkMessage>()
                            if (msg is NetworkMessage.Disconnect) break
                            _incoming.emit(msg)
                        }
                    } finally {
                        forwarder.cancel()
                    }
                }
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.debug { "Reconnect attempt failed: ${e.message}" }
                false
            }
            if (pingOk && _state.value is PairingState.Connected) {
                // Session completed cleanly after a successful reconnect; loop will retry again if needed.
                return true
            }
            delay(RECONNECT_RETRY_INTERVAL_MS)
        }
        return false
    }

    override suspend fun send(message: NetworkMessage) {
        // Suspending send: applies back-pressure when the buffered outgoing
        // channel (capacity 64) is full. The earlier `trySend` silently
        // dropped overflow, which corrupted erase batches that emit
        // hundreds of deltas in one burst.
        _outgoing.send(message)
    }

    override suspend fun disconnect() {
        explicitlyDisconnected = true
        sessionScope?.cancel()
        sessionScope = null
        _state.value = PairingState.Idle
    }

    private enum class SessionOutcome { Disconnected, PairingFailed }
}
