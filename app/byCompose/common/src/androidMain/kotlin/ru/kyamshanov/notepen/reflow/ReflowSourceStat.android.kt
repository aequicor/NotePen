package ru.kyamshanov.notepen.reflow

import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import ru.kyamshanov.notepen.AppContextHolder
import java.io.File

internal actual fun statReflowSource(path: String): SourceStat? {
    // Прямой файловый путь (sdcard/...) — быстрый случай через File API.
    val file = File(path)
    if (file.exists() && file.isFile) {
        return SourceStat(size = file.length(), mtime = file.lastModified())
    }
    // SAF-URI (content://..., document:...) — статим через ContentResolver.
    return statUri(path)
}

private fun statUri(path: String): SourceStat? {
    val uri = runCatching { Uri.parse(path) }.getOrNull() ?: return null
    val context = AppContextHolder.context
    var size = -1L
    var mtime = -1L
    runCatching {
        context.contentResolver
            .query(
                uri,
                arrayOf(OpenableColumns.SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
                    val mtimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    if (mtimeIdx >= 0 && !cursor.isNull(mtimeIdx)) mtime = cursor.getLong(mtimeIdx)
                }
            }
    }
    if (size < 0) return null
    // mtime может отсутствовать у некоторых SAF-провайдеров — считаем за 0; size один
    // — слабая защита от устаревания, но всё ещё лучше, чем переэкстракт каждый раз.
    return SourceStat(size = size, mtime = if (mtime >= 0) mtime else 0L)
}

internal actual fun reflowCacheDir(): String = File(AppContextHolder.context.cacheDir, "reflow-cache").absolutePath
