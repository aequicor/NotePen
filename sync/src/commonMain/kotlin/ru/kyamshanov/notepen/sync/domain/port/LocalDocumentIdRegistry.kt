package ru.kyamshanov.notepen.sync.domain.port

/**
 * Реестр локальных кеш-копий PDF, скачанных с пира.
 *
 * Хранит соответствие `localPath → documentId`, чтобы tablet'у не приходилось
 * вычислять documentId заново из локального пути (что бы давало другой id,
 * чем host, и ломало синк правок). Регистрация выполняется после успешной
 * загрузки/cache-hit в `RemoteDocumentOpener`; lookup — при открытии файла
 * в редакторе.
 *
 * Реализация должна быть persistent (между запусками приложения) и
 * thread-safe; lookup ожидается синхронным и быстрым (вызывается из UI).
 */
interface LocalDocumentIdRegistry {

    /**
     * Возвращает documentId, ранее зарегистрированный для [localPath],
     * либо `null` если файл не был открыт через `RemoteDocumentOpener`.
     */
    fun lookup(localPath: String): String?

    /**
     * Запоминает соответствие `localPath → documentId`. Перезаписывает, если
     * запись уже существовала. Должен быть persistent — переживает рестарт.
     */
    suspend fun register(localPath: String, documentId: String)

    /** Удаляет запись (например, после удаления кеш-файла cleaner-ом). */
    suspend fun forget(localPath: String)
}
