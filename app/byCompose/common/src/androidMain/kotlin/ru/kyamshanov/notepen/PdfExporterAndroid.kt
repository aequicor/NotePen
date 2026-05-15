package ru.kyamshanov.notepen

import kotlinx.coroutines.Dispatchers
import ru.kyamshanov.notepen.annotation.domain.port.PdfExporter
import ru.kyamshanov.notepen.pdf.infrastructure.AndroidPdfExporter

actual fun createPdfExporter(): PdfExporter = AndroidPdfExporter(Dispatchers.IO)
