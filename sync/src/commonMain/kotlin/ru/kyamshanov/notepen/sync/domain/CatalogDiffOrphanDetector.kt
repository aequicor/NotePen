package ru.kyamshanov.notepen.sync.domain

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.sync.domain.model.RemoteCatalog
import ru.kyamshanov.notepen.sync.domain.model.SyncStatus
import ru.kyamshanov.notepen.sync.domain.port.PendingDeltaQueue
import ru.kyamshanov.notepen.sync.domain.port.RemoteDocumentStatusRegistry

private val logger = KotlinLogging.logger {}

/**
 * Tablet-side detector that flips a document to [SyncStatus.OrphanedOnHost]
 * the moment a fresh catalog reveals the host has dropped it, **without
 * waiting for the user to tap the tile or attempt a save**.
 *
 * Trigger: on every new [RemoteCatalog] (or pending-count change), compute
 * the diff between `pendingDeltaCounts.keys` (locally-known docs with unsent
 * edits) and the catalog's `documentId`s.
 *
 * - `documentId` in pending **and not** in catalog → mark [SyncStatus.OrphanedOnHost].
 *   The replay queue will keep buffering, but the user sees the warning so
 *   they don't expect the changes to land.
 * - `documentId` in pending **and** in catalog → mark [SyncStatus.Synced].
 *   Clears stale orphan flags (e.g. host re-added the file between snapshots,
 *   or an earlier `SaveResult` error has since been resolved).
 *
 * Docs without pending edits are intentionally ignored — they have nothing
 * to lose, and surfacing every removed entry would just be noise.
 *
 * Complements [DocumentStatusCoordinator]: that one reacts to host-pushed
 * `DocumentNotFound` / `SaveResult` errors per-operation; this one reacts to
 * passive catalog refreshes so the orphan flag never gets stuck waiting for
 * the user to interact.
 */
class CatalogDiffOrphanDetector(
    private val catalog: Flow<RemoteCatalog?>,
    private val queue: PendingDeltaQueue,
    private val registry: RemoteDocumentStatusRegistry,
) {

    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(
                catalog.filterNotNull(),
                queue.pendingCounts(),
            ) { snapshot, counts -> snapshot to counts }
                .collect { (snapshot, counts) ->
                    if (counts.isEmpty()) return@collect
                    val presentIds = snapshot.recent.mapTo(HashSet()) { it.documentId }
                    for (documentId in counts.keys) {
                        if (documentId !in presentIds) {
                            logger.info {
                                "Catalog from '${snapshot.hostName}' dropped $documentId — " +
                                    "marking OrphanedOnHost (pending=${counts[documentId]})"
                            }
                            registry.set(documentId, SyncStatus.OrphanedOnHost)
                        } else {
                            // Doc is back (or never left); make sure any stale orphan flag is gone.
                            registry.set(documentId, SyncStatus.Synced)
                        }
                    }
                }
        }
    }
}
