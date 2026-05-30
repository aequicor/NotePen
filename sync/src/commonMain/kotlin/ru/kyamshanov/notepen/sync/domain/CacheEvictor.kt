package ru.kyamshanov.notepen.sync.domain

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import ru.kyamshanov.notepen.sync.domain.port.CacheFileStore
import ru.kyamshanov.notepen.sync.domain.port.CachedFile
import ru.kyamshanov.notepen.sync.domain.port.LocalDocumentIdRegistry
import ru.kyamshanov.notepen.sync.domain.port.OpenDocumentRegistry

private val logger = KotlinLogging.logger {}

/** Default cap on the total size of downloaded-document caches: 300 MB. */
const val DEFAULT_CACHE_CAP_BYTES: Long = 300L * 1024 * 1024

/**
 * Bounded LRU eviction for the on-disk caches of *downloaded* documents — the LAN cache
 * (`RemoteDocumentOpener`'s received dir) and the GitHub library cache. Both grow unbounded
 * otherwise (eviction was deferred to M6); this trims them back under a configurable byte cap.
 *
 * ## Policy
 * Across all configured [directories], every cached file is collected and sorted by
 * [CachedFile.lastAccessMillis] ascending (least-recently-used first). Files are deleted in that
 * order until the combined size of the survivors is at or below [capBytes]. When already under cap,
 * nothing is deleted.
 *
 * ## Never evict an open document
 * Before deleting, each file's path is resolved to a sync `documentId` via [documentIdRegistry]; if
 * that id is currently pinned in [openDocumentRegistry] (the editor has it open), the file is
 * **skipped** and its bytes still count toward the cap. This mirrors [LocalCachedDocumentCleaner]'s
 * guard and avoids deleting a file the user is actively viewing (and the hard failure that would
 * cause on Windows, where deleting an open file is rejected).
 *
 * The use-case is pure orchestration: filesystem work goes through the injected [CacheFileStore]
 * (off the caller's thread via [ioDispatcher]), open-state through the registries.
 *
 * @param fileStore platform file enumeration + delete.
 * @param openDocumentRegistry source of the currently-open `documentId` pin set.
 * @param documentIdRegistry maps a cached file path back to its `documentId` (null when the file was
 *   never opened through the opener — such files are always evictable).
 * @param ioDispatcher dispatcher for the (blocking) listing/delete I/O.
 * @param capBytes total byte budget across all caches; defaults to [DEFAULT_CACHE_CAP_BYTES].
 */
class CacheEvictor(
    private val fileStore: CacheFileStore,
    private val openDocumentRegistry: OpenDocumentRegistry,
    private val documentIdRegistry: LocalDocumentIdRegistry?,
    private val ioDispatcher: CoroutineDispatcher,
    private val capBytes: Long = DEFAULT_CACHE_CAP_BYTES,
) {
    /**
     * Enforces the cap across [directories]. Returns the number of files actually deleted.
     *
     * Safe to call repeatedly (e.g. at startup and after each download): a no-op when already under
     * cap. Missing directories are ignored.
     */
    suspend fun evict(directories: List<String>): Int =
        withContext(ioDispatcher) {
            val files = directories.flatMap { fileStore.listFiles(it) }
            var total = files.sumOf { it.sizeBytes }
            if (total <= capBytes) return@withContext 0

            val openIds = openDocumentRegistry.openDocumentIds.value
            // Least-recently-used first; stable secondary sort by path for determinism on ties.
            val candidates = files.sortedWith(compareBy({ it.lastAccessMillis }, { it.path })).iterator()
            var deleted = 0
            while (total > capBytes && candidates.hasNext()) {
                val freed = tryEvict(candidates.next(), openIds)
                if (freed > 0) {
                    total -= freed
                    deleted++
                }
            }
            deleted
        }

    /**
     * Attempts to evict [file], honouring the open-document guard. Returns the bytes freed (the
     * file's size on a successful delete) or `0` when the file is pinned-open, the delete failed, or
     * it was already gone. Keeping the result a byte count keeps [evict]'s loop branch-light.
     */
    private suspend fun tryEvict(
        file: CachedFile,
        openIds: Set<String>,
    ): Long {
        if (isOpen(file.path, openIds)) {
            logger.debug { "Keeping cached ${file.path}: document is open" }
            return 0L
        }
        val removed =
            runCatching { fileStore.delete(file.path) }.getOrElse {
                logger.warn { "Failed to evict ${file.path}: ${it::class.simpleName}" }
                false
            }
        if (removed) {
            documentIdRegistry?.forget(file.path)
            logger.info { "Evicted cached ${file.path} (${file.sizeBytes} B)" }
        }
        return if (removed) file.sizeBytes else 0L
    }

    private fun isOpen(
        path: String,
        openIds: Set<String>,
    ): Boolean {
        val docId = documentIdRegistry?.lookup(path) ?: return false
        return docId in openIds
    }
}
