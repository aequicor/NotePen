package ru.kyamshanov.notepen.sync.domain.port

import kotlinx.coroutines.flow.StateFlow
import ru.kyamshanov.notepen.sync.domain.model.SyncStatus

/**
 * Tablet-side registry of per-document [SyncStatus] flags. Updated by
 * [ru.kyamshanov.notepen.sync.domain.DocumentStatusCoordinator] in response to
 * host-originated signals; consumed by the main-screen ViewModel to drive
 * orphan badges on Remote tiles.
 *
 * Only non-default ([SyncStatus.Synced]) entries should appear in the map;
 * absence implies healthy.
 */
interface RemoteDocumentStatusRegistry {

    /** Hot stream of `documentId → status`. */
    val statuses: StateFlow<Map<String, SyncStatus>>

    /** Marks [documentId] with [status]; passing [SyncStatus.Synced] clears the entry. */
    suspend fun set(documentId: String, status: SyncStatus)
}
