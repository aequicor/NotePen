package ru.kyamshanov.notepen.mainscreen.domain.port

/**
 * Порт для хранения и получения миниатюр PDF.
 * Декларируется в `:shared`. Реализуется в инфраструктурном слое.
 *
 * Стратегия хранения: on-disk, переживает перезапуск приложения.
 * Таймаут I/O: 3 секунды на каждый вызов [get] и [put].
 */
interface ThumbnailRepository {

    /**
     * Возвращает закешированные данные миниатюры или null при кеш-промахе или инвалидации по mtime.
     *
     * @param uri Нормализованный URI файла.
     * @param currentFileMtime Актуальное mtime файла (epochMillis) для проверки инвалидации.
     * Таймаут: 3 секунды; при превышении возвращает null.
     */
    suspend fun get(uri: String, currentFileMtime: Long?): ByteArray?

    /**
     * Сохраняет миниатюру в кеш. Применяет LRU-вытеснение при превышении 50 МБ.
     * Таймаут: 3 секунды; при превышении логирует категорию ошибки, не выбрасывает исключение.
     *
     * @param uri Нормализованный URI файла.
     * @param imageData Закодированные данные изображения.
     * @param fileMtime mtime файла на момент генерации (epochMillis).
     */
    suspend fun put(uri: String, imageData: ByteArray, fileMtime: Long?)

    /**
     * Возвращает суммарный размер кеша в байтах.
     */
    suspend fun totalSizeBytes(): Long
}
