package ru.kyamshanov.notepen.mainscreen.infrastructure

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.kyamshanov.notepen.mainscreen.domain.model.AvailabilityStatus
import ru.kyamshanov.notepen.mainscreen.domain.port.FileAvailabilityChecker

/**
 * Android-реализация [FileAvailabilityChecker].
 *
 * Проверяет доступность файла через [android.content.ContentResolver].
 * SecurityException (CC-12: истёкшее SAF-разрешение) → FILE_ERROR.
 * FileNotFoundException → NOT_FOUND.
 * Прочие ошибки → FILE_ERROR.
 */
class FileAvailabilityCheckerAndroid(
    private val context: Context,
) : FileAvailabilityChecker {
    override suspend fun check(uri: String): AvailabilityStatus =
        withContext(Dispatchers.IO) {
            checkSync(uri)
        }

    override fun checkSync(uri: String): AvailabilityStatus =
        try {
            context.contentResolver.openInputStream(Uri.parse(uri))?.use {
                AvailabilityStatus.AVAILABLE
            } ?: AvailabilityStatus.FILE_ERROR
        } catch (_: SecurityException) {
            AvailabilityStatus.FILE_ERROR
        } catch (_: java.io.FileNotFoundException) {
            AvailabilityStatus.NOT_FOUND
        } catch (_: Exception) {
            AvailabilityStatus.FILE_ERROR
        }
}
