package ru.kyamshanov.notepen.sync.infrastructure

import io.github.oshai.kotlinlogging.KotlinLogging
import ru.kyamshanov.notepen.sync.domain.port.CacheFileStore
import ru.kyamshanov.notepen.sync.domain.port.CachedFile
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * Android [CacheFileStore] over `java.io.File`.
 *
 * Uses last-modified as the LRU key (the NIO `lastAccessTime` API needs API 26, above this module's
 * minSdk 24, and many Android filesystems mount `noatime` anyway, so access time would be unreliable
 * even when available). Walks the cache directory recursively; deletes are best-effort.
 */
class AndroidCacheFileStore : CacheFileStore {
    override suspend fun listFiles(directory: String): List<CachedFile> {
        val root = File(directory)
        if (!root.isDirectory) return emptyList()
        return root.walkTopDown()
            .filter { it.isFile }
            .map { file ->
                CachedFile(
                    path = file.absolutePath,
                    sizeBytes = file.length(),
                    lastAccessMillis = file.lastModified(),
                )
            }
            .toList()
    }

    override suspend fun delete(path: String): Boolean {
        val file = File(path)
        return runCatching { !file.exists() || file.delete() }
            .getOrElse {
                logger.warn { "delete($path) failed: ${it::class.simpleName}" }
                false
            }
    }
}
