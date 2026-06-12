package ru.kyamshanov.notepen.reflow.ui.bookcurl

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * Рисует [block] в свежий [ImageBitmap] размера [width]×[height] закадрово
 * (Density 1, LTR). Общий идиом offscreen-рендера для текстур книжного кёрла:
 * кроп ([cropBookCurlImage]), зеркалирование ([mirrorBookCurlImageHorizontally])
 * и подкладка фона под снимок слоя ([snapshotBookCurlLayer]).
 */
internal fun renderToImageBitmap(
    width: Int,
    height: Int,
    block: DrawScope.() -> Unit,
): ImageBitmap {
    val image = ImageBitmap(width, height)
    CanvasDrawScope().draw(
        density = Density(1f),
        layoutDirection = LayoutDirection.Ltr,
        canvas = Canvas(image),
        size = Size(width.toFloat(), height.toFloat()),
        block = block,
    )
    return image
}
