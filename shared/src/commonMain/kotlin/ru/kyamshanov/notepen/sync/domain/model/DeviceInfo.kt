package ru.kyamshanov.notepen.sync.domain.model

import kotlinx.serialization.Serializable

/**
 * Describes a peer device visible on the local network.
 *
 * @param id stable device identifier (UUID or platform-generated)
 * @param name human-readable display name (hostname / device name)
 * @param host IP address or hostname
 * @param port TCP port the peer's server listens on
 */
@Serializable
data class DeviceInfo(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
)
