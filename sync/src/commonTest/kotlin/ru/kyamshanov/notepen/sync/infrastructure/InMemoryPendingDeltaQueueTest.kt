package ru.kyamshanov.notepen.sync.infrastructure

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import ru.kyamshanov.notepen.sync.domain.model.StrokeDelta
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InMemoryPendingDeltaQueueTest {
    /** Minimal [StrokeDelta] whose [clock] doubles as its FIFO identity in assertions. */
    private fun delta(clock: Long): StrokeDelta =
        StrokeDelta.Removed(
            strokeId = "s$clock",
            pageIndex = 0,
            authorDeviceId = "dev",
            clock = clock,
        )

    @Test
    fun enqueue_underCap_keepsEverythingInFifoOrder() =
        runTest {
            val queue = InMemoryPendingDeltaQueue(maxPerDocument = 4, maxTotal = 100)

            (0L until 3L).forEach { queue.enqueue("doc", delta(it)) }

            assertEquals(listOf(0L, 1L, 2L), queue.peek("doc").map { it.clock })
            assertEquals(3, queue.pendingCount("doc"))
        }

    @Test
    fun enqueue_pastPerDocumentCap_staysBoundedAndKeepsNewest() =
        runTest {
            val cap = 5
            val queue = InMemoryPendingDeltaQueue(maxPerDocument = cap, maxTotal = 1_000)

            // Push far beyond the cap; clocks 0..19 in order.
            (0L until 20L).forEach { queue.enqueue("doc", delta(it)) }

            val remaining = queue.peek("doc").map { it.clock }
            assertEquals(cap, remaining.size, "size is bounded by the per-document cap")
            assertEquals(cap, queue.pendingCount("doc"))
            // The oldest were dropped; only the newest `cap` survive, still in FIFO order.
            assertEquals(listOf(15L, 16L, 17L, 18L, 19L), remaining, "newest deltas preserved, oldest dropped")
        }

    @Test
    fun enqueue_atPerDocumentCapBoundary_dropsExactlyOneOldestPerOverflow() =
        runTest {
            val queue = InMemoryPendingDeltaQueue(maxPerDocument = 3, maxTotal = 1_000)

            (0L until 3L).forEach { queue.enqueue("doc", delta(it)) } // [0,1,2] — full
            queue.enqueue("doc", delta(3L)) // overflow by one → drop 0 → [1,2,3]

            assertEquals(listOf(1L, 2L, 3L), queue.peek("doc").map { it.clock })
        }

    @Test
    fun enqueue_pastGlobalCap_dropsOldestAcrossOtherDocumentsAndLandsNewest() =
        runTest {
            // Per-document cap high enough that only the GLOBAL cap bites.
            val queue = InMemoryPendingDeltaQueue(maxPerDocument = 1_000, maxTotal = 4)

            // docA gets the globally-oldest deltas (clocks 0,1), docB the next (2,3).
            queue.enqueue("docA", delta(0L))
            queue.enqueue("docA", delta(1L))
            queue.enqueue("docB", delta(2L))
            queue.enqueue("docB", delta(3L))
            assertEquals(4, queue.pendingCount("docA") + queue.pendingCount("docB"), "exactly at the global cap")

            // One more (clock 4) on docB must evict the globally-oldest (docA's clock 0) and still land.
            queue.enqueue("docB", delta(4L))

            val total = queue.pendingCount("docA") + queue.pendingCount("docB")
            assertEquals(4, total, "total stays bounded by the global cap")
            assertEquals(listOf(1L), queue.peek("docA").map { it.clock }, "globally-oldest delta (docA clock 0) dropped")
            assertEquals(listOf(2L, 3L, 4L), queue.peek("docB").map { it.clock }, "the freshly enqueued delta landed")
        }

    @Test
    fun enqueue_floodOnManyDocuments_neverExceedsGlobalCap() =
        runTest {
            val maxTotal = 50
            val queue = InMemoryPendingDeltaQueue(maxPerDocument = 1_000, maxTotal = maxTotal)

            var clock = 0L
            repeat(10) { doc ->
                repeat(100) {
                    queue.enqueue("doc$doc", delta(clock++))
                }
                val total = (0..9).sumOf { queue.pendingCount("doc$it") }
                assertTrue(total <= maxTotal, "global cap never exceeded mid-flood (was $total)")
            }

            val finalTotal = (0..9).sumOf { queue.pendingCount("doc$it") }
            assertEquals(maxTotal, finalTotal, "queue saturates at exactly the global cap after a flood")

            // The single newest delta enqueued must always be present.
            val lastClock = clock - 1
            val lastDoc = "doc9"
            assertTrue(
                queue.peek(lastDoc).any { it.clock == lastClock },
                "the most-recent delta survives the flood",
            )
        }

    @Test
    fun pendingCounts_reflectBoundedSizeAfterOverflow() =
        runTest {
            val cap = 6
            val queue = InMemoryPendingDeltaQueue(maxPerDocument = cap, maxTotal = 1_000)

            (0L until 50L).forEach { queue.enqueue("doc", delta(it)) }

            // The published hot-stream snapshot reports the bounded size, not 50.
            // pendingCounts() is StateFlow-backed, so first() yields the current value.
            val current = queue.pendingCounts().first()
            assertEquals(cap, current["doc"], "pendingCounts() reports the bounded size")
        }
}
