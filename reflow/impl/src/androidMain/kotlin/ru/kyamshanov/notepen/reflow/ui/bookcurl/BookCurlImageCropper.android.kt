package ru.kyamshanov.notepen.reflow.ui.bookcurl

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap

internal actual fun cropBookCurlImage(
    image: ImageBitmap,
    sourceX: Int,
    width: Int,
): ImageBitmap {
    val source = image.toSoftwareBookCurlBitmap()
    val safeX = sourceX.coerceIn(0, source.width - 1)
    val safeWidth = width.coerceIn(1, source.width - safeX)
    return Bitmap.createBitmap(source, safeX, 0, safeWidth, source.height).asImageBitmap()
}

internal actual fun mirrorBookCurlImageHorizontally(image: ImageBitmap): ImageBitmap {
    val source = image.toSoftwareBookCurlBitmap()
    val matrix = Matrix().apply { preScale(-1f, 1f) }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true).asImageBitmap()
}

internal fun ImageBitmap.toSoftwareBookCurlBitmap(): Bitmap = asAndroidBitmap().toSoftwareBookCurlBitmap()

private fun Bitmap.toSoftwareBookCurlBitmap(): Bitmap =
    if (config == Bitmap.Config.HARDWARE || config == null) {
        copy(Bitmap.Config.ARGB_8888, false)
    } else {
        this
    }
