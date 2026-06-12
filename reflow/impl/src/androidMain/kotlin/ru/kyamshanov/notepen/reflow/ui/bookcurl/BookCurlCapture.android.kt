package ru.kyamshanov.notepen.reflow.ui.bookcurl

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Density

// На Android нет ImageComposeScene (закадровый рендер @Composable в bitmap). Возвращаем null
// НАМЕРЕННО: это сигнал ReflowReader снимать кёрл-текстуру со страничного GraphicsLayer через
// snapshotBookCurlLayer (софтверная отрисовка записанного слоя, см.
// BookCurlLayerSnapshot.android.kt). GraphicsLayer.toImageBitmap() для этого НЕ годится: его
// вторичный HardwareRenderer на части устройств отдаёт пустой кадр — лист без текста.
@Suppress("UNUSED_PARAMETER")
internal actual suspend fun captureReflowTexture(
    widthPx: Int,
    heightPx: Int,
    density: Density,
    content: @Composable () -> Unit,
): ImageBitmap? = null
