package ru.kyamshanov.notepen.sync.domain.port

import kotlinx.coroutines.flow.Flow
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.model.PairingState

/**
 * Port for the client side of a peer connection.
 *
 * Connects to a server discovered via [DeviceDiscovery], presents a pairing
 * code, and exchanges [NetworkMessage]s once paired.
 */
interface SyncClient {

    /** Current connection / pairing state from the client's perspective. */
    val state: Flow<PairingState>

    /** Messages received from the server after [PairingState.Connected]. */
    val incomingMessages: Flow<NetworkMessage>

    /**
     * Connects to [server] and sends [pairingCode].
     *
     * Transitions [state] to [PairingState.Connected] on success, or
     * [PairingState.Error] if the code is rejected or the connection fails.
     */
    suspend fun connect(server: DeviceInfo, pairingCode: String, selfInfo: DeviceInfo)

    /** Sends [message] to the server. No-op if not connected. */
    suspend fun send(message: NetworkMessage)

    /** Closes the connection gracefully. */
    suspend fun disconnect()
}
