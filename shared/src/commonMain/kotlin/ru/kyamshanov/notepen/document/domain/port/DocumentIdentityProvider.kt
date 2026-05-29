package ru.kyamshanov.notepen.document.domain.port

import ru.kyamshanov.notepen.document.domain.model.DocumentIdentity

/**
 * Computes (and caches) the content-addressed [DocumentIdentity] of a document.
 *
 * The identity is derived from a `sha256` over the document's full original
 * bytes, so hashing is potentially expensive I/O. Implementations therefore:
 *
 * - run the digest off the caller's thread (inject a `CoroutineDispatcher`);
 * - **cache** results persistently (e.g. a sidecar next to the file, or a
 *   registry keyed by a cheap fingerprint), so repeated opens of the same file
 *   do not re-hash large content;
 * - expose a non-suspending [cachedWireIdForPath] for synchronous call sites
 *   (Compose's tab/document factories run outside a coroutine) — it returns the
 *   wire id only on a warm cache hit and never performs blocking I/O.
 *
 * Typical use: a UI warming effect calls [identityForPath] for every open
 * document to populate the cache; synchronous code then reads
 * [cachedWireIdForPath], falling back to a legacy id while the cache is cold.
 *
 * Implementations must be thread-safe.
 */
interface DocumentIdentityProvider {
    /**
     * Returns the [DocumentIdentity] for the file at [filePath], hashing its
     * full content on a cache miss and caching the result. Suspends for the
     * duration of the (possibly slow) digest + cache I/O.
     */
    suspend fun identityForPath(filePath: String): DocumentIdentity

    /**
     * Computes the [DocumentIdentity] for in-memory [bytes] with the given
     * [basename]. Does not touch the path-keyed cache (the caller supplies the
     * content directly); intended for callers that already hold the bytes.
     */
    suspend fun identityForBytes(
        basename: String,
        bytes: ByteArray,
    ): DocumentIdentity

    /**
     * Synchronous, non-blocking cache lookup. Returns the wire id previously
     * computed for [filePath] by [identityForPath], or `null` when the cache is
     * cold. Never performs I/O — safe to call from the UI thread / Compose.
     */
    fun cachedWireIdForPath(filePath: String): String?
}
