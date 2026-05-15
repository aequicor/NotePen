package ru.kyamshanov.notepen

actual fun PdfManager(path: String): PdfManager = AndroidPdfManager(path)
