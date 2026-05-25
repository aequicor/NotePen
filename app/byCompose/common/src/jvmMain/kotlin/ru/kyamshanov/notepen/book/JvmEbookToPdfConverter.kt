package ru.kyamshanov.notepen.book

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * JVM-реализация [EbookToPdfConverter]: определяет формат книги
 * ([detectBookFormat]), парсит её в [BookContent] ([parseBook]) и верстает в PDF
 * через [JvmBookPdfRenderer]. Результат кешируется в системном временном
 * каталоге; ключ кеша учитывает путь, размер и mtime, поэтому изменённый файл
 * переконвертируется автоматически.
 *
 * @param ioDispatcher диспетчер для блокирующего IO/CPU; не должен быть Main
 */
class JvmEbookToPdfConverter(
    private val ioDispatcher: CoroutineDispatcher,
) : EbookToPdfConverter,
    DocumentOutlineProvider {
    private val cacheDir = File(System.getProperty("java.io.tmpdir"), "notepen-books")
    private val mutex = Mutex()

    override fun canConvert(path: String): Boolean = detectBookFormat(path) != null

    override suspend fun ensurePdf(path: String): String =
        withContext(ioDispatcher) {
            val source = File(path)
            require(source.exists() && source.canRead()) { "Book file not found: $path" }
            val format = requireNotNull(detectBookFormat(path)) { "Unsupported book format: $path" }

            val target = cacheFileFor(path, source.length(), source.lastModified())
            if (target.exists() && target.length() > 0L) return@withContext target.absolutePath

            mutex.withLock {
                if (target.exists() && target.length() > 0L) return@withLock target.absolutePath
                cacheDir.mkdirs()
                val tmp = File.createTempFile("book", ".pdf.tmp", cacheDir)
                try {
                    val outline =
                        when (val parsed = readBookSource(source.readBytes(), format)) {
                            is BookSource.Text -> JvmBookPdfRenderer.render(parsed.content, tmp)
                            is BookSource.Comic -> {
                                JvmComicPdfRenderer.render(parsed.images, tmp)
                                emptyList<TocEntry>()
                            }
                        }
                    Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    OutlineSidecar.write(target.absolutePath, outline)
                } finally {
                    tmp.delete()
                }
                target.absolutePath
            }
        }

    override suspend fun outlineFor(path: String): List<TocEntry> {
        if (!canConvert(path)) return emptyList()
        val pdfPath = ensurePdf(path)
        return withContext(ioDispatcher) { OutlineSidecar.read(pdfPath) }
    }

    private fun cacheFileFor(
        path: String,
        size: Long,
        modified: Long,
    ): File {
        val digest =
            MessageDigest
                .getInstance("MD5")
                .digest("$path|$size|$modified".toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte) }
        return File(cacheDir, "book_$digest.pdf")
    }
}
