package ru.kyamshanov.notepen

import android.net.Uri
import android.provider.OpenableColumns

/**
 * For `content://` URIs queries the [OpenableColumns.DISPLAY_NAME] via the
 * [android.content.ContentResolver]; otherwise (and on any failure) falls back
 * to the path basename.
 */
actual fun resolveDocumentDisplayName(filePathOrUri: String): String? {
    if (filePathOrUri.startsWith("content://")) {
        runCatching {
            AppContextHolder.context.contentResolver.query(
                Uri.parse(filePathOrUri),
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        cursor.getString(index)?.takeIf { it.isNotBlank() }?.let { return it }
                    }
                }
            }
        }
    }
    return filePathOrUri
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .ifBlank { null }
}
