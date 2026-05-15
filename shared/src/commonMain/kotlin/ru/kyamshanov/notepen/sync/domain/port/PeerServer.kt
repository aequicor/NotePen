package ru.kyamshanov.notepen.sync.domain.port

import kotlinx.coroutines.flow.Flow
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.model.PairingState

/**
 * Port for the local peer server.
 *
 * The server listens on a TCP port, broadcasts itself via mDNS,
 * manages pairing, and multiplexes [NetworkMessage]s once paired.
 */
interface PeerServer {

    /** Current server / pairing state. Starts as [PairingState.Idle]. */
    val state: Flow<PairingState>

    /** Messages received from the paired peer after [PairingState.Connected]. */
    val incomingMessages: Flow<NetworkMessage>

    /**
     * Starts the server and begins mDNS registration.
     *
     * Returns the 6-digit pairing code clients must present.
     * Transitions [state] to [PairingState.AwaitingConnection].
     */
    suspend fun start(): Result<String>

    /** Sends [message] to the currently connected peer. No-op if not connected. */
    suspend fun send(message: NetworkMessage)

    /** Stops the server, closes connections, and unregisters mDNS. */
    suspend fun stop()
}
