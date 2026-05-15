package ru.kyamshanov.notepen.mainscreen.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image as SkiaImage

/**
 * Desktop (JVM)-реализация [rememberPdfThumbnailPainter].
 *
 * Skia декодирует PNG синхронно и нативно — быстро даже на главном потоке.
 * Асинхронный produceState не используется: на Desktop обновление состояния
 * из фоновой корутины не всегда триггерит рекомпозицию после навигации.
 */
@Composable
actual fun rememberPdfThumbnailPainter(imageData: ByteArray): Painter? =
    remember(imageData) {
        runCatching {
            SkiaImage.makeFromEncoded(imageData).toComposeImageBitmap()
        }.getOrNull()?.let { BitmapPainter(it) }
    }
