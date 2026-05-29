package ru.kyamshanov.notepen.reflow

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.kyamshanov.notepen.reflow.api.REFLOW_PARSER_VERSION
import ru.kyamshanov.notepen.reflow.api.ReflowDocument
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.time.TimeSource

private val cacheLogger = KotlinLogging.logger {}
private const val HEX_DIGITS = "0123456789abcdef"
private const val CACHE_FILE_SUFFIX = ".reflow.bin"
private const val CACHE_KEY_HEX_CHARS = 48

/**
 * Хранит сериализованные [ReflowDocument] в [reflowCacheDir] на диске.
 *
 * Имя файла — `sha256(path)[..48 hex].reflow.bin`; валидность кэша — по
 * `(sourceSize, sourceMtime)` из [statReflowSource]. При промахе/устаревании/ошибке
 * чтения декоратор-извлекатель просто экстрактит заново.
 */
internal class FileSystemReflowDocumentDiskCache(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    cacheDir: File? = null,
    private val stat: (String) -> SourceStat? = ::statReflowSource,
) : ReflowDocumentDiskCache {
    private val dir: File by lazy { (cacheDir ?: File(reflowCacheDir())).also { it.mkdirs() } }

    override suspend fun read(path: String): ReflowDocument? =
        withContext(ioDispatcher) {
            val stat = stat(path)
            if (stat == null) {
                cacheLogger.info { "PdfReflow: cache stat-fail path=${maskPath(path)}" }
                return@withContext null
            }
            val file = File(dir, fileNameFor(path))
            if (!file.exists()) {
                cacheLogger.info { "PdfReflow: cache miss path=${maskPath(path)}" }
                return@withContext null
            }
            val mark = TimeSource.Monotonic.markNow()
            runCatching {
                file.inputStream().use { ReflowBinaryFormat.read(it) }
            }.fold(
                onSuccess = { cached ->
                    val decodeMs = mark.elapsedNow().inWholeMilliseconds
                    val staleSource = cached.sourceSize != stat.size || cached.sourceMtime != stat.mtime
                    val staleParser = cached.parserVersion != REFLOW_PARSER_VERSION
                    if (staleSource || staleParser) {
                        cacheLogger.info {
                            "PdfReflow: cache stale path=${maskPath(path)} " +
                                "cachedSize=${cached.sourceSize} actualSize=${stat.size} " +
                                "cachedMtime=${cached.sourceMtime} actualMtime=${stat.mtime} " +
                                "cachedParser=${cached.parserVersion} actualParser=$REFLOW_PARSER_VERSION"
                        }
                        file.delete()
                        null
                    } else {
                        cacheLogger.info {
                            "PdfReflow: cache hit path=${maskPath(path)} " +
                                "blocks=${cached.document.blocks.size} size=${file.length()}b " +
                                "decode=${decodeMs}ms"
                        }
                        cached.document
                    }
                },
                onFailure = { e ->
                    cacheLogger.warn(e) { "PdfReflow: cache read failed path=${maskPath(path)}" }
                    file.delete()
                    null
                },
            )
        }

    override suspend fun write(
        path: String,
        document: ReflowDocument,
    ) {
        withContext(ioDispatcher) {
            val stat = stat(path)
            if (stat == null) {
                cacheLogger.info { "PdfReflow: cache write skipped (stat-fail) path=${maskPath(path)}" }
                return@withContext
            }
            val file = File(dir, fileNameFor(path))
            val tmp = File(dir, file.name + ".tmp")
            val mark = TimeSource.Monotonic.markNow()
            val outcome =
                runCatching {
                    tmp.outputStream().use { ReflowBinaryFormat.write(document, stat.size, stat.mtime, it) }
                    if (!tmp.renameTo(file)) {
                        tmp.delete()
                        throw IOException("rename failed")
                    }
                }
            outcome.fold(
                onSuccess = {
                    cacheLogger.info {
                        "PdfReflow: cache write path=${maskPath(path)} " +
                            "blocks=${document.blocks.size} size=${file.length()}b " +
                            "took=${mark.elapsedNow().inWholeMilliseconds}ms"
                    }
                },
                onFailure = { e ->
                    cacheLogger.warn(e) { "PdfReflow: cache write failed path=${maskPath(path)}" }
                    tmp.delete()
                },
            )
        }
    }

    private fun fileNameFor(path: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(path.toByteArray(Charsets.UTF_8))
        return hexEncode(hash).take(CACHE_KEY_HEX_CHARS) + CACHE_FILE_SUFFIX
    }

    private fun hexEncode(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(HEX_DIGITS[(b.toInt() ushr 4) and 0xF])
            sb.append(HEX_DIGITS[b.toInt() and 0xF])
        }
        return sb.toString()
    }

    private fun maskPath(path: String): String = path.substringAfterLast('/')
}
