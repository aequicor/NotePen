package ru.kyamshanov.notepen.mainscreen.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

/**
 * Возвращает [Painter] для отображения миниатюры PDF из массива байт.
 *
 * Реализации предоставляются в `androidMain` и `jvmMain`.
 *
 * @param imageData Закодированные данные изображения.
 */
@Composable
expect fun rememberPdfThumbnailPainter(imageData: ByteArray): Painter
