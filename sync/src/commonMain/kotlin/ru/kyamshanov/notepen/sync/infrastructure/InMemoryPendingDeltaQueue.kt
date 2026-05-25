package ru.kyamshanov.notepen.sync.infrastructure

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.kyamshanov.notepen.sync.domain.model.StrokeDelta
import ru.kyamshanov.notepen.sync.domain.port.PendingDeltaQueue

/**
 * In-memory [PendingDeltaQueue] for Phase 4.
 *
 * Buffers offline edits per `documentId`; replays survive reconnects within
 * one app session. The queue is **not** persistent — process death drops
 * everything. Phase 5 replaces this with a SQLDelight-backed implementation.
 *
 * Thread-safety: a single [Mutex] guards both the per-document deque and the
 * aggregated count map. Operations are cheap (list append / prefix drop) so
 * coarse locking is fine for the expected stroke-rate.
 */
class InMemoryPendingDeltaQueue : PendingDeltaQueue {
    private val mutex = Mutex()
    private val queues = mutableMapOf<String, ArrayDeque<StrokeDelta>>()
    private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())

    override suspend fun enqueue(
        documentId: String,
        delta: StrokeDelta,
    ) {
        mutex.withLock {
            queues.getOrPut(documentId) { ArrayDeque() }.addLast(delta)
            publishCountsLocked()
        }
    }

    override suspend fun peek(documentId: String): List<StrokeDelta> = mutex.withLock { queues[documentId]?.toList() ?: emptyList() }

    override suspend fun markSent(
        documentId: String,
        upToClock: Long,
    ) {
        mutex.withLock {
            val deque = queues[documentId] ?: return@withLock
            while (deque.isNotEmpty() && deque.first().clock <= upToClock) {
                deque.removeFirst()
            }
            if (deque.isEmpty()) queues.remove(documentId)
            publishCountsLocked()
        }
    }

    override suspend fun pendingCount(documentId: String): Int = mutex.withLock { queues[documentId]?.size ?: 0 }

    override fun pendingCounts(): Flow<Map<String, Int>> = _counts.asStateFlow()

    private fun publishCountsLocked() {
        // Snapshot under the same lock so observers never see a torn map.
        val snapshot = queues.mapValues { it.value.size }
        _counts.update { snapshot }
    }
}
