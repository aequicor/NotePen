package ru.kyamshanov.notepen.sync.domain.port

import kotlinx.coroutines.flow.Flow
import ru.kyamshanov.notepen.sync.domain.model.StrokeDelta

/**
 * Per-document FIFO queue of locally-generated [StrokeDelta]s that haven't yet
 * been confirmed by the peer.
 *
 * Owned by [ru.kyamshanov.notepen.sync.domain.SyncEngine] (one engine per
 * `documentId`). On every local edit the engine [enqueue]s the freshly-stamped
 * delta; whenever the peer reconnects, the engine [peek]s, replays the batch
 * over the wire, and [markSent] clears the prefix.
 *
 * **Phase 4** ships with an in-memory implementation
 * ([ru.kyamshanov.notepen.sync.infrastructure.InMemoryPendingDeltaQueue]) —
 * survives reconnects within one app session, lost on process death.
 * **Phase 5** will swap in a SQLDelight-backed implementation so the queue
 * survives restarts and powers the "не синхронизировано" badge across launches.
 */
interface PendingDeltaQueue {
    /** Appends [delta] to the end of [documentId]'s queue. */
    suspend fun enqueue(
        documentId: String,
        delta: StrokeDelta,
    )

    /**
     * Returns the current queue for [documentId] in FIFO order, without
     * removing anything. Callers replay the result and then narrow it via
     * [markSent] once the peer confirms (or simply once the WebSocket `send`
     * suspends successfully — Phase 4 acks are implicit).
     */
    suspend fun peek(documentId: String): List<StrokeDelta>

    /**
     * Drops every delta in [documentId]'s queue whose `clock` is `≤ upToClock`.
     * Idempotent.
     */
    suspend fun markSent(
        documentId: String,
        upToClock: Long,
    )

    /** Current pending count for one document. */
    suspend fun pendingCount(documentId: String): Int

    /**
     * Hot stream of `documentId → pendingCount` updated on every [enqueue] /
     * [markSent]. UI subscribes to drive the "Pending(N) — не синхронизировано"
     * badge on Remote-section tiles.
     */
    fun pendingCounts(): Flow<Map<String, Int>>
}
