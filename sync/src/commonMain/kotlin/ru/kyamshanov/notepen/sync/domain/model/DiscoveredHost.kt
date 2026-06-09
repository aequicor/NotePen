package ru.kyamshanov.notepen.sync.domain.model

/**
 * A NotePen host found on the local network via mDNS (service type
 * `_notepen._tcp`). Carries the resolved [deviceInfo] (host/port/name) needed to
 * reach the host. The pairing [code] is **not** advertised on the LAN, so it is
 * normally blank here and the host must be paired with a code obtained out of
 * band (QR scan or manual entry).
 *
 * @param code pairing code, if already known; forwarded to
 *   [ru.kyamshanov.notepen.sync.domain.port.SyncClient.connect]. Blank for hosts
 *   surfaced by mDNS discovery — the UI then routes to a manual code prompt.
 */
data class DiscoveredHost(
    val deviceInfo: DeviceInfo,
    val code: String,
)
