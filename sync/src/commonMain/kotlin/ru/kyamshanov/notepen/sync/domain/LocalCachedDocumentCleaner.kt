package ru.kyamshanov.notepen.sync.domain

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.RemoteCatalog
import ru.kyamshanov.notepen.sync.domain.port.LocalDocumentIdRegistry
import ru.kyamshanov.notepen.sync.domain.port.OpenDocumentRegistry
import ru.kyamshanov.notepen.sync.infrastructure.okio_delete
import ru.kyamshanov.notepen.sync.infrastructure.okio_exists

private val logger = KotlinLogging.logger {}

/**
 * Удаляет локальные кеш-копии PDF, скачанные через [RemoteDocumentOpener],
 * сразу после того как все накопленные offline-правки для этого документа
 * успешно проиграны на пир.
 *
 * Семантика: «успешный flush» = переход `pendingCount > 0 → 0` (или
 * `pendingCount > 0 → отсутствует в карте`). Если pendingCount был 0 с момента
 * подписки — ничего не делаем (не было offline-эпизода).
 *
 * Безопасность: не трогаем файл, если документ открыт в редакторе
 * ([openDocuments]). Удаление отложится до следующего перехода 0 → … → 0,
 * либо ручного перехода в другую сессию.
 *
 * Имя файла на диске — `displayName` из последнего полученного каталога
 * ([catalogsFlow]) для соответствующего `documentId`.
 *
 * @param receivedPdfDir Каталог, где [RemoteDocumentOpener] складирует PDF-ы.
 * @param pendingCounts Поток `documentId → pendingCount` из
 *        [ru.kyamshanov.notepen.sync.domain.port.PendingDeltaQueue].
 * @param catalogsFlow Карта `peer → catalog` (для resolve documentId → displayName).
 * @param openDocuments Реестр документов, открытых в редакторе.
 */
class LocalCachedDocumentCleaner(
    private val receivedPdfDir: String,
    private val pendingCounts: Flow<Map<String, Int>>,
    private val catalogsFlow: Flow<Map<DeviceInfo, RemoteCatalog>>,
    private val openDocuments: OpenDocumentRegistry,
    private val documentIdRegistry: LocalDocumentIdRegistry? = null,
) {

    fun start(scope: CoroutineScope) {
        scope.launch {
            var previousCounts = emptyMap<String, Int>()
            // Игнорируем первый эмит, чтобы не удалять файлы при холодном старте
            // когда pendingCount уже 0 — это норма, а не свежий flush.
            var bootstrapped = false
            combine(
                pendingCounts,
                catalogsFlow,
                openDocuments.openDocumentIds,
            ) { counts, catalogs, openIds ->
                Triple(counts, catalogs, openIds)
            }.collect { (counts, catalogs, openIds) ->
                if (!bootstrapped) {
                    previousCounts = counts
                    bootstrapped = true
                    return@collect
                }
                val flushed = previousCounts.keys.filter { docId ->
                    val before = previousCounts[docId] ?: 0
                    val after = counts[docId] ?: 0
                    before > 0 && after == 0
                }
                previousCounts = counts
                if (flushed.isEmpty()) return@collect
                val displayNames = displayNamesByDocumentId(catalogs)
                for (docId in flushed) {
                    if (docId in openIds) {
                        logger.info { "Skipping cache cleanup for $docId — document is open" }
                        continue
                    }
                    val name = displayNames[docId] ?: continue
                    val path = joinPath(receivedPdfDir, documentIdToCacheFileName(docId, name))
                    if (!okio_exists(path)) continue
                    val ok = runCatching { okio_delete(path) }.getOrElse {
                        logger.warn { "Failed to delete cached $path: ${it::class.simpleName}" }
                        false
                    }
                    if (ok) {
                        logger.info { "Deleted local cache for synced document $docId at $path" }
                        documentIdRegistry?.forget(path)
                    }
                }
            }
        }
    }

    private fun displayNamesByDocumentId(
        catalogs: Map<DeviceInfo, RemoteCatalog>,
    ): Map<String, String> {
        val out = mutableMapOf<String, String>()
        for (catalog in catalogs.values) {
            for (entry in catalog.recent) {
                if (entry.documentId !in out && entry.displayName.isNotBlank()) {
                    out[entry.documentId] = entry.displayName
                }
            }
        }
        return out
    }
}

private fun joinPath(dir: String, name: String): String {
    val sep = if (dir.contains('\\')) "\\" else "/"
    return if (dir.endsWith('/') || dir.endsWith('\\')) "$dir$name" else "$dir$sep$name"
}
