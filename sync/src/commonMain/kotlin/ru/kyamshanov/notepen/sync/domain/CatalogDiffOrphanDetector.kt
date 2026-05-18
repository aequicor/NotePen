package ru.kyamshanov.notepen.sync.domain

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.RemoteCatalog
import ru.kyamshanov.notepen.sync.domain.model.SyncStatus
import ru.kyamshanov.notepen.sync.domain.port.PendingDeltaQueue
import ru.kyamshanov.notepen.sync.domain.port.RemoteDocumentStatusRegistry

private val logger = KotlinLogging.logger {}

/**
 * Tablet-side detector that flips a document to [SyncStatus.OrphanedOnHost]
 * the moment a fresh catalog from **any** connected host reveals the document
 * has been dropped.
 *
 * Trigger: on every new catalog map (or pending-count change), compute the
 * diff between `pendingDeltaCounts.keys` (locally-known docs with unsent
 * edits) and the union of all hosts' catalog `documentId`s.
 *
 * - `documentId` in pending **and not** in ANY catalog → mark
 *   [SyncStatus.OrphanedOnHost]. A document is considered live if at least
 *   one connected host still has it.
 * - `documentId` in pending **and** in at least one catalog → mark
 *   [SyncStatus.Synced].
 */
class CatalogDiffOrphanDetector(
    private val catalogs: Flow<Map<DeviceInfo, RemoteCatalog>>,
    private val queue: PendingDeltaQueue,
    private val registry: RemoteDocumentStatusRegistry,
) {

    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(
                catalogs,
                queue.pendingCounts(),
            ) { snapshot, counts -> snapshot to counts }
                .collect { (snapshot, counts) ->
                    if (counts.isEmpty() || snapshot.isEmpty()) return@collect
                    val presentIds = snapshot.values
                        .flatMap { catalog -> catalog.recent.map { it.documentId } }
                        .toHashSet()
                    for (documentId in counts.keys) {
                        if (documentId !in presentIds) {
                            logger.info {
                                "No connected host still has $documentId — marking OrphanedOnHost (pending=${counts[documentId]})"
                            }
                            registry.set(documentId, SyncStatus.OrphanedOnHost)
                        } else {
                            registry.set(documentId, SyncStatus.Synced)
                        }
                    }
                }
        }
    }
}
