package ru.kyamshanov.notepen

import kotlinx.coroutines.Dispatchers
import ru.kyamshanov.notepen.annotation.domain.port.PdfExporter
import ru.kyamshanov.notepen.book.AndroidEbookToPdfConverter
import ru.kyamshanov.notepen.book.EbookAwarePdfExporter
import ru.kyamshanov.notepen.pdf.infrastructure.AndroidPdfExporter

actual fun createPdfExporter(): PdfExporter =
    EbookAwarePdfExporter(
        AndroidPdfExporter(Dispatchers.IO),
        AndroidEbookToPdfConverter(AppContextHolder.context, Dispatchers.IO),
    )
