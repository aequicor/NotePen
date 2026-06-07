package ru.kyamshanov.notepen.reflow.ui.bookcurl

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Density

/**
 * Растеризует [content] заданного размера в [ImageBitmap] ВНЕ основной композиции —
 * текстура страницы для page-curl.
 *
 * `GraphicsLayer.toImageBitmap()` на Desktop отдаёт пустой кадр (контент рисуется только на
 * живой экранный GPU-canvas, но не растеризуется закадрово), поэтому страницу для загиба
 * перекомпонуем в отдельной сцене и честно рендерим. Возвращает null, если платформа не
 * поддерживает закадровый рендер (сейчас реализован Desktop через `ImageComposeScene`;
 * Android временно возвращает null — там нужен отдельный путь).
 */
internal expect suspend fun captureReflowTexture(
    widthPx: Int,
    heightPx: Int,
    density: Density,
    content: @Composable () -> Unit,
): ImageBitmap?
