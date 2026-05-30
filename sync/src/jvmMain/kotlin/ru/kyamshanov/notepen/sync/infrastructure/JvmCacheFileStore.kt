package ru.kyamshanov.notepen.sync.infrastructure

import io.github.oshai.kotlinlogging.KotlinLogging
import ru.kyamshanov.notepen.sync.domain.port.CacheFileStore
import ru.kyamshanov.notepen.sync.domain.port.CachedFile
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes

private val logger = KotlinLogging.logger {}

/**
 * JVM/desktop [CacheFileStore] over `java.io` / `java.nio`.
 *
 * Walks a cache directory recursively, reporting each regular file with its size and a best-effort
 * last-access time (the NIO `lastAccessTime`, falling back to `lastModifiedTime` when the filesystem
 * does not track access). Deletes are best-effort.
 */
class JvmCacheFileStore : CacheFileStore {
    override suspend fun listFiles(directory: String): List<CachedFile> {
        val root = File(directory)
        if (!root.isDirectory) return emptyList()
        return root.walkTopDown()
            .filter { it.isFile }
            .map { file ->
                val accessMillis =
                    runCatching {
                        val attrs = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
                        val access = attrs.lastAccessTime().toMillis()
                        if (access > 0L) access else attrs.lastModifiedTime().toMillis()
                    }.getOrElse { file.lastModified() }
                CachedFile(path = file.absolutePath, sizeBytes = file.length(), lastAccessMillis = accessMillis)
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
