package ru.kyamshanov.notepen.qrconnect

import androidx.compose.runtime.Composable

/**
 * Requests the platform's local-network permission once, if the OS gates LAN
 * sockets behind one (Android 17+ `ACCESS_LOCAL_NETWORK`; without it mDNS
 * discovery and host connects fail silently). No-op everywhere else.
 *
 * Non-blocking: the pairing UI renders regardless of the outcome — a denial
 * later surfaces through the actionable connect-error messages.
 */
@Composable
expect fun LocalNetworkPermissionEffect()
