package ru.kyamshanov.notepen

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

internal actual class NativeImageDrawCache actual constructor() {
    actual fun close() = Unit
}

internal actual fun prewarmNativeImage(image: ImageBitmap) = Unit

internal actual fun DrawScope.drawNativeCachedImage(
    cache: NativeImageDrawCache,
    image: ImageBitmap,
    srcOffset: IntOffset,
    srcSize: IntSize,
    dstOffset: IntOffset,
    dstSize: IntSize,
    blendMode: BlendMode,
    filterQuality: FilterQuality,
) {
    drawImage(
        image = image,
        srcOffset = srcOffset,
        srcSize = srcSize,
        dstOffset = dstOffset,
        dstSize = dstSize,
        blendMode = blendMode,
        filterQuality = filterQuality,
    )
}
