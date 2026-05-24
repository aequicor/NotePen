package ru.kyamshanov.notepen.epub

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.kyamshanov.notepen.pdf.infrastructure.isEpubMime
import ru.kyamshanov.notepen.pdf.infrastructure.isEpubPath
import java.io.File
import java.security.MessageDigest

/**
 * Android-реализация [EpubToPdfConverter]: читает EPUB из файла или
 * `content://` URI, парсит его [EpubParser] и верстает в PDF через
 * [AndroidEpubPdfRenderer]. Результат кешируется в `cacheDir`; ключ кеша
 * учитывает источник и его размер, поэтому изменённый файл переконвертируется.
 *
 * @param context контекст для доступа к ContentResolver и `cacheDir`
 * @param ioDispatcher диспетчер для блокирующего IO/CPU; не должен быть Main
 */
class AndroidEpubToPdfConverter(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
) : EpubToPdfConverter {

    private val cacheDir = File(context.cacheDir, "notepen-epub")
    private val mutex = Mutex()

    override fun isEpub(path: String): Boolean {
        val mime = runCatching { context.contentResolver.getType(Uri.parse(path)) }.getOrNull()
        if (mime != null) return isEpubMime(mime)
        return isEpubPath(path)
    }

    override suspend fun ensurePdf(path: String): String = withContext(ioDispatcher) {
        val uri = Uri.parse(path)
        val target = cacheFileFor(path, sourceSize(uri))
        if (target.exists() && target.length() > 0L) return@withContext target.absolutePath

        mutex.withLock {
            if (target.exists() && target.length() > 0L) return@withLock target.absolutePath
            cacheDir.mkdirs()
            val bytes = readBytes(uri) ?: throw IllegalArgumentException("Cannot read EPUB: $path")
            val book = EpubParser.parse(bytes)
            val tmp = File.createTempFile("epub", ".pdf.tmp", cacheDir)
            try {
                AndroidEpubPdfRenderer.render(book, tmp)
                if (!tmp.renameTo(target)) tmp.copyTo(target, overwrite = true)
            } finally {
                tmp.delete()
            }
            target.absolutePath
        }
    }

    private fun readBytes(uri: Uri): ByteArray? = when (uri.scheme) {
        null, "file" -> uri.path?.let { File(it) }?.takeIf { it.canRead() }?.readBytes()
        else -> context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }

    private fun sourceSize(uri: Uri): Long = when (uri.scheme) {
        null, "file" -> uri.path?.let { File(it).length() } ?: 0L
        else -> queryContentSize(uri)
    }

    private fun queryContentSize(uri: Uri): Long =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else 0L
                } else {
                    0L
                }
            } ?: 0L
        }.getOrDefault(0L)

    private fun cacheFileFor(path: String, size: Long): File {
        val digest = MessageDigest.getInstance("MD5")
            .digest("$path|$size".toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
        return File(cacheDir, "epub_$digest.pdf")
    }
}
