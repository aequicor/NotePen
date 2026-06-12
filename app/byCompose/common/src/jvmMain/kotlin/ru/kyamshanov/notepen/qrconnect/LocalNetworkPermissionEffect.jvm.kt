package ru.kyamshanov.notepen.qrconnect

import androidx.compose.runtime.Composable

/** Desktop has no local-network permission gate — nothing to request. */
@Composable
actual fun LocalNetworkPermissionEffect() = Unit
