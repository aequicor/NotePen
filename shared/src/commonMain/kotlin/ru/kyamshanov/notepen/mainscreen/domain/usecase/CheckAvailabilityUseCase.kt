package ru.kyamshanov.notepen.mainscreen.domain.usecase

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import ru.kyamshanov.notepen.mainscreen.domain.model.AvailabilityStatus
import ru.kyamshanov.notepen.mainscreen.domain.model.RecentFile
import ru.kyamshanov.notepen.mainscreen.domain.port.FileAvailabilityChecker
import ru.kyamshanov.notepen.mainscreen.domain.port.FileHistoryRepository

private val logger = KotlinLogging.logger {}

/** Результат проверки доступности одного файла. */
data class AvailabilityUpdate(val id: String, val status: AvailabilityStatus)

/**
 * Параллельно проверяет доступность всех записей истории.
 *
 * Максимальный параллелизм: 5 (Semaphore). Таймаут на одну проверку: 2 секунды (AC-9a).
 *
 * @param checker Порт проверки доступности файла.
 * @param repository Порт для обновления статуса.
 */
class CheckAvailabilityUseCase(
    private val checker: FileAvailabilityChecker,
    private val repository: FileHistoryRepository,
) {
    /**
     * Запускает параллельную проверку доступности файлов.
     *
     * @param files Список записей истории для проверки.
     * @return Flow с [AvailabilityUpdate] по одному на каждый файл.
     */
    fun execute(files: List<RecentFile>): Flow<AvailabilityUpdate> =
        channelFlow {
            val semaphore = Semaphore(5)
            files.forEach { file ->
                launch {
                    semaphore.withPermit {
                        val status =
                            try {
                                withTimeout(2_000L) { checker.check(file.uri) }
                            } catch (_: TimeoutCancellationException) {
                                AvailabilityStatus.FILE_ERROR
                            } catch (e: Exception) {
                                // Boundary catch: platform exceptions from FileAvailabilityChecker.check
                                // are not enumerable in commonMain contract; FILE_ERROR prevents UI crash (ADR-004).
                                logger.warn { "Availability check failed for file ${file.id}: ${e::class.simpleName}" }
                                AvailabilityStatus.FILE_ERROR
                            }
                        repository.updateStatus(file.id, status)
                        send(AvailabilityUpdate(file.id, status))
                    }
                }
            }
        }
}
