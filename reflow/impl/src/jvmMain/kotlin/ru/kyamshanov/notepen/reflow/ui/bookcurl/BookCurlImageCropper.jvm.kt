package ru.kyamshanov.notepen.reflow.ui.bookcurl

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection

internal actual fun cropBookCurlImage(
    image: ImageBitmap,
    sourceX: Int,
    width: Int,
): ImageBitmap {
    val safeX = sourceX.coerceIn(0, image.width - 1)
    val safeWidth = width.coerceIn(1, image.width - safeX)
    val result = ImageBitmap(safeWidth, image.height)
    CanvasDrawScope().draw(
        density = Density(1f),
        layoutDirection = LayoutDirection.Ltr,
        canvas = Canvas(result),
        size = Size(safeWidth.toFloat(), image.height.toFloat()),
    ) {
        drawImage(
            image = image,
            srcOffset = IntOffset(safeX, 0),
            srcSize = IntSize(safeWidth, image.height),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(safeWidth, image.height),
        )
    }
    return result
}

internal actual fun mirrorBookCurlImageHorizontally(image: ImageBitmap): ImageBitmap {
    val result = ImageBitmap(image.width, image.height)
    CanvasDrawScope().draw(
        density = Density(1f),
        layoutDirection = LayoutDirection.Ltr,
        canvas = Canvas(result),
        size = Size(image.width.toFloat(), image.height.toFloat()),
    ) {
        scale(scaleX = -1f, scaleY = 1f) {
            drawImage(
                image = image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(image.width, image.height),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(image.width, image.height),
            )
        }
    }
    return result
}
