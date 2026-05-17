package ru.kyamshanov.notepen.sync.domain.model

/**
 * Per-document sync health flag tracked on the tablet.
 *
 * Pending count is tracked separately via
 * [ru.kyamshanov.notepen.sync.domain.port.PendingDeltaQueue.pendingCounts] —
 * a non-zero count means "Pending(N)" regardless of [SyncStatus].
 *
 * [Synced] is the implicit default and does NOT need to be stored — its
 * absence in the registry means "nothing wrong known". Only states worth
 * surfacing to the user live here.
 */
enum class SyncStatus {
    /** Default — no known issue. */
    Synced,

    /**
     * Host explicitly told us this `documentId` is unknown (either via
     * [ru.kyamshanov.notepen.sync.domain.model.NetworkMessage.DocumentNotFound]
     * directly or a `SaveResult(success=false)` with "Unknown documentId").
     * Pending deltas for this id will not replay; the user should be told.
     */
    OrphanedOnHost,
}
