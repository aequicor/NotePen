package ru.kyamshanov.notepen.sync.domain.port

import kotlinx.coroutines.flow.Flow
import ru.kyamshanov.notepen.sync.domain.model.DiscoveredHost

/**
 * Discovers NotePen hosts advertised on the local network via mDNS (service
 * type `_notepen._tcp`), so a client can connect without scanning a QR.
 *
 * Implemented per platform: Android uses `NsdManager`. Desktop is host-only and
 * provides no implementation (the client-side discovery UI is simply absent).
 */
interface PeerDiscovery {
    /**
     * Hosts currently visible on the LAN. Emits a fresh snapshot as services
     * appear/disappear. Empty until [start] resolves at least one host; a host
     * leaves the set when its mDNS service is lost.
     */
    val discoveredHosts: Flow<Set<DiscoveredHost>>

    /** Begins browsing. Idempotent — a second call while running is a no-op. */
    fun start()

    /** Stops browsing and releases platform resources. Idempotent. */
    fun stop()
}
