package ru.kyamshanov.notepen.pdf.presentation

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import ru.kyamshanov.notepen.pdf.domain.model.PdfPageData

actual fun PdfPageData.toImageBitmap(): ImageBitmap {
    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, widthPx, 0, 0, widthPx, heightPx)
    return bitmap.asImageBitmap()
}
