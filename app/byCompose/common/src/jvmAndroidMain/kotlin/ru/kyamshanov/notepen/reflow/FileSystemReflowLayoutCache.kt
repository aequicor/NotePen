package ru.kyamshanov.notepen.reflow

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.kyamshanov.notepen.reflow.ui.CachedLayout
import ru.kyamshanov.notepen.reflow.ui.LayoutCacheKey
import ru.kyamshanov.notepen.reflow.ui.ReflowLayoutCache
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.time.TimeSource

private val layoutCacheLogger = KotlinLogging.logger {}
private const val HEX_DIGITS = "0123456789abcdef"
private const val LAYOUT_FILE_SUFFIX = ".layout.bin"

/**
 * Дисковый кэш расчётной раскладки. Имя файла —
 * `sha256(typoFingerprint)[..24]__<docFingerprint hex>.layout.bin` (typo —
 * первым, docFingerprint — вторым; так одинаковая типографика для разных
 * документов группируется в директории по префиксу).
 *
 * Невалидный/повреждённый файл удаляется при чтении, чтобы следующее открытие
 * пересчитало и переписало. Запись — атомарная (через tmp + rename), ошибки
 * логируются и проглатываются.
 */
internal class FileSystemReflowLayoutCache(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    cacheDir: File? = null,
) : ReflowLayoutCache {
    private val dir: File by lazy { (cacheDir ?: File(reflowCacheDir())).also { it.mkdirs() } }

    override suspend fun read(key: LayoutCacheKey): CachedLayout? =
        withContext(ioDispatcher) {
            val file = File(dir, fileNameFor(key))
            if (!file.exists()) {
                layoutCacheLogger.info {
                    "PdfReflow: layout-cache miss key=${shortKey(key)}"
                }
                return@withContext null
            }
            val mark = TimeSource.Monotonic.markNow()
            runCatching {
                file.inputStream().use { ReflowLayoutBinaryFormat.read(it) }
            }.fold(
                onSuccess = { layout ->
                    layoutCacheLogger.info {
                        "PdfReflow: layout-cache hit key=${shortKey(key)} " +
                            "heights=${layout.textHeights.size} " +
                            "lineBottoms=${layout.textLineBottoms.size} " +
                            "size=${file.length()}b " +
                            "decode=${mark.elapsedNow().inWholeMilliseconds}ms"
                    }
                    layout
                },
                onFailure = { e ->
                    layoutCacheLogger.warn(e) { "PdfReflow: layout-cache read failed key=${shortKey(key)}" }
                    file.delete()
                    null
                },
            )
        }

    override suspend fun write(
        key: LayoutCacheKey,
        layout: CachedLayout,
    ) {
        withContext(ioDispatcher) {
            val file = File(dir, fileNameFor(key))
            val tmp = File(dir, file.name + ".tmp")
            val mark = TimeSource.Monotonic.markNow()
            runCatching {
                tmp.outputStream().use { ReflowLayoutBinaryFormat.write(layout, it) }
                if (!tmp.renameTo(file)) {
                    tmp.delete()
                    throw IOException("rename failed")
                }
            }.fold(
                onSuccess = {
                    layoutCacheLogger.info {
                        "PdfReflow: layout-cache write key=${shortKey(key)} " +
                            "heights=${layout.textHeights.size} " +
                            "lineBottoms=${layout.textLineBottoms.size} " +
                            "size=${file.length()}b took=${mark.elapsedNow().inWholeMilliseconds}ms"
                    }
                },
                onFailure = { e ->
                    layoutCacheLogger.warn(e) { "PdfReflow: layout-cache write failed key=${shortKey(key)}" }
                    tmp.delete()
                },
            )
        }
    }

    private fun fileNameFor(key: LayoutCacheKey): String {
        val typoString =
            buildString(128) {
                append(key.contentWidthPx).append('|')
                append(key.fontFamilyId).append('|')
                append(key.fontSizeSp).append('|')
                append(key.lineHeightMultiplier).append('|')
                append(key.letterSpacingSp).append('|')
                append(key.wordSpacingSp).append('|')
                append(key.hyphenation).append('|')
                append(key.align).append('|')
                append(key.bionic).append('|')
                append(key.columnChars).append('|')
                append(key.contentPaddingDp)
            }
        val digest = MessageDigest.getInstance("SHA-256")
        val typoHash = digest.digest(typoString.toByteArray(Charsets.UTF_8))
        val typoHex = hexEncode(typoHash).take(24)
        val docHex = key.docFingerprint.toULong().toString(16).padStart(16, '0')
        return "${typoHex}__$docHex$LAYOUT_FILE_SUFFIX"
    }

    private fun hexEncode(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(HEX_DIGITS[(b.toInt() ushr 4) and 0xF])
            sb.append(HEX_DIGITS[b.toInt() and 0xF])
        }
        return sb.toString()
    }

    private fun shortKey(key: LayoutCacheKey): String {
        val docHex = key.docFingerprint.toULong().toString(16).padStart(16, '0')
        return "doc=$docHex w=${key.contentWidthPx} f=${key.fontFamilyId}@${key.fontSizeSp}sp"
    }
}
