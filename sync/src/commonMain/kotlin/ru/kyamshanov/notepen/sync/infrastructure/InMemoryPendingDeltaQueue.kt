package ru.kyamshanov.notepen.sync.infrastructure

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.kyamshanov.notepen.sync.domain.model.StrokeDelta
import ru.kyamshanov.notepen.sync.domain.port.PendingDeltaQueue

private val logger = KotlinLogging.logger {}

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
 *
 * Bounded memory: a runaway peer (long disconnect, delta flood) could otherwise
 * grow the queue without limit and exhaust the heap. [maxPerDocument] caps each
 * document's deque and [maxTotal] caps the sum across all documents; on overflow
 * the **oldest** delta is dropped (FIFO) so the newest edits — the ones a peer
 * most wants on reconnect — always survive. Dropping a pending delta only loses
 * a not-yet-replayed local edit; LWW convergence on the surviving deltas is
 * unaffected.
 */
class InMemoryPendingDeltaQueue(
    private val maxPerDocument: Int = MAX_PER_DOCUMENT,
    private val maxTotal: Int = MAX_TOTAL,
) : PendingDeltaQueue {
    private val mutex = Mutex()
    private val queues = mutableMapOf<String, ArrayDeque<StrokeDelta>>()
    private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())

    override suspend fun enqueue(
        documentId: String,
        delta: StrokeDelta,
    ) {
        mutex.withLock {
            val deque = queues.getOrPut(documentId) { ArrayDeque() }
            // Per-document cap: drop the oldest delta(s) for this document before appending.
            while (deque.size >= maxPerDocument) {
                deque.removeFirst()
                logger.debug {
                    "Pending queue per-document cap ($maxPerDocument) reached for doc=$documentId; dropped oldest delta"
                }
            }
            // Global cap: drop the oldest delta across the OTHER documents until there is
            // room for one more. The current document is excluded so the newest edit always
            // lands; the per-document cap above already keeps `deque` within its own bound.
            while (totalSizeLocked() >= maxTotal) {
                val victim = oldestEvictableQueueLocked(except = documentId) ?: break
                victim.value.removeFirst()
                if (victim.value.isEmpty()) queues.remove(victim.key)
                logger.debug {
                    "Pending queue global cap ($maxTotal) reached; dropped oldest delta from doc=${victim.key}"
                }
            }
            deque.addLast(delta)
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

    /** Total number of pending deltas across all documents. Caller holds [mutex]. */
    private fun totalSizeLocked(): Int = queues.values.sumOf { it.size }

    /**
     * The non-empty queue (other than [except]) whose head delta is the oldest by
     * `clock` — the global FIFO eviction victim. `null` when no other document has
     * anything to drop. Caller holds [mutex].
     */
    private fun oldestEvictableQueueLocked(except: String): Map.Entry<String, ArrayDeque<StrokeDelta>>? =
        queues.entries
            .filter { it.key != except && it.value.isNotEmpty() }
            .minByOrNull { it.value.first().clock }

    companion object {
        /** Default per-document delta cap before the oldest entry is dropped. */
        const val MAX_PER_DOCUMENT: Int = 50_000

        /** Default total delta cap across all documents before the oldest entry is dropped. */
        const val MAX_TOTAL: Int = 200_000
    }
}
