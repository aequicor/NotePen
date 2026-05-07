package ru.kyamshanov.notepen.mainscreen.domain.port

import ru.kyamshanov.notepen.mainscreen.domain.exception.HistoryFlushException
import ru.kyamshanov.notepen.mainscreen.domain.model.AvailabilityStatus
import ru.kyamshanov.notepen.mainscreen.domain.model.RecentFile

/**
 * Порт для персистирования и чтения истории файлов.
 * Декларируется в `:shared`. Реализуется в инфраструктурном слое.
 *
 * Все операции изменения данных атомарны.
 */
interface FileHistoryRepository {

    /**
     * Возвращает все записи истории, отсортированные по openedAt DESC.
     * Никогда не бросает исключение — при ошибке возвращает пустой список.
     *
     * Таймаут: реализация ДОЛЖНА завершиться не позднее чем через 5 секунд.
     */
    suspend fun getAll(): List<RecentFile>

    /**
     * Добавляет или обновляет запись.
     *
     * Если запись с таким же нормализованным URI уже существует — перемещает её в начало
     * (обновляет openedAt). Иначе добавляет новую запись и применяет алгоритм вытеснения.
     *
     * @param file Доменная сущность RecentFile для сохранения.
     * @param lastPageIndex Индекс страницы (0-based). При первом добавлении — 0.
     * @throws HistoryFlushException при ошибке персистирования.
     */
    suspend fun upsert(file: RecentFile, lastPageIndex: Int = 0)

    /**
     * Обновляет поле availabilityStatus для записи с указанным id.
     * Если запись не найдена — операция игнорируется (idempotent).
     *
     * @throws HistoryFlushException при ошибке I/O записи.
     */
    suspend fun updateStatus(id: String, status: AvailabilityStatus)

    /**
     * Обновляет поле lastPageIndex для записи с указанным нормализованным URI.
     * Если запись не существует — no-op.
     *
     * @param uri Нормализованный URI файла. Не логируется (потенциально чувствительные данные).
     * @param pageIndex Текущий видимый индекс страницы (0-based).
     * @throws HistoryFlushException при ошибке I/O.
     */
    suspend fun updateLastPage(uri: String, pageIndex: Int)

    /**
     * Откатывает последнюю операцию upsert для указанного URI.
     *
     * @param uri Нормализованный URI. Не логируется.
     * @throws HistoryFlushException при ошибке I/O.
     */
    suspend fun rollbackUpsert(uri: String)
}
