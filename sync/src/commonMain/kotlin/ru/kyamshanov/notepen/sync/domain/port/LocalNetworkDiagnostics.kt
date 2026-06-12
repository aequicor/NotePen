package ru.kyamshanov.notepen.sync.domain.port

import ru.kyamshanov.notepen.sync.domain.model.LocalNetworkHealth

/**
 * Reads the platform conditions that gate LAN sync (local-network permission,
 * active VPN) so connect failures can be explained to the user instead of
 * surfacing as a generic timeout.
 *
 * Implemented per platform (Android: ConnectivityManager + permission check);
 * platforms with nothing to report may simply return an all-clear snapshot.
 */
interface LocalNetworkDiagnostics {
    /** Current snapshot; cheap enough to call at failure time. */
    fun snapshot(): LocalNetworkHealth
}
