package ru.kyamshanov.notepen

import kotlinx.coroutines.Dispatchers
import ru.kyamshanov.notepen.annotation.domain.port.PdfExporter
import ru.kyamshanov.notepen.epub.AndroidEpubToPdfConverter
import ru.kyamshanov.notepen.epub.EpubAwarePdfExporter
import ru.kyamshanov.notepen.pdf.infrastructure.AndroidPdfExporter

actual fun createPdfExporter(): PdfExporter =
    EpubAwarePdfExporter(
        AndroidPdfExporter(Dispatchers.IO),
        AndroidEpubToPdfConverter(AppContextHolder.context, Dispatchers.IO),
    )
