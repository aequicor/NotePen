package ru.kyamshanov.notepen

import kotlinx.coroutines.Dispatchers
import ru.kyamshanov.notepen.epub.EpubAwarePdfReflowExtractor
import ru.kyamshanov.notepen.epub.JvmEpubToPdfConverter
import ru.kyamshanov.notepen.reflow.JvmPdfReflowExtractor
import ru.kyamshanov.notepen.reflow.api.PdfReflowExtractor

actual fun createPdfReflowExtractor(): PdfReflowExtractor =
    EpubAwarePdfReflowExtractor(JvmPdfReflowExtractor(Dispatchers.IO), JvmEpubToPdfConverter(Dispatchers.IO))
