package ru.kyamshanov.notepen.pdf.presentation

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import ru.kyamshanov.notepen.pdf.domain.model.PdfPageData
import ru.kyamshanov.notepen.prewarmNativeImage
import java.awt.image.BufferedImage

actual fun PdfPageData.toImageBitmap(): ImageBitmap {
    findJvmBufferedImage()?.let { return it.toComposeImageBitmap().also(::prewarmNativeImage) }

    val image = BufferedImage(widthPx, heightPx, BufferedImage.TYPE_INT_ARGB)
    image.setRGB(0, 0, widthPx, heightPx, pixels, 0, widthPx)
    return image.toComposeImageBitmap().also(::prewarmNativeImage)
}
