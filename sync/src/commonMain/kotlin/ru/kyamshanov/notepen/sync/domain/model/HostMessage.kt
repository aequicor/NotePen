package ru.kyamshanov.notepen.sync.domain.model

/**
 * A [NetworkMessage] received from a specific connected [host].
 *
 * Used by the multi-host client so consumers can route replies back to the
 * specific host that originated the request via
 * [ru.kyamshanov.notepen.sync.domain.port.SyncClient.send] with `host.id`.
 */
data class HostMessage(val host: DeviceInfo, val message: NetworkMessage)
