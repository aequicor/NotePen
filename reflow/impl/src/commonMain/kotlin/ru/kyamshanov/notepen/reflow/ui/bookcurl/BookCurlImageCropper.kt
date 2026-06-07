package ru.kyamshanov.notepen.reflow.ui.bookcurl

import androidx.compose.ui.graphics.ImageBitmap

internal expect fun cropBookCurlImage(
    image: ImageBitmap,
    sourceX: Int,
    width: Int,
): ImageBitmap

/** Зеркало изображения по горизонтали — для изнанки листа, которая после переворота читается верно. */
internal expect fun mirrorBookCurlImageHorizontally(image: ImageBitmap): ImageBitmap
