package ru.kyamshanov.notepen.sync.domain.port

/**
 * Durable store of the peer ids the host operator has granted the **Librarian**
 * role over this host's library (the per-peer write allow-set).
 *
 * M5a kept the librarian grants in-memory inside
 * [ru.kyamshanov.notepen.sync.domain.RemoteCatalogProvider]; M5b persists them
 * alongside the rest of the host's pairing state so a granted librarian survives
 * a host restart and is re-granted on the next startup.
 *
 * Implementations live in the application layer (where the data directory is
 * known) and must be thread-safe with atomic writes (temp file + rename), so an
 * interrupted save never leaves a truncated/corrupt file. Reading an absent or
 * unreadable store yields an empty set rather than throwing.
 *
 * The store is **optional** for the provider: a `null` store keeps the M5a
 * in-memory-only behaviour (e.g. Android, which never hosts a library over LAN).
 */
interface LibrarianGrantStore {
    /**
     * Loads the persisted librarian peer ids.
     *
     * @return the granted peer ids, or an empty set when nothing was saved yet
     *   or the backing file is missing/unreadable.
     */
    suspend fun load(): Set<String>

    /** Adds [peerId] to the persisted grant set (idempotent, atomic write). */
    suspend fun grant(peerId: String)

    /** Removes [peerId] from the persisted grant set (idempotent, atomic write). */
    suspend fun revoke(peerId: String)
}
