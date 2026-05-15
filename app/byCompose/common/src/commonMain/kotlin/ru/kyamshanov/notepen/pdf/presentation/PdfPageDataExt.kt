package ru.kyamshanov.notepen.pdf.presentation

import androidx.compose.ui.graphics.ImageBitmap
import ru.kyamshanov.notepen.pdf.domain.model.PdfPageData

/** Конвертирует пиксельный буфер страницы в [ImageBitmap] для отображения в Compose. */
expect fun PdfPageData.toImageBitmap(): ImageBitmap
