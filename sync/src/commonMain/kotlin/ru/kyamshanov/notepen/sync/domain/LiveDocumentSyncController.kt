package ru.kyamshanov.notepen.sync.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import ru.kyamshanov.notepen.sync.domain.port.AnnotationResyncRequester
import ru.kyamshanov.notepen.sync.domain.port.OpenDocumentRegistry

/**
 * Тонкий контроллер «синхронизации документа» (M4) поверх существующего sync-стека.
 *
 * Позволяет пользователю включить живую синхронизацию для КОНКРЕТНОГО открытого
 * документа (PC ↔ планшет правки текут в реальном времени) и выключить её —
 * не трогая ни транспорт, ни wire-протокол.
 *
 * При [enable] контроллер:
 *  1. пинит документ в [OpenDocumentRegistry] (ref-counted `acquire`). Это пин
 *     контроллера — он удерживается ТОЛЬКО пока живая синхронизация ВКЛЮЧЕНА, и
 *     защищает консистентность активного синка: пока правки реально текут, фоновые
 *     сервисы (напр. `LocalCachedDocumentCleaner`) не должны трогать файл. Это
 *     отдельный пин от того, что берёт сам редактор на всё время открытия документа
 *     (см. `EditorPanel` `openDocumentRegistry.acquire/release` в `DisposableEffect`),
 *     назначение которого — не дать удалить файл, пока он открыт. Реестр считает
 *     ref-count, так что оба пина сосуществуют корректно;
 *  2. помечает [SyncEngine] документа активно вещающим ([SyncEngine.setActive]`(true)`)
 *     — движок начинает реально транслировать локальные дельты и применять удалённые;
 *  3. запрашивает у пира свежий полный снимок аннотаций ([AnnotationResyncRequester],
 *     если задан) — ДОГОН. Пока документ был на паузе, `SyncEngine.processPeer`
 *     отбрасывал входящие удалённые дельты, поэтому правки пира за время паузы
 *     локально не применились. Повторный запрос снимка и его применение по
 *     last-writer-wins устраняет расхождение хост↔планшет без нового wire-сообщения.
 *
 * При [disable]: парный `release` + [SyncEngine.setActive]`(false)`. Снимок НЕ нужен
 * (выключение синхронизации ничего не «теряет»). Оффлайн-очередь накопленных дельт
 * ([ru.kyamshanov.notepen.sync.domain.port.PendingDeltaQueue]) НЕ трогается — при
 * повторном включении она доиграется через `drainAndReplay`.
 *
 * Состояние per-document — реактивный набор «живых» id ([liveDocumentIds]); для
 * конкретного документа — [isLive]. По умолчанию синхронизация документа ВЫКЛЮЧЕНА
 * (пустой набор): движки создаются с `active=true`, но контроллер выставляет их в
 * паузу при первом [bind], чтобы открытие документа само по себе не начинало вещание.
 *
 * Идемпотентность: повторный [enable]/[disable] одного и того же id — no-op (ни
 * лишнего `acquire`, ни лишнего `release`).
 *
 * Все методы ожидаются с одного потока (Compose lifecycle редактора); внутренний
 * набор обновляется через атомарный `MutableStateFlow.update`.
 */
class LiveDocumentSyncController(
    private val openDocumentRegistry: OpenDocumentRegistry,
    private val syncEngineRegistry: SyncEngineRegistry,
    /**
     * Запрашивает полный снимок аннотаций у пира при включении синхронизации
     * (догон правок, пропущенных пока документ был на паузе). `null` — снимок не
     * запрашивается (напр. sync-стек без клиента/сервера или в тестах, где догон
     * не проверяется). Вызывается строго на переходе OFF→ON, поэтому повторный
     * [enable] уже «живого» документа НЕ шлёт повторный запрос.
     */
    private val resyncRequester: AnnotationResyncRequester? = null,
) {
    private val _liveDocumentIds = MutableStateFlow<Set<String>>(emptySet())

    /** Реактивный снимок документов с включённой живой синхронизацией. */
    val liveDocumentIds: StateFlow<Set<String>> = _liveDocumentIds.asStateFlow()

    /** Текущее (синхронное) состояние живой синхронизации [documentId]. */
    fun isLiveNow(documentId: String): Boolean = documentId in _liveDocumentIds.value

    /** Поток on/off для конкретного [documentId] — для тумблера в редакторе. */
    fun isLive(documentId: String): Flow<Boolean> =
        _liveDocumentIds
            .map { documentId in it }
            .distinctUntilChanged()

    /**
     * Подготавливает движок документа к ручному переключению: создаёт его (если ещё
     * нет) и выставляет в паузу. Вызывается при открытии документа, чтобы он НЕ
     * начинал вещать сам по себе (default OFF). No-op для уже «живого» документа.
     */
    fun bind(documentId: String) {
        if (documentId.isBlank()) return
        if (documentId in _liveDocumentIds.value) return
        syncEngineRegistry.get(documentId).setActive(false)
    }

    /**
     * Включает живую синхронизацию [documentId]: pin + active broadcasting + догон
     * (полный снимок у пира). Идемпотентно — повторный [enable] уже «живого»
     * документа выходит ДО любых сайд-эффектов, поэтому ни лишнего `acquire`, ни
     * повторного запроса снимка не будет.
     */
    fun enable(documentId: String) {
        if (documentId.isBlank()) return
        if (documentId in _liveDocumentIds.value) return
        openDocumentRegistry.acquire(documentId)
        syncEngineRegistry.get(documentId).setActive(true)
        _liveDocumentIds.update { it + documentId }
        // Догон: пока документ был на паузе, processPeer отбрасывал удалённые дельты.
        // Просим у пира свежий полный снимок — он применится по LWW и восстановит то,
        // что пир/хост изменил во время паузы. Fire-and-forget; оффлайн — no-op
        // (broadcast без подключений ничего не шлёт, догон возьмёт on-connect catch-up).
        resyncRequester?.requestResync(documentId)
    }

    /** Выключает живую синхронизацию [documentId]: release + пауза вещания. Идемпотентно. */
    fun disable(documentId: String) {
        if (documentId.isBlank()) return
        if (documentId !in _liveDocumentIds.value) return
        openDocumentRegistry.release(documentId)
        syncEngineRegistry.get(documentId).setActive(false)
        _liveDocumentIds.update { it - documentId }
    }

    /** Переключает живую синхронизацию [documentId]. */
    fun toggle(documentId: String) {
        if (documentId in _liveDocumentIds.value) disable(documentId) else enable(documentId)
    }
}
