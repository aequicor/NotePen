package ru.kyamshanov.notepen

import kotlinx.coroutines.Dispatchers
import ru.kyamshanov.notepen.epub.AndroidEpubToPdfConverter
import ru.kyamshanov.notepen.epub.EpubAwarePdfReflowExtractor
import ru.kyamshanov.notepen.reflow.AndroidPdfReflowExtractor
import ru.kyamshanov.notepen.reflow.api.PdfReflowExtractor

actual fun createPdfReflowExtractor(): PdfReflowExtractor =
    EpubAwarePdfReflowExtractor(
        AndroidPdfReflowExtractor(AppContextHolder.context, Dispatchers.IO),
        AndroidEpubToPdfConverter(AppContextHolder.context, Dispatchers.IO),
    )
