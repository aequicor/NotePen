package ru.kyamshanov.notepen.epub

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.kyamshanov.notepen.pdf.infrastructure.isEpubPath
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * JVM-реализация [EpubToPdfConverter]: читает файл EPUB, парсит его
 * [EpubParser] и верстает в PDF через [JvmEpubPdfRenderer]. Результат кешируется
 * в системном временном каталоге; ключ кеша учитывает путь, размер и mtime,
 * поэтому изменённый файл переконвертируется автоматически.
 *
 * @param ioDispatcher диспетчер для блокирующего IO/CPU; не должен быть Main
 */
class JvmEpubToPdfConverter(private val ioDispatcher: CoroutineDispatcher) : EpubToPdfConverter {

    private val cacheDir = File(System.getProperty("java.io.tmpdir"), "notepen-epub")
    private val mutex = Mutex()

    override fun isEpub(path: String): Boolean = isEpubPath(path)

    override suspend fun ensurePdf(path: String): String = withContext(ioDispatcher) {
        val source = File(path)
        require(source.exists() && source.canRead()) { "EPUB file not found: $path" }

        val target = cacheFileFor(path, source.length(), source.lastModified())
        if (target.exists() && target.length() > 0L) return@withContext target.absolutePath

        mutex.withLock {
            if (target.exists() && target.length() > 0L) return@withLock target.absolutePath
            cacheDir.mkdirs()
            val book = EpubParser.parse(source.readBytes())
            val tmp = File.createTempFile("epub", ".pdf.tmp", cacheDir)
            try {
                JvmEpubPdfRenderer.render(book, tmp)
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } finally {
                tmp.delete()
            }
            target.absolutePath
        }
    }

    private fun cacheFileFor(path: String, size: Long, modified: Long): File {
        val digest = MessageDigest.getInstance("MD5")
            .digest("$path|$size|$modified".toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
        return File(cacheDir, "epub_$digest.pdf")
    }
}
