package ru.kyamshanov.notepen.server

import io.github.oshai.kotlinlogging.KotlinLogging
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.model.PairingState
import ru.kyamshanov.notepen.sync.domain.port.PeerServer
import java.net.InetAddress
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

/**
 * Ktor CIO [PeerServer] implementation.
 *
 * Starts an HTTP/WebSocket server on a random available port. After [start],
 * callers should register an mDNS service using [state]'s [PairingState.AwaitingConnection]
 * to let peers discover the port.
 *
 * Only one client is accepted at a time; subsequent connections are rejected
 * while a session is active.
 *
 * @param selfInfo description of this device sent to peers after pairing
 * @param ioDispatcher dispatcher for Ktor engine I/O
 */
class KtorPeerServer(
    private val selfInfo: DeviceInfo,
    private val ioDispatcher: CoroutineDispatcher,
) : PeerServer {

    private val _state = MutableStateFlow<PairingState>(PairingState.Idle)
    override val state: Flow<PairingState> = _state.asStateFlow()

    private val _incoming = MutableSharedFlow<NetworkMessage>(extraBufferCapacity = 64)
    override val incomingMessages: Flow<NetworkMessage> = _incoming.asSharedFlow()

    private val pairing = PairingManager()
    private val json = Json { classDiscriminator = "type" }

    private var engine: EmbeddedServer<*, *>? = null
    private var serverPort: Int = 0
    private var activeSession: DefaultWebSocketServerSession? = null
    private val serverScope = CoroutineScope(ioDispatcher + Job())

    override suspend fun start(): Result<String> = runCatching {
        val code = pairing.generateCode()
        val host = InetAddress.getLocalHost().hostAddress ?: "127.0.0.1"
        val port = findFreePort()
        serverPort = port

        engine = embeddedServer(CIO, port = port) {
            install(ContentNegotiation) { json(json) }
            install(WebSockets) { pingPeriod = 30.seconds }
            routing {
                webSocket("/ws") { handleSession(this) }
            }
        }.also { it.start(wait = false) }

        _state.value = PairingState.AwaitingConnection(code = code, host = host, port = port)
        logger.info { "PeerServer started on $host:$port (code=$code)" }
        code
    }

    private fun findFreePort(): Int = java.net.ServerSocket(0).use { it.localPort }

    private suspend fun handleSession(session: DefaultWebSocketServerSession) {
        if (activeSession != null) {
            logger.warn { "Rejecting extra connection — already have an active session" }
            session.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "already connected"))
            return
        }
        _state.value = PairingState.AwaitingCode

        try {
            val first = session.receiveDeserialized<NetworkMessage>()
            if (first !is NetworkMessage.PairRequest) {
                session.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "expected pair_request"))
                _state.value = PairingState.AwaitingConnection(
                    code = pairing.generateCode(),
                    host = ((_state.value as? PairingState.AwaitingConnection)?.host ?: ""),
                    port = ((_state.value as? PairingState.AwaitingConnection)?.port ?: 0),
                )
                return
            }

            if (!pairing.validateAndConsume(first.code)) {
                session.sendSerialized<NetworkMessage>(NetworkMessage.PairRejected("invalid code"))
                session.close()
                resetToAwaitingConnection()
                return
            }

            session.sendSerialized<NetworkMessage>(NetworkMessage.PairAccepted(selfInfo))
            activeSession = session
            _state.value = PairingState.Connected(first.device)
            logger.info { "Paired with ${first.device.name}" }

            for (frame in session.incoming) {
                runCatching {
                    val msg = session.receiveDeserialized<NetworkMessage>()
                    if (msg is NetworkMessage.Disconnect) return@runCatching
                    _incoming.emit(msg)
                }
            }
        } catch (e: Exception) {
            logger.warn { "Session ended: ${e.message}" }
        } finally {
            activeSession = null
            resetToAwaitingConnection()
        }
    }

    private fun resetToAwaitingConnection() {
        val current = _state.value
        if (current is PairingState.AwaitingConnection || current is PairingState.Idle) return
        serverScope.launch {
            if (engine == null) return@launch
            val code = pairing.generateCode()
            val host = InetAddress.getLocalHost().hostAddress ?: "127.0.0.1"
            _state.value = PairingState.AwaitingConnection(code = code, host = host, port = serverPort)
        }
    }

    override suspend fun send(message: NetworkMessage) {
        activeSession?.sendSerialized<NetworkMessage>(message)
    }

    override suspend fun stop() {
        pairing.invalidate()
        activeSession?.close(CloseReason(CloseReason.Codes.GOING_AWAY, "server stopping"))
        activeSession = null
        engine?.stop()
        engine = null
        _state.value = PairingState.Idle
        logger.info { "PeerServer stopped" }
    }
}
