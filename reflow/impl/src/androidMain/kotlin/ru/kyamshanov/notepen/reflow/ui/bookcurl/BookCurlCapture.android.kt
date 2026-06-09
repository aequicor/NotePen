package ru.kyamshanov.notepen.reflow.ui.bookcurl

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Density

// На Android нет ImageComposeScene (закадровый рендер @Composable в bitmap). Возвращаем null
// НАМЕРЕННО: это сигнал ReflowReader брать кёрл-текстуру со страничного GraphicsLayer через
// toImageBitmap() — на устройстве он растеризуется корректно (в отличие от Desktop, где
// toImageBitmap пуст и нужен ImageComposeScene). Если on-device путь окажется ненадёжным —
// заменить на честный offscreen-рендер (ComposeView + PixelCopy).
@Suppress("UNUSED_PARAMETER")
internal actual suspend fun captureReflowTexture(
    widthPx: Int,
    heightPx: Int,
    density: Density,
    content: @Composable () -> Unit,
): ImageBitmap? = null
