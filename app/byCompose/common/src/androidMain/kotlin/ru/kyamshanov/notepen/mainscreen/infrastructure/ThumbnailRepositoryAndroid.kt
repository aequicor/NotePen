package ru.kyamshanov.notepen.mainscreen.infrastructure

import android.content.Context
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import ru.kyamshanov.notepen.mainscreen.domain.port.ThumbnailRepository

private val logger = KotlinLogging.logger {}

/**
 * Android-реализация [ThumbnailRepository].
 *
 * Диск-кеш в `context.cacheDir/thumbnails/`.
 * Ключ файла: `uri.hashCode().toString() + ".png"`.
 * Метаданные mtime: `uri.hashCode().toString() + ".meta"` (AC-22).
 * LRU-вытеснение при превышении [maxCacheSizeBytes] (50 МБ по умолчанию).
 * Таймаут I/O: 3 секунды на каждую операцию.
 */
class ThumbnailRepositoryAndroid(
    private val context: Context,
    private val maxCacheSizeBytes: Long = 50 * 1024 * 1024L,
) : ThumbnailRepository {
    private val cacheDir get() = java.io.File(context.cacheDir, "thumbnails").also { it.mkdirs() }
    private val mutex = Mutex()

    private fun keyToFile(uri: String): java.io.File = java.io.File(cacheDir, uri.hashCode().toString() + ".png")

    private fun keyToMetaFile(uri: String): java.io.File = java.io.File(cacheDir, uri.hashCode().toString() + ".meta")

    override suspend fun get(
        uri: String,
        currentFileMtime: Long?,
    ): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                withTimeout(3_000) {
                    val file = keyToFile(uri)
                    if (!file.exists()) return@withTimeout null
                    // Инвалидация по mtime (AC-22)
                    if (currentFileMtime != null) {
                        val metaFile = keyToMetaFile(uri)
                        if (metaFile.exists()) {
                            val storedMtime = metaFile.readText().toLongOrNull()
                            if (storedMtime != null && storedMtime != currentFileMtime) {
                                file.delete()
                                metaFile.delete()
                                return@withTimeout null
                            }
                        }
                    }
                    file.readBytes()
                }
            } catch (_: Exception) {
                null
            }
        }

    override suspend fun put(
        uri: String,
        imageData: ByteArray,
        fileMtime: Long?,
    ) = withContext(Dispatchers.IO) {
        try {
            withTimeout(3_000) {
                mutex.withLock {
                    keyToFile(uri).writeBytes(imageData)
                    fileMtime?.let { keyToMetaFile(uri).writeText(it.toString()) }
                    evictIfNeeded()
                }
            }
        } catch (e: Exception) {
            logger.warn { "Thumbnail cache write failed: ${e::class.simpleName}" }
        }
    }

    override suspend fun totalSizeBytes(): Long =
        withContext(Dispatchers.IO) {
            cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
        }

    private fun evictIfNeeded() {
        val pngFiles =
            cacheDir.listFiles()?.filter { it.extension == "png" }
                ?.sortedBy { it.lastModified() } ?: return
        var total = pngFiles.sumOf { it.length() }
        for (f in pngFiles) {
            if (total <= maxCacheSizeBytes) break
            total -= f.length()
            f.delete()
            val metaFile = java.io.File(f.path.replace(".png", ".meta"))
            if (metaFile.exists()) metaFile.delete()
        }
    }
}
