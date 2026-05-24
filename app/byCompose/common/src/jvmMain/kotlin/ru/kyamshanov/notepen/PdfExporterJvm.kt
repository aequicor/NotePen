package ru.kyamshanov.notepen

import kotlinx.coroutines.Dispatchers
import ru.kyamshanov.notepen.annotation.domain.port.PdfExporter
import ru.kyamshanov.notepen.epub.EpubAwarePdfExporter
import ru.kyamshanov.notepen.epub.JvmEpubToPdfConverter
import ru.kyamshanov.notepen.pdf.infrastructure.JvmPdfExporter

actual fun createPdfExporter(): PdfExporter =
    EpubAwarePdfExporter(JvmPdfExporter(Dispatchers.IO), JvmEpubToPdfConverter(Dispatchers.IO))
