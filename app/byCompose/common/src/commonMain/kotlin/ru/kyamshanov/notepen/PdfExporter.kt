package ru.kyamshanov.notepen

import ru.kyamshanov.notepen.annotation.domain.port.PdfExporter

expect fun createPdfExporter(): PdfExporter
