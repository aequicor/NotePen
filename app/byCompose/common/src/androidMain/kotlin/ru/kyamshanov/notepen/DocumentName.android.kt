package ru.kyamshanov.notepen

import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

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

/**
 * For `content://` URIs queries [OpenableColumns.SIZE] via the
 * [android.content.ContentResolver]; for plain file paths/`file://` URIs reads
 * the filesystem length. Returns `null` when the size cannot be determined.
 */
actual fun resolveDocumentSize(filePathOrUri: String): Long? {
    if (filePathOrUri.startsWith("content://")) {
        return runCatching {
            AppContextHolder.context.contentResolver.query(
                Uri.parse(filePathOrUri),
                arrayOf(OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else null
                } else {
                    null
                }
            }
        }.getOrNull()
    }
    val path = if (filePathOrUri.startsWith("file://")) Uri.parse(filePathOrUri).path else filePathOrUri
    return path?.let { File(it) }?.takeIf { it.isFile }?.length()
}
