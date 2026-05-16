package ru.kyamshanov.notepen.pdfviewer

/**
 * Текущая платформа выполнения. Используется в [DetailsContent] для
 * выбора между новым [PdfDesktopPagesViewer] и старой LazyColumn-веткой
 * на Android.
 */
expect val isJvmDesktop: Boolean
