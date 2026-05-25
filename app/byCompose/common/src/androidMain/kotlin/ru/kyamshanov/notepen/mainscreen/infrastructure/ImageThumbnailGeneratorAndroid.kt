package ru.kyamshanov.notepen.mainscreen.infrastructure

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.kyamshanov.notepen.mainscreen.domain.exception.ThumbnailGenerationException
import ru.kyamshanov.notepen.mainscreen.domain.port.PdfThumbnailGenerator
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Android-генератор миниатюр для растровых изображений (PNG/JPEG).
 *
 * Декодирует изображение, вписывает в [widthPx]×[heightPx] с сохранением пропорций и кодирует в PNG.
 * Все исключения (включая OOM) оборачиваются в [ThumbnailGenerationException].
 */
class ImageThumbnailGeneratorAndroid(
    private val context: Context,
) : PdfThumbnailGenerator {
    override suspend fun generate(
        uri: String,
        widthPx: Int,
        heightPx: Int,
    ): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(widthPx in 1..4096 && heightPx in 1..4096) {
                    "Thumbnail dimensions out of range: ${widthPx}x$heightPx"
                }
                val source =
                    openStream(uri).use { stream ->
                        BitmapFactory.decodeStream(stream)
                            ?: throw ThumbnailGenerationException("Unsupported or corrupt image: $uri")
                    }

                val scale =
                    minOf(
                        widthPx.toFloat() / source.width,
                        heightPx.toFloat() / source.height,
                    )
                val targetW = (source.width * scale).toInt().coerceAtLeast(1)
                val targetH = (source.height * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(source, targetW, targetH, true)
                if (scaled !== source) source.recycle()

                try {
                    val stream = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.PNG, 85, stream)
                    stream.toByteArray()
                } finally {
                    scaled.recycle()
                }
            }.mapFailure { cause ->
                ThumbnailGenerationException("Thumbnail generation failed", cause)
            }
        }

    private fun openStream(uri: String): java.io.InputStream {
        val parsed = Uri.parse(uri)
        return when (parsed.scheme) {
            null, "file" -> File(parsed.path ?: uri).inputStream()
            else ->
                context.contentResolver.openInputStream(parsed)
                    ?: throw ThumbnailGenerationException("Cannot open input stream for: $uri")
        }
    }
}

private fun <T> Result<T>.mapFailure(transform: (Throwable) -> Throwable): Result<T> =
    exceptionOrNull()?.let { Result.failure(transform(it)) } ?: this
