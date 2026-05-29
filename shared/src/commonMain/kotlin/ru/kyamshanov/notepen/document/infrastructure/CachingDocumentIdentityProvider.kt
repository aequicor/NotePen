package ru.kyamshanov.notepen.document.infrastructure

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.kyamshanov.notepen.document.domain.model.CanonicalBookId
import ru.kyamshanov.notepen.document.domain.model.DocumentIdentity
import ru.kyamshanov.notepen.document.domain.model.basenameOf
import ru.kyamshanov.notepen.document.domain.model.wireIdOf
import ru.kyamshanov.notepen.document.domain.port.DocumentIdentityProvider
import ru.kyamshanov.notepen.document.domain.port.IdentityCache
import ru.kyamshanov.notepen.document.domain.sha256Hex
import kotlin.concurrent.Volatile

/**
 * Content-addressed [DocumentIdentityProvider] that hashes a document's full
 * bytes with `sha256` and caches the result at two levels:
 *
 * 1. an in-memory map keyed by `path` (warms [cachedWireIdForPath] for the
 *    synchronous UI call sites and short-circuits repeated [identityForPath]);
 * 2. a persistent [IdentityCache], guarded by a `size-mtime` fingerprint, so a
 *    cold start does not re-read large files that were hashed in a prior run.
 *
 * Platform specifics are injected rather than branched on: [readBytes] supplies
 * the right byte source (a plain file on desktop, a `ContentResolver` stream on
 * Android) and [fingerprint] the cheap staleness guard. Hashing and cache I/O
 * run on [ioDispatcher]; nothing here blocks the caller's thread except the
 * pure in-memory lookups.
 *
 * @param readBytes reads the full content of a document path/URI. Suspends.
 * @param fingerprint cheap `size-mtime` style guard, or `null` when unavailable
 *   (e.g. a content URI whose metadata is not cheaply readable) — in that case
 *   the persistent cache simply always misses and the in-memory cache carries
 *   the result for the session.
 * @param persistentCache cross-run cache; pass a no-op implementation to disable.
 * @param ioDispatcher dispatcher for hashing + cache I/O.
 */
class CachingDocumentIdentityProvider(
    private val readBytes: suspend (path: String) -> ByteArray,
    private val fingerprint: (path: String) -> String? = { null },
    private val persistentCache: IdentityCache,
    private val ioDispatcher: CoroutineDispatcher,
) : DocumentIdentityProvider {
    private val mutex = Mutex()

    // path -> (fingerprint, sha256Hex). Read locklessly by the synchronous
    // cachedWireIdForPath; @Volatile gives safe publication of the immutable-map
    // reference swapped in by [store] (which holds [mutex] to serialise writers).
    @Volatile
    private var memory: Map<String, CachedDigest> = emptyMap()

    override suspend fun identityForPath(filePath: String): DocumentIdentity {
        val fp = withContext(ioDispatcher) { fingerprint(filePath) }
        val hex = resolveDigest(filePath, fp)
        return identity(filePath, hex)
    }

    private suspend fun resolveDigest(
        filePath: String,
        fp: String?,
    ): String {
        memory[filePath]?.takeIf { it.fingerprint == fp }?.let { return it.sha256Hex }
        return cachedOrComputed(filePath, fp)
    }

    private suspend fun cachedOrComputed(
        filePath: String,
        fp: String?,
    ): String {
        val fromCache = persistentCache.get(filePath, fp)
        val hex = fromCache ?: withContext(ioDispatcher) { sha256Hex(readBytes(filePath)) }
        store(filePath, fp, hex)
        if (fromCache == null) persistentCache.put(filePath, fp, hex)
        return hex
    }

    override suspend fun identityForBytes(
        basename: String,
        bytes: ByteArray,
    ): DocumentIdentity {
        val hex = withContext(ioDispatcher) { sha256Hex(bytes) }
        return DocumentIdentity(CanonicalBookId(hex), wireIdOf(basename, hex))
    }

    override fun cachedWireIdForPath(filePath: String): String? = memory[filePath]?.let { wireIdOf(basenameOf(filePath), it.sha256Hex) }

    private fun identity(
        filePath: String,
        hex: String,
    ): DocumentIdentity = DocumentIdentity(CanonicalBookId(hex), wireIdOf(basenameOf(filePath), hex))

    private suspend fun store(
        path: String,
        fingerprint: String?,
        sha256Hex: String,
    ) {
        mutex.withLock {
            memory = memory + (path to CachedDigest(fingerprint, sha256Hex))
        }
    }

    private data class CachedDigest(
        val fingerprint: String?,
        val sha256Hex: String,
    )
}
