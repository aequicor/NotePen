package ru.kyamshanov.notepen

import kotlinx.coroutines.Dispatchers
import ru.kyamshanov.notepen.annotation.domain.port.PdfExporter
import ru.kyamshanov.notepen.book.EbookAwarePdfExporter
import ru.kyamshanov.notepen.book.JvmEbookToPdfConverter
import ru.kyamshanov.notepen.pdf.infrastructure.JvmPdfExporter

actual fun createPdfExporter(): PdfExporter = EbookAwarePdfExporter(JvmPdfExporter(Dispatchers.IO), JvmEbookToPdfConverter(Dispatchers.IO))
