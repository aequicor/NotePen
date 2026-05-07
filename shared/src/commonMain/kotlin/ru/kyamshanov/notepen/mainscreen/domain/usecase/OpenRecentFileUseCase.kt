package ru.kyamshanov.notepen.mainscreen.domain.usecase

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.kyamshanov.notepen.mainscreen.domain.model.AvailabilityStatus
import ru.kyamshanov.notepen.mainscreen.domain.port.FileAvailabilityChecker

/** Результат открытия файла из истории. */
sealed class OpenFileResult {
    /** Файл доступен — можно открыть. */
    data class Success(val uri: String) : OpenFileResult()

    /** Файл недоступен; поле [status] объясняет причину. */
    data class NotAvailable(val status: AvailabilityStatus) : OpenFileResult()
}

/**
 * Выполняет синхронную проверку доступности файла непосредственно перед открытием (CC-19).
 *
 * @param checker Порт проверки доступности.
 * @param ioDispatcher Диспетчер для блокирующего IO; по умолчанию [Dispatchers.IO] (ADR-003).
 *   В тестах подставляется [kotlinx.coroutines.test.UnconfinedTestDispatcher].
 */
class OpenRecentFileUseCase(
    private val checker: FileAvailabilityChecker,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /**
     * Проверяет доступность файла и возвращает результат.
     *
     * @param uri Нормализованный URI файла.
     * @return [OpenFileResult.Success] если файл доступен, иначе [OpenFileResult.NotAvailable].
     */
    suspend fun execute(uri: String): OpenFileResult {
        val status = try {
            withContext(ioDispatcher) { checker.checkSync(uri) }
        } catch (_: SecurityException) {
            AvailabilityStatus.FILE_ERROR
        } catch (e: Exception) {
            // Platform exceptions from checkSync are not enumerable in commonMain contract.
            // Boundary catch prevents UI crash — returns FILE_ERROR instead (ADR-004).
            AvailabilityStatus.FILE_ERROR
        }
        return when (status) {
            AvailabilityStatus.AVAILABLE -> OpenFileResult.Success(uri)
            else -> OpenFileResult.NotAvailable(status)
        }
    }
}
