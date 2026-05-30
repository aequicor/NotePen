package ru.kyamshanov.notepen.sync.domain.model

/**
 * A NotePen host found on the local network via mDNS (service type
 * `_notepen._tcp`). Carries everything needed for a one-tap connect: the
 * resolved [deviceInfo] (host/port/name) and the pairing [code] read from the
 * service's `c` TXT record.
 *
 * @param code pairing code published by the host; forwarded to
 *   [ru.kyamshanov.notepen.sync.domain.port.SyncClient.connect]. Blank when the
 *   host advertised an older record without it — the UI then falls back to a
 *   manual code prompt.
 */
data class DiscoveredHost(
    val deviceInfo: DeviceInfo,
    val code: String,
)
