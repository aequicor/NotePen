package ru.kyamshanov.notepen.mainscreen.infrastructure

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.kyamshanov.notepen.mainscreen.domain.model.AvailabilityStatus
import ru.kyamshanov.notepen.mainscreen.domain.port.FileAvailabilityChecker

/**
 * Desktop (JVM)-реализация [FileAvailabilityChecker].
 *
 * Проверяет доступность файла через [java.io.File] API.
 * Несуществующий файл → NOT_FOUND (TC-28).
 * Нечитаемый файл или ошибка → FILE_ERROR.
 */
class FileAvailabilityCheckerDesktop : FileAvailabilityChecker {

    override suspend fun check(uri: String): AvailabilityStatus =
        withContext(Dispatchers.IO) { checkSync(uri) }

    override fun checkSync(uri: String): AvailabilityStatus {
        val file = try {
            java.io.File(uri).canonicalFile
        } catch (_: Exception) {
            return AvailabilityStatus.FILE_ERROR
        }
        return when {
            !file.exists() -> AvailabilityStatus.NOT_FOUND
            !file.canRead() -> AvailabilityStatus.FILE_ERROR
            else -> AvailabilityStatus.AVAILABLE
        }
    }
}
