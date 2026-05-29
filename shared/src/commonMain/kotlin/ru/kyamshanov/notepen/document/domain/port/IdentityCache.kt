package ru.kyamshanov.notepen.document.domain.port

/**
 * Persistent cache of computed canonical identities, so opening a file the app
 * has hashed before does not re-read and re-digest its full content.
 *
 * Entries are keyed by document path and guarded by a cheap [fingerprint]
 * (typically `size-mtime`): when the stored fingerprint no longer matches the
 * file on disk the cached digest is treated as stale and ignored, forcing a
 * re-hash. This is what keeps an in-place edit from resolving to the old id.
 *
 * Platform implementations differ in *where* they persist:
 * - desktop: a `<file>.notepen.id` sidecar next to the document;
 * - Android: a small registry file under app-private storage (content URIs
 *   have no writable "next to" location).
 *
 * Implementations must be thread-safe.
 */
interface IdentityCache {
    /**
     * Returns the cached `sha256` hex digest for [path] iff an entry exists and
     * its stored fingerprint equals [fingerprint]; otherwise `null` (miss or
     * stale). A `null` [fingerprint] (file missing) is always a miss.
     */
    suspend fun get(
        path: String,
        fingerprint: String?,
    ): String?

    /** Stores [sha256Hex] for [path] together with its [fingerprint] guard. */
    suspend fun put(
        path: String,
        fingerprint: String?,
        sha256Hex: String,
    )
}
