package ru.kyamshanov.notepen.sync.domain.port

import kotlinx.coroutines.flow.StateFlow

/**
 * Простой in-memory реестр документов, открытых в редакторе прямо сейчас.
 *
 * Используется фоновыми сервисами уровня приложения, которые хотят выполнить
 * операцию над файлом только когда он гарантированно не используется UI
 * (типичный кейс — удаление локальной кеш-копии после успешной синхронизации
 * правок: `LocalCachedDocumentCleaner` ждёт пока документ закроется, чтобы
 * избежать race на Windows, где удаление открытого файла запрещено).
 *
 * Реестр считает acquire/release по семантике reference counter — один и тот
 * же документ можно открыть несколько раз (например split-view), и releas-ом
 * считается «отпускание последнего держателя».
 */
interface OpenDocumentRegistry {

    /** Снимок множества открытых documentId-ов. Реактивный — обновляется на каждом acquire/release. */
    val openDocumentIds: StateFlow<Set<String>>

    /** Зарегистрировать открытие. Парный вызов — [release]. */
    fun acquire(documentId: String)

    /** Снять регистрацию. No-op, если документ не был открыт. */
    fun release(documentId: String)
}
