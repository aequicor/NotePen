package ru.kyamshanov.notepen.reflow.api

/**
 * Порт персистентности настроек ридера (глобальных, на все документы).
 *
 * Реализации — платформенные адаптеры в инфраструктурном слое.
 */
public interface ReaderSettingsRepository {
    /**
     * Читает сохранённое состояние.
     * Не бросает: на любой ошибке I/O или парсинга возвращает дефолтный
     * [StoredReaderSettings].
     */
    public suspend fun load(): StoredReaderSettings

    /**
     * Атомарно сохраняет [settings], заменяя предыдущее содержимое.
     *
     * @throws Exception если нижележащая запись упала.
     */
    public suspend fun save(settings: StoredReaderSettings)
}
