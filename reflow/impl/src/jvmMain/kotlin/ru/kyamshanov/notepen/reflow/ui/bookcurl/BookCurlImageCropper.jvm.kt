package ru.kyamshanov.notepen.reflow.ui.bookcurl

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

internal actual fun cropBookCurlImage(
    image: ImageBitmap,
    sourceX: Int,
    width: Int,
): ImageBitmap {
    val safeX = sourceX.coerceIn(0, image.width - 1)
    val safeWidth = width.coerceIn(1, image.width - safeX)
    return renderToImageBitmap(safeWidth, image.height) {
        drawImage(
            image = image,
            srcOffset = IntOffset(safeX, 0),
            srcSize = IntSize(safeWidth, image.height),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(safeWidth, image.height),
        )
    }
}

internal actual fun mirrorBookCurlImageHorizontally(image: ImageBitmap): ImageBitmap =
    renderToImageBitmap(image.width, image.height) {
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
