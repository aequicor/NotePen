package ru.kyamshanov.notepen.sync.domain.model

/**
 * Snapshot of the platform conditions that gate LAN sync.
 *
 * @property localNetworkPermissionGranted whether the OS lets this app open
 *   sockets on the local network (Android 17+ gates this behind the
 *   `ACCESS_LOCAL_NETWORK` runtime permission; older platforms always grant).
 * @property vpnActive whether a VPN tunnel is currently up. On Android a
 *   default-configured VPN routes ALL app traffic — including LAN sockets —
 *   into the tunnel, so an active VPN is the most common reason discovery and
 *   connect silently fail on an otherwise healthy network.
 */
data class LocalNetworkHealth(
    val localNetworkPermissionGranted: Boolean,
    val vpnActive: Boolean,
)
