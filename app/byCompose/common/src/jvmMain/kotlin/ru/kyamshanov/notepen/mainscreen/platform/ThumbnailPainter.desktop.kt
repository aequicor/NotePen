package ru.kyamshanov.notepen.mainscreen.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Desktop (JVM)-реализация [rememberPdfThumbnailPainter].
 *
 * Декодирует [imageData] через [javax.imageio.ImageIO] на [Dispatchers.IO].
 * Возвращает null пока декодирование не завершено (DEF-003).
 */
@Composable
actual fun rememberPdfThumbnailPainter(imageData: ByteArray): Painter? {
    val bitmap by produceState<ImageBitmap?>(null, imageData) {
        value = withContext(Dispatchers.IO) {
            javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(imageData))
                ?.toComposeImageBitmap()
        }
    }
    return bitmap?.let { BitmapPainter(it) }
}
