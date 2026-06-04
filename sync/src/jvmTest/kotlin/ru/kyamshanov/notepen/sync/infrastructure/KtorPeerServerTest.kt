package ru.kyamshanov.notepen.sync.infrastructure

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.sendSerialized
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.websocket.Frame
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import ru.kyamshanov.notepen.sync.domain.Direction
import ru.kyamshanov.notepen.sync.domain.SessionCipher
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.model.ServerLifecycleState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * End-to-end DoS-control tests for [KtorPeerServer]: an approval that times out is
 * rejected and its bookkeeping is cleaned up, and a paired peer that floods the
 * channel is closed for policy violation. Both drive a real Ktor server + client
 * over loopback.
 */
class KtorPeerServerTest {
    private val selfInfo = DeviceInfo(id = "host-1", name = "Host", host = "127.0.0.1", port = 0)
    private val peerInfo = DeviceInfo(id = "peer-1", name = "Peer", host = "127.0.0.1", port = 0)
    private val wsJson = Json { classDiscriminator = "type" }

    private fun newClient(): HttpClient =
        HttpClient(CIO) {
            install(WebSockets) {
                maxFrameSize = MAX_WS_FRAME_SIZE_BYTES
                contentConverter = KotlinxWebsocketSerializationConverter(wsJson)
            }
        }

    private suspend fun KtorPeerServer.startRunning(): ServerLifecycleState.Running {
        start().getOrThrow()
        return lifecycle.filterIsInstance<ServerLifecycleState.Running>().first()
    }

    /**
     * Drives the client side of the cleartext pairing handshake plus the encrypted
     * key-confirmation, mirroring [KtorSyncClient]. Sends `PairRequest` with a fresh
     * nonce, awaits `PairAccepted`, derives the [SessionCipher], verifies the host's
     * encrypted hello and replies with the hello-ack. Returns the established cipher
     * so the test can then exchange encrypted [NetworkMessage]s, or `null` if the
     * server did not accept (caller asserts on that).
     */
    private suspend fun DefaultClientWebSocketSession.pairAndConfirm(
        code: String,
        self: DeviceInfo,
    ): SessionCipher? {
        val clientNonce = SecureChannel.newNonce()
        sendSerialized<NetworkMessage>(NetworkMessage.PairRequest(code, self, clientNonce))
        val accepted = receiveDeserialized<NetworkMessage>() as? NetworkMessage.PairAccepted ?: return null
        val cipher = SecureChannel.deriveCipher(code, clientNonce, accepted.serverNonce)
        val hello = SecureChannel.nextBinaryFrame(this) ?: return null
        SecureChannel.verifyHello(cipher, Direction.SERVER_TO_CLIENT, hello, expectedPeerNonce = accepted.serverNonce)
        send(Frame.Binary(fin = true, data = SecureChannel.buildHello(cipher, Direction.CLIENT_TO_SERVER, clientNonce)))
        return cipher
    }

