package ru.kyamshanov.notepen.sync.infrastructure

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.sendSerialized
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.model.PairingState
import ru.kyamshanov.notepen.sync.domain.port.SyncClient

private val logger = KotlinLogging.logger {}

/**
 * [SyncClient] backed by Ktor WebSocket.
 *
 * Engine is injected so the same class works on JVM (CIO) and Android (CIO).
 * The caller must install [WebSockets] on [client] before passing it in.
 */
class KtorSyncClient(private val client: HttpClient) : SyncClient {

    private val _state = MutableStateFlow<PairingState>(PairingState.Idle)
    override val state: Flow<PairingState> = _state.asStateFlow()

    private val _incoming = MutableSharedFlow<NetworkMessage>(extraBufferCapacity = 64)
    override val incomingMessages: Flow<NetworkMessage> = _incoming.asSharedFlow()

    /** Buffered outgoing queue; consumed by the active WebSocket session's forward coroutine. */
    private val _outgoing = Channel<NetworkMessage>(Channel.BUFFERED)

    private var sessionScope: CoroutineScope? = null

    override suspend fun connect(server: DeviceInfo, pairingCode: String, selfInfo: DeviceInfo) {
        val scope = CoroutineScope(Job())
        sessionScope = scope

        scope.launch {
            runCatching {
                client.webSocket(host = server.host, port = server.port, path = "/ws") {
                    sendSerialized<NetworkMessage>(NetworkMessage.PairRequest(code = pairingCode, device = selfInfo))

                    when (val reply = receiveDeserialized<NetworkMessage>()) {
                        is NetworkMessage.PairAccepted -> {
                            _state.value = PairingState.Connected(reply.serverDevice)
                            logger.info { "Paired with ${reply.serverDevice.name}" }
                        }
                        is NetworkMessage.PairRejected -> {
                            _state.value = PairingState.Error("Pairing rejected: ${reply.reason}")
                            return@webSocket
                        }
                        else -> {
                            _state.value = PairingState.Error("Unexpected reply: $reply")
                            return@webSocket
                        }
                    }

                    // Forward outgoing messages from the shared channel to this session.
                    launch {
                        for (msg in _outgoing) {
                            sendSerialized<NetworkMessage>(msg)
                        }
                    }

                    while (true) {
                        val msg = receiveDeserialized<NetworkMessage>()
                        if (msg is NetworkMessage.Disconnect) break
                        _incoming.emit(msg)
                    }
                }
            }.onFailure { e ->
                logger.warn { "SyncClient disconnected: ${e.message}" }
                _state.value = PairingState.Error(e.message ?: "connection error")
            }
        }
    }

    override suspend fun send(message: NetworkMessage) {
        _outgoing.trySend(message)
    }

    override suspend fun disconnect() {
        sessionScope?.cancel()
        sessionScope = null
        _state.value = PairingState.Idle
    }
}
