package ru.kyamshanov.notepen.reflow.ui.bookcurl

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope

internal expect fun isBookCurlNativeRendererSupported(): Boolean

internal expect fun DrawScope.drawBookCurlNative(
    front: ImageBitmap,
    back: ImageBitmap?,
    mesh: BookCurlMesh,
    buffers: BookCurlMeshBuffers,
    offsetX: Float,
    paint: BookCurlPaint,
)
