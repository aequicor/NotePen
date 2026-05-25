package ru.kyamshanov.notepen.mainscreen.domain.port

import ru.kyamshanov.notepen.mainscreen.domain.model.AvailabilityStatus

/**
 * Порт для проверки доступности файла.
 * Декларируется в `:shared`. Реализуется в инфраструктурном слое.
 */
interface FileAvailabilityChecker {
    /**
     * Проверяет доступность файла по URI асинхронно.
     *
     * Android: проверяет URI через ContentResolver.
     * Desktop: проверяет существование и читаемость файла по canonical path.
     *
     * @return [AvailabilityStatus] — никогда не возвращает [AvailabilityStatus.UNKNOWN].
     */
    suspend fun check(uri: String): AvailabilityStatus

    /**
     * Синхронная проверка доступности файла непосредственно перед открытием (CC-19).
     *
     * CRITICAL — диспетчер: метод НЕ является `suspend fun` намеренно, но
     * ДОЛЖЕН вызываться исключительно с `Dispatchers.IO` (через `withContext`
     * в вызывающем UseCase). Вызов с UI-диспетчера запрещён (блокировка главного потока).
     *
     * Таймаут: реализация ДОЛЖНА вернуть результат не позднее чем через 2 секунды.
     * При превышении — возвращает [AvailabilityStatus.FILE_ERROR].
     *
     * @return [AvailabilityStatus] — никогда не возвращает [AvailabilityStatus.UNKNOWN].
     */
    fun checkSync(uri: String): AvailabilityStatus
}
