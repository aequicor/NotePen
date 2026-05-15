package ru.kyamshanov.notepen

import kotlinx.coroutines.Dispatchers
import ru.kyamshanov.notepen.annotation.domain.port.PdfExporter
import ru.kyamshanov.notepen.pdf.infrastructure.JvmPdfExporter

actual fun createPdfExporter(): PdfExporter = JvmPdfExporter(Dispatchers.IO)
