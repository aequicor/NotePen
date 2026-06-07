package ru.kyamshanov.notepen.reflow.ui.bookcurl

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal actual suspend fun captureReflowTexture(
    widthPx: Int,
    heightPx: Int,
    density: Density,
    content: @Composable () -> Unit,
): ImageBitmap? {
    if (widthPx <= 0 || heightPx <= 0) return null
    // Закадровый рендер не на main: ImageComposeScene держит собственную сцену/часы и
    // безопасно работает на отдельном потоке (как в jvmTest), не дёргая кадры ридера.
    return withContext(Dispatchers.Default) {
        runCatching {
            val scene = ImageComposeScene(width = widthPx, height = heightPx, density = density, content = content)
            try {
                scene.render().toComposeImageBitmap()
            } finally {
                scene.close()
            }
        }.getOrNull()
    }
}
