package ru.kyamshanov.notepen.mainscreen.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android-реализация [rememberPdfThumbnailPainter].
 *
 * Декодирует [imageData] через [android.graphics.BitmapFactory] на [Dispatchers.IO] и возвращает [BitmapPainter].
 */
@Composable
actual fun rememberPdfThumbnailPainter(imageData: ByteArray): Painter {
    val bitmap by produceState<ImageBitmap?>(null, imageData) {
        value = withContext(Dispatchers.IO) {
            android.graphics.BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
                ?.asImageBitmap()
        }
    }
    return bitmap?.let { BitmapPainter(it) } ?: BitmapPainter(ImageBitmap(1, 1))
}
