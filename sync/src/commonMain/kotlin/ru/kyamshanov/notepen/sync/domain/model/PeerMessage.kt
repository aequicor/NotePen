package ru.kyamshanov.notepen.sync.domain.model

/**
 * A [NetworkMessage] received from a specific connected [peer].
 *
 * Used by the multi-client host server so consumers can address replies back
 * to the requester via
 * [ru.kyamshanov.notepen.sync.domain.port.PeerServer.send] with `peer.id`.
 */
data class PeerMessage(val peer: DeviceInfo, val message: NetworkMessage)
