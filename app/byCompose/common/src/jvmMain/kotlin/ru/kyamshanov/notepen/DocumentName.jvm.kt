package ru.kyamshanov.notepen

/** Desktop paths are real filesystem paths — the basename is the display name. */
actual fun resolveDocumentDisplayName(filePathOrUri: String): String? =
    filePathOrUri
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .ifBlank { null }
