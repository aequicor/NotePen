package ru.kyamshanov.notepen.sync.domain.port

/**
 * Запрашивает у пира свежий полный снимок аннотаций документа («догон»).
 *
 * Нужен, когда сторона ВКЛЮЧАЕТ живую синхронизацию документа после паузы:
 * пока [ru.kyamshanov.notepen.sync.domain.SyncEngine] был `active=false`,
 * `processPeer` ОТБРАСЫВАЛ входящие удалённые дельты, поэтому правки, сделанные
 * пиром во время паузы, локально не применялись. Повторный запрос полного снимка
 * и его применение по last-writer-wins восстанавливает расхождение, не вводя
 * нового wire-сообщения — реализация переиспользует существующий
 * `AnnotationSnapshotRequest`/`AnnotationSnapshot`.
 *
 * Реализация (инфраструктура/платформа) сама выбирает транспорт (broadcast у
 * [ru.kyamshanov.notepen.sync.domain.port.SyncClient] на планшете или у
 * [ru.kyamshanov.notepen.sync.domain.port.PeerServer] на хосте) и собственный
 * scope/диспетчер. Вызов — fire-and-forget: контроллер дёргается из Compose
 * lifecycle (не suspend), поэтому фактический запрос уходит асинхронно.
 *
 * Оффлайн-семантика: если достижимого пира нет, реализация молча ничего не шлёт
 * (broadcast без подключений — no-op). Догон в этом случае берёт на себя
 * штатный on-connect catch-up (тот же запрос снимка, дёргается при подключении).
 */
fun interface AnnotationResyncRequester {
    /** Просит у пира полный снимок аннотаций для [documentId]. Fire-and-forget. */
    fun requestResync(documentId: String)
}
