package ru.kyamshanov.notepen.pdf.infrastructure

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import ru.kyamshanov.notepen.pdf.domain.model.PdfDocument
import ru.kyamshanov.notepen.pdf.domain.model.PdfDocumentInfo
import ru.kyamshanov.notepen.pdf.domain.model.PdfPageInfo
import ru.kyamshanov.notepen.pdf.domain.port.PdfDocumentLoader
import java.io.File
import java.io.InputStream

/**
 * Android-реализация [PdfDocumentLoader] для растровых изображений (PNG/JPEG).
 *
 * Принимает те же форматы `path`, что и [AndroidPdfDocumentLoader]: абсолютный путь,
 * `file://…` и `content://…` (SAF). Декодирует в ARGB-буфер и представляет как документ
 * из одной страницы.
 *
 * @param context контекст приложения для доступа к ContentResolver
 * @param ioDispatcher диспетчер для блокирующего IO; не должен быть Main-диспетчером
 */
class AndroidImageDocumentLoader(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
) : PdfDocumentLoader {
    override suspend fun load(path: String): PdfDocument =
        withContext(ioDispatcher) {
            val bitmap =
                openStream(path).use { stream ->
                    BitmapFactory.decodeStream(stream)
                        ?: throw IllegalArgumentException("Unsupported or corrupt image: $path")
                }

            val width = bitmap.width
            val height = bitmap.height
            val argb =
                if (bitmap.config == Bitmap.Config.ARGB_8888) {
                    bitmap
                } else {
                    val converted =
                        requireNotNull(bitmap.copy(Bitmap.Config.ARGB_8888, false)) {
                            "Failed to convert image to ARGB_8888: $path"
                        }
                    bitmap.recycle()
                    converted
                }
            val pixels = IntArray(width * height)
            argb.getPixels(pixels, 0, width, 0, 0, width, height)
            argb.recycle()

            val pageInfo =
                PdfPageInfo(
                    pageIndex = 0,
                    widthPt = width.toFloat(),
                    heightPt = height.toFloat(),
                    rotation = 0,
                )
            AndroidImageDocument(
                widthPx = width,
                heightPx = height,
                pixels = pixels,
                info = PdfDocumentInfo(pageCount = 1, pages = listOf(pageInfo)),
            )
        }

    private fun openStream(path: String): InputStream {
        val uri = Uri.parse(path)
        return when (uri.scheme) {
            null, "file" -> {
                val file = File(uri.path ?: path)
                if (!file.exists() || !file.canRead()) {
                    throw java.io.FileNotFoundException("Image file not found or not readable: $path")
                }
                file.inputStream()
            }
            else ->
                context.contentResolver.openInputStream(uri)
                    ?: throw IllegalArgumentException("Cannot open input stream for: $path")
        }
    }
}
