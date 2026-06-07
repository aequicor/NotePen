package ru.kyamshanov.notepen.reflow.ui.bookcurl

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Density

// На Android нет ImageComposeScene. Закадровый рендер контента в bitmap требует отдельного
// пути (например, offscreen ComposeView + PixelCopy) — TODO. Пока кёрл-текстура только на Desktop.
@Suppress("UNUSED_PARAMETER")
internal actual suspend fun captureReflowTexture(
    widthPx: Int,
    heightPx: Int,
    density: Density,
    content: @Composable () -> Unit,
): ImageBitmap? = null
