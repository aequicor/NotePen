package ru.kyamshanov.notepen.mainscreen.domain.usecase

import ru.kyamshanov.notepen.mainscreen.domain.model.AvailabilityStatus
import ru.kyamshanov.notepen.mainscreen.domain.model.RecentFile
import ru.kyamshanov.notepen.mainscreen.domain.model.UriNormalizer
import ru.kyamshanov.notepen.mainscreen.domain.model.generateUuid
import ru.kyamshanov.notepen.mainscreen.domain.port.FileHistoryRepository

/** Результат добавления файла в историю. */
sealed class AddHistoryResult {
    /** Файл добавлен как новая запись. */
    data class Added(val record: RecentFile) : AddHistoryResult()

    /** Существующая запись перемещена в начало (обновлён openedAt). */
    data class Moved(val record: RecentFile) : AddHistoryResult()

    /**
     * Обнаружено fuzzy-совпадение SAF: запись с тем же именем и размером,
     * но с другим URI. Пользователю требуется подтверждение (AC-5b, CC-1, CC-2).
     */
    data class SafFuzzyMatchDetected(
        val existing: RecentFile,
        val newUri: String,
    ) : AddHistoryResult()
}

/**
 * Добавляет файл в историю или перемещает существующую запись в начало.
 *
 * @param repository Порт для персистирования истории.
 */
class AddToHistoryUseCase(
    private val repository: FileHistoryRepository,
) {
    /**
     * Выполняет добавление/upsert файла в историю.
     *
     * @param uri URI файла (будет нормализован).
     * @param displayName Отображаемое имя файла.
     * @param fileSize Размер файла в байтах (null если неизвестен).
     * @param openedAt Момент открытия (epochMillis).
     * @param lastPageIndex Индекс последней просмотренной страницы (0-based).
     * @return [Result] с [AddHistoryResult] или исключением при ошибке персистирования.
     */
    suspend fun execute(
        uri: String,
        displayName: String,
        fileSize: Long?,
        openedAt: Long,
        lastPageIndex: Int = 0,
    ): Result<AddHistoryResult> = runCatching {
        val normalizedUri = UriNormalizer.normalize(uri)
        val existing = repository.getAll()

        // Android SAF fuzzy-match: одинаковое имя + размер, разные URI (AC-5b, CC-1, CC-2)
        if (normalizedUri.startsWith("content://")) {
            val candidate = existing.firstOrNull { rec ->
                rec.uri != normalizedUri &&
                    rec.displayName == displayName &&
                    fileSize != null && rec.fileSize != null && rec.fileSize == fileSize
            }
            if (candidate != null) {
                return@runCatching AddHistoryResult.SafFuzzyMatchDetected(candidate, normalizedUri)
            }
        }

        val existingRecord = existing.firstOrNull { it.uri == normalizedUri }
        val newRecord = RecentFile(
            id = existingRecord?.id ?: generateUuid(),
            uri = normalizedUri,
            displayName = displayName,
            fileSize = fileSize,
            openedAt = openedAt,
            availabilityStatus = AvailabilityStatus.AVAILABLE,
            lastPageIndex = lastPageIndex,
        )

        repository.upsert(newRecord, lastPageIndex)

        if (existingRecord != null) AddHistoryResult.Moved(newRecord) else AddHistoryResult.Added(newRecord)
    }
}
