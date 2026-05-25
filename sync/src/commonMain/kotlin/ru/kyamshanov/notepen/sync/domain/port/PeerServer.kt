package ru.kyamshanov.notepen.sync.domain.port

import kotlinx.coroutines.flow.Flow
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.model.PeerMessage
import ru.kyamshanov.notepen.sync.domain.model.ServerLifecycleState

/**
 * Port for the local peer server.
 *
 * The server listens on a TCP port, broadcasts itself via mDNS, manages
 * per-peer pairing, and multiplexes [NetworkMessage]s for multiple
 * simultaneously connected clients.
 *
 * **Pairing**: one [ServerLifecycleState.Running.code] is generated per
 * [start] and is reusable — any number of clients may pair with the same
 * code while the server is running. Each new client triggers a
 * [pendingApprovals] event; the UI calls [approve] or [reject].
 *
 * **Per-peer addressing**: every connected client is identified by
 * [DeviceInfo.id]. Use [send] for RPC-style replies and [broadcast] for
 * fan-out (stroke deltas, projection frames).
 */
interface PeerServer {
    /** Server lifecycle — starts as [ServerLifecycleState.Idle]. */
    val lifecycle: Flow<ServerLifecycleState>

    /** Currently approved peers. Replays the latest snapshot to new collectors. */
    val connectedPeers: Flow<Set<DeviceInfo>>

    /**
     * Clients that have presented a valid pairing code and are waiting for the
     * user to [approve] or [reject] them. Each peer appears exactly once per
     * connection attempt.
     */
    val pendingApprovals: Flow<DeviceInfo>

    /** Messages received from approved peers, attributed to the sender. */
    val incomingMessages: Flow<PeerMessage>

    /**
     * Starts the server and begins mDNS registration.
     *
     * On success, transitions [lifecycle] to [ServerLifecycleState.Running]
     * and returns the running state (host, port, code).
     */
    suspend fun start(): Result<ServerLifecycleState.Running>

    /** Sends [message] to the peer identified by [peerId]. No-op if unknown. */
    suspend fun send(
        peerId: String,
        message: NetworkMessage,
    )

    /** Sends [message] to every currently connected peer. */
    suspend fun broadcast(message: NetworkMessage)

    /** Accepts a pending peer; moves it from [pendingApprovals] into [connectedPeers]. */
    suspend fun approve(peerId: String)

    /**
     * Rejects a pending peer; sends [NetworkMessage.Disconnect] and closes the
     * underlying session. The peer never appears in [connectedPeers].
     */
    suspend fun reject(peerId: String)

    /** Closes the session for one connected peer, leaving the server running. */
    suspend fun disconnect(peerId: String)

    /** Closes every connected peer's session, leaving the server running. */
    suspend fun disconnectAll()

    /** Stops the server, closes connections, and unregisters mDNS. */
    suspend fun stop()
}
