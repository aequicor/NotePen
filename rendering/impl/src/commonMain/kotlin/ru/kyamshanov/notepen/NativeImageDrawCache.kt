package ru.kyamshanov.notepen

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

internal expect class NativeImageDrawCache() {
    fun close()
}

internal expect fun prewarmNativeImage(image: ImageBitmap)

internal expect fun DrawScope.drawNativeCachedImage(
    cache: NativeImageDrawCache,
    image: ImageBitmap,
    srcOffset: IntOffset,
    srcSize: IntSize,
    dstOffset: IntOffset,
    dstSize: IntSize,
    blendMode: BlendMode,
    filterQuality: FilterQuality,
)
