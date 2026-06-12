package ru.kyamshanov.notepen.reflow.ui.bookcurl

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.toPixelMap

// На Desktop эталонные текстуры кёрла приходят из captureReflowTexture (ImageComposeScene:
// фон, sRGB-тег, чистое состояние выделения); слой — страховка для тапа, опередившего
// 32мс-префетч. После починки captureToLayer (scoped record) слой на Desktop содержит
// реальный контент, но БЕЗ бумаги (её рисует родитель пейджера) — без подкладки фона тап
// кэшировал бы полупрозрачный лист (изнанка просвечивала бы под-страницей). Пустой кадр
// (headless-тесты) отбраковываем в null, иначе непрозрачная бумага превратила бы его в
// «валидную» текстуру и отравила кэш до конца сессии.
internal actual suspend fun snapshotBookCurlLayer(
    layer: GraphicsLayer,
    drawBackground: DrawScope.() -> Unit,
): ImageBitmap? {
    val size = layer.size
    if (size.width <= 0 || size.height <= 0) return null
    return runCatching { layer.toImageBitmap() }
        .getOrNull()
        ?.takeIf { it.hasInkContent() }
        ?.let { content -> compositeOverBackground(content, drawBackground) }
}

private fun compositeOverBackground(
    content: ImageBitmap,
    drawBackground: DrawScope.() -> Unit,
): ImageBitmap =
    renderToImageBitmap(content.width, content.height) {
        drawBackground()
        drawImage(content)
    }

private fun ImageBitmap.hasInkContent(): Boolean {
    val buffer = toPixelMap().buffer
    for (pixel in buffer) {
        if (pixel != 0) return true
    }
    return false
}
