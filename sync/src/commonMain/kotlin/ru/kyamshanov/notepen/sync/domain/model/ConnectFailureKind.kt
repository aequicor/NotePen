package ru.kyamshanov.notepen.sync.domain.model

/**
 * Coarse classification of why a client→host connect attempt failed.
 *
 * The transport layer can rarely tell these apart from raw exceptions alone —
 * notably, Android 17's local-network permission makes blocked LAN traffic
 * fail *silently* (outbound TCP times out, UDP gets EPERM) instead of raising
 * a permission error — so the kinds here are best-effort buckets the UI can
 * turn into actionable messages ("check VPN / local-network permission")
 * rather than a generic "host offline".
 */
enum class ConnectFailureKind {
    /** No reply within the connect deadline — host offline, wrong network, or silently blocked LAN. */
    TIMED_OUT,

    /** The OS refused the socket outright (EPERM / permission denied) — local network access is blocked. */
    LOCAL_NETWORK_BLOCKED,

    /** The host machine answered but nothing listens on the port (server stopped). */
    CONNECTION_REFUSED,

    /** Transport worked; the host rejected the pairing (bad code, unexpected reply, key mismatch). */
    PAIRING_REJECTED,

    /** Anything we could not classify. */
    UNKNOWN,
}
