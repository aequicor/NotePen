package ru.kyamshanov.notepen.sync.domain.port

import kotlinx.coroutines.flow.Flow
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.HostMessage
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.model.PairingState

/**
 * Port for the client side of peer connections.
 *
 * Supports **multiple simultaneously connected hosts**. Each host is keyed by
 * the `DeviceInfo.id` returned in the successful `Result` of [connect].
 * Per-host state is exposed via [pairingStates]; aggregated set of currently
 * paired hosts via [connectedHosts]. Messages arrive attributed to the
 * originating host via [incomingMessages].
 */
interface SyncClient {

    /**
     * Map of `hostId → PairingState` for every host the client has ever tried
     * to connect to during this session — including hosts that are currently
     * `Reconnecting`, `Error` or `LostConnection`.
     */
    val pairingStates: Flow<Map<String, PairingState>>

    /** Hosts currently in [PairingState.Connected]. Replays the latest snapshot. */
    val connectedHosts: Flow<Set<DeviceInfo>>

    /** Inbound messages, each tagged with the host that sent it. */
    val incomingMessages: Flow<HostMessage>

    /**
     * Initiates a connection to [server]. The method **returns when pairing
     * either completes (success) or definitively fails** — caller can rely on
     * the `Result` to know whether the new host appears in [connectedHosts].
     *
     * On success returns the paired server's [DeviceInfo] (as announced by the
     * host in `PairAccepted`).
     */
    suspend fun connect(
        server: DeviceInfo,
        pairingCode: String,
        selfInfo: DeviceInfo,
    ): Result<DeviceInfo>

    /** Sends [message] to the host identified by [hostId]. No-op if unknown. */
    suspend fun send(hostId: String, message: NetworkMessage)

    /** Sends [message] to every currently connected host. */
    suspend fun broadcast(message: NetworkMessage)

    /** Disconnects from a single host; other hosts remain connected. */
    suspend fun disconnect(hostId: String)

    /** Disconnects from every host. */
    suspend fun disconnectAll()
}
