package ru.kyamshanov.notepen.mainscreen.infrastructure

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.kyamshanov.notepen.mainscreen.domain.exception.ThumbnailGenerationException
import ru.kyamshanov.notepen.mainscreen.domain.port.PdfThumbnailGenerator

/**
 * Android-реализация [PdfThumbnailGenerator].
 *
 * Использует [PdfRenderer] (API 21+, minSdk=24 — безопасно).
 * Рендерит первую страницу PDF в ARGB_8888 Bitmap, сжимает в PNG, возвращает ByteArray.
 * Все исключения (включая OOM — CC-11) оборачиваются в [ThumbnailGenerationException].
 */
class PdfThumbnailGeneratorAndroid(
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
                val descriptor =
                    context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")
                        ?: throw ThumbnailGenerationException("Cannot open file descriptor for uri")
                descriptor.use { fd ->
                    PdfRenderer(fd).use { renderer ->
                        if (renderer.pageCount == 0) throw ThumbnailGenerationException("Empty PDF: no pages")
                        renderer.openPage(0).use { page ->
                            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                            try {
                                page.render(
                                    bitmap,
                                    null,
                                    null,
                                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                                )
                                val stream = java.io.ByteArrayOutputStream()
                                bitmap.compress(Bitmap.CompressFormat.PNG, 85, stream)
                                stream.toByteArray()
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                }
            }.mapFailure { cause ->
                ThumbnailGenerationException("Thumbnail generation failed", cause)
            }
        }
}

private fun <T> Result<T>.mapFailure(transform: (Throwable) -> Throwable): Result<T> =
    exceptionOrNull()?.let { Result.failure(transform(it)) } ?: this