    @Test
    fun approvalTimeoutRejectsThePeerAndCleansUp() =
        runBlocking {
            // Short real-clock approval window: withTimeoutOrNull uses the dispatcher's
            // delay (not the injected `now`), so a small Duration makes the wait fire fast.
            val server =
                KtorPeerServer(
                    selfInfo = selfInfo,
                    ioDispatcher = Dispatchers.IO,
                    approvalTimeout = 300.milliseconds,
                )
            // Count how many times THIS peer is announced for approval. After the first
            // attempt times out and is cleaned up, a reconnect must produce a SECOND
            // announcement (proving pendingPeers/approvalDeferreds were cleared, not stuck
            // in "approval in progress").
            val approvals = CompletableDeferred<Int>()
            val collectorReady = CompletableDeferred<Unit>()
            val scope = this
            val collector =
                scope.launch {
                    var seen = 0
                    server.pendingApprovals
                        .onSubscription { collectorReady.complete(Unit) }
                        .collect {
                            if (it.id == peerInfo.id) {
                                seen += 1
                                if (seen == 2) approvals.complete(seen)
                            }
                        }
                }
            val client = newClient()
            try {
                val running = server.startRunning()
                // Don't connect until the approval collector is actually subscribed, else the
                // first (replay-less) pendingApprovals emission could be missed.
                withTimeout(5.seconds) { collectorReady.await() }

                // First attempt: open a session, present the valid code, then never approve.
                // The server's bounded approval wait elapses and rejects us with a clear reason.
                val firstReply = CompletableDeferred<NetworkMessage>()
                client.webSocket(host = "127.0.0.1", port = running.port, path = "/ws") {
                    sendSerialized<NetworkMessage>(NetworkMessage.PairRequest(running.code, peerInfo))
                    firstReply.complete(receiveDeserialized<NetworkMessage>())
                }
                val reply = withTimeout(5.seconds) { firstReply.await() }
                assertTrue(
                    reply is NetworkMessage.PairRejected,
                    "a peer that is never approved must be rejected on timeout, got $reply",
                )
                assertEquals("approval timed out", (reply as NetworkMessage.PairRejected).reason)

                // Second attempt with the same device id: if cleanup worked, the server prompts
                // again (a fresh pending approval) rather than refusing with "approval in progress".
                scope.launch {
                    runCatching {
                        client.webSocket(host = "127.0.0.1", port = running.port, path = "/ws") {
                            sendSerialized<NetworkMessage>(NetworkMessage.PairRequest(running.code, peerInfo))
                            receiveDeserialized<NetworkMessage>()
                        }
                    }
                }
                val seenCount = withTimeout(5.seconds) { approvals.await() }
                assertEquals(2, seenCount, "reconnect after timeout must yield a second approval prompt")
            } finally {
                collector.cancel()
                runCatching { client.close() }
                server.stop()
            }
        }

    @Test
    fun messageFloodClosesTheSession() =
        runBlocking {
            // Tiny per-session budget so a short burst trips the flood guard.
            val server =
                KtorPeerServer(
                    selfInfo = selfInfo,
                    ioDispatcher = Dispatchers.IO,
                    maxMessagesPerSecond = 5,
                )
            val scope = this
            val approverReady = CompletableDeferred<Unit>()
            // Auto-approve the peer as soon as it is announced so we reach the receive loop.
            val approver =
                scope.launch {
                    server.pendingApprovals
                        .onSubscription { approverReady.complete(Unit) }
                        .collect { server.approve(it.id) }
                }
            val client = newClient()
            try {
                val running = server.startRunning()
                withTimeout(5.seconds) { approverReady.await() }
                // Distinguishes "paired, flooded, then closed by the server" (the signal we want)
                // from a handshake that never paired (which must fail the test, not pass it).
                val paired = CompletableDeferred<Boolean>()
                val closedAfterFlood = CompletableDeferred<Boolean>()
                scope.launch {
                    runCatching {
                        client.webSocket(host = "127.0.0.1", port = running.port, path = "/ws") {
                            // Full pair + key confirmation; the flood guard lives past it.
                            val cipher = pairAndConfirm(running.code, peerInfo)
                            paired.complete(cipher != null)
                            if (cipher == null) return@webSocket
                            // Flood well past the budget of 5 messages/second, now over the
                            // encrypted channel exactly as a real client would.
                            repeat(50) {
                                SecureChannel.sendEncrypted(this, cipher, Direction.CLIENT_TO_SERVER, wsJson, NetworkMessage.Ping)
                            }
                            // The server closes for VIOLATED_POLICY; our next receive throws and ends the loop.
                            while (true) {
                                SecureChannel.receiveEncrypted(this, cipher, Direction.SERVER_TO_CLIENT, wsJson) ?: break
                            }
                        }
                    }
                    if (!paired.isCompleted) paired.complete(false)
                    closedAfterFlood.complete(true)
                }
                assertTrue(withTimeout(5.seconds) { paired.await() }, "peer should pair before flooding")
                assertTrue(
                    withTimeout(10.seconds) { closedAfterFlood.await() },
                    "a flooding peer must have its session closed by the server",
                )
            } finally {
                approver.cancel()
                runCatching { client.close() }
                server.stop()
            }
        }
}
