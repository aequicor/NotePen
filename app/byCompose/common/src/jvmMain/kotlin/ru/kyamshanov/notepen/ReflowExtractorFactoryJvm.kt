package ru.kyamshanov.notepen

import kotlinx.coroutines.Dispatchers
import ru.kyamshanov.notepen.book.EbookAwarePdfReflowExtractor
import ru.kyamshanov.notepen.book.JvmEbookToPdfConverter
import ru.kyamshanov.notepen.reflow.JvmPdfReflowExtractor
import ru.kyamshanov.notepen.reflow.api.PdfReflowExtractor

actual fun createPdfReflowExtractor(): PdfReflowExtractor {
    val converter = JvmEbookToPdfConverter(Dispatchers.IO)
    return EbookAwarePdfReflowExtractor(JvmPdfReflowExtractor(Dispatchers.IO), converter, converter)
}
