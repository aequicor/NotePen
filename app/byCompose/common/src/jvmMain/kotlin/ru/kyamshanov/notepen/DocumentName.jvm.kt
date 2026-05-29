package ru.kyamshanov.notepen

import java.io.File
import java.net.URI

/** Desktop paths are real filesystem paths — the basename is the display name. */
actual fun resolveDocumentDisplayName(filePathOrUri: String): String? =
    filePathOrUri
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .ifBlank { null }

/** Desktop paths are real filesystem paths — read the file length directly. */
actual fun resolveDocumentSize(filePathOrUri: String): Long? {
    val file =
        if (filePathOrUri.startsWith("file:")) {
            runCatching { File(URI(filePathOrUri)) }.getOrNull()
        } else {
            File(filePathOrUri)
        }
    return file?.takeIf { it.isFile }?.length()
}
