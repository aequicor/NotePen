package ru.kyamshanov.notepen.mainscreen.domain.model

/**
 * Чистая логика управления историей файлов (без I/O).
 *
 * Все методы — чистые функции: получают список, возвращают новый список.
 * Персистирование выполняется вызывающим кодом через [ru.kyamshanov.notepen.mainscreen.domain.port.FileHistoryRepository].
 */
object FileHistoryManager {
    /** Максимальный размер истории (AC-2). */
    const val MAX_HISTORY_SIZE = 20

    /**
     * Применяет upsert-операцию к списку записей истории.
     *
     * Алгоритм (AC-2, AC-4, AC-5, AC-9e):
     * 1. Нормализовать URI входящего файла.
     * 2. Найти существующую запись по нормализованному URI.
     * 3. Если найдена — переместить в начало (обновить openedAt). Вернуть (newList, null).
     * 4. Если не найдена и size < MAX → добавить в начало. Вернуть (newList, null).
     * 5. Если size == MAX → найти кандидата на вытеснение через [findEvictIndex],
     *    удалить его, добавить новую запись в начало. Вернуть (newList, evictedUri).
     *
     * @param entries Текущий список записей (отсортирован по openedAt DESC).
     * @param newFile Новая запись для добавления/обновления.
     * @return Пара (обновлённый список, URI вытесненной записи или null).
     */
    fun applyUpsert(
        entries: List<RecentFile>,
        newFile: RecentFile,
    ): Pair<List<RecentFile>, String?> {
        val normalizedUri = UriNormalizer.normalize(newFile.uri)
        val existingIndex = entries.indexOfFirst { UriNormalizer.normalize(it.uri) == normalizedUri }

        if (existingIndex >= 0) {
            val updated = entries[existingIndex].copy(openedAt = newFile.openedAt)
            val newList =
                buildList {
                    add(updated)
                    entries.forEachIndexed { index, file -> if (index != existingIndex) add(file) }
                }
            return Pair(newList, null)
        }

        if (entries.size < MAX_HISTORY_SIZE) {
            return Pair(listOf(newFile) + entries, null)
        }

        val evictIndex = findEvictIndex(entries)
        val evictedUri = entries[evictIndex].uri
        val newList =
            buildList {
                add(newFile)
                entries.forEachIndexed { index, file -> if (index != evictIndex) add(file) }
            }
        return Pair(newList, evictedUri)
    }

    /**
     * Находит индекс записи-кандидата на вытеснение в полном (size == MAX) списке.
     *
     * Приоритет вытеснения (AC-9e, CC-8):
     * 1. Самая старая запись с [AvailabilityStatus.NOT_FOUND] или [AvailabilityStatus.FILE_ERROR].
     * 2. Если нет — самая старая запись с [AvailabilityStatus.UNKNOWN] (pessimistic, CC-8).
     * 3. Если все [AvailabilityStatus.AVAILABLE] — самая старая по [RecentFile.openedAt] (LRU, AC-5).
     *
     * @param entries Непустой список записей.
     * @return Индекс записи для вытеснения.
     */
    fun findEvictIndex(entries: List<RecentFile>): Int {
        val notFoundIndex =
            entries
                .withIndex()
                .filter { (_, f) ->
                    f.availabilityStatus == AvailabilityStatus.NOT_FOUND ||
                        f.availabilityStatus == AvailabilityStatus.FILE_ERROR
                }.minByOrNull { (_, f) -> f.openedAt }
                ?.index

        if (notFoundIndex != null) return notFoundIndex

        val unknownIndex =
            entries
                .withIndex()
                .filter { (_, f) -> f.availabilityStatus == AvailabilityStatus.UNKNOWN }
                .minByOrNull { (_, f) -> f.openedAt }
                ?.index

        if (unknownIndex != null) return unknownIndex

        return entries
            .withIndex()
            .minByOrNull { (_, f) -> f.openedAt }
            ?.index
            ?: 0
    }
}
