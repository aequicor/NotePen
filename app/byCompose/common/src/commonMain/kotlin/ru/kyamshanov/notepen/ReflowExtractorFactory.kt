package ru.kyamshanov.notepen

import ru.kyamshanov.notepen.reflow.api.PdfReflowExtractor

/** Создаёт платформенный извлекатель reflow-содержимого (PDFBox / PdfBox-Android). */
expect fun createPdfReflowExtractor(): PdfReflowExtractor
