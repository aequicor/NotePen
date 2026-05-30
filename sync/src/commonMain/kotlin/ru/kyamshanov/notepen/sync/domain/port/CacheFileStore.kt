package ru.kyamshanov.notepen.sync.domain.port

/**
 * One cached file on disk, as seen by the cache evictor.
 *
 * @property path absolute path to the file.
 * @property sizeBytes file size in bytes.
 * @property lastAccessMillis best-available "last touched" time in epoch millis, used as the LRU
 *   key. Implementations prefer the OS last-access time and fall back to last-modified when the
 *   platform/filesystem does not track access time.
 */
data class CachedFile(
    val path: String,
    val sizeBytes: Long,
    val lastAccessMillis: Long,
)

/**
 * Platform file operations the cache evictor needs: enumerate a cache directory and delete a file.
 *
 * Kept as a port so the pure [ru.kyamshanov.notepen.sync.domain.CacheEvictor] use-case stays free of
 * `java.io` / `okio`; the concrete implementation lives in a platform source set.
 */
interface CacheFileStore {
    /**
     * Lists every regular file under [directory] (recursively), with size and last-access time.
     * Returns an empty list when [directory] does not exist. Must not throw on a missing directory.
     */
    suspend fun listFiles(directory: String): List<CachedFile>

    /** Best-effort delete of the file at [path]. Returns `true` if the file is gone afterwards. */
    suspend fun delete(path: String): Boolean
}
