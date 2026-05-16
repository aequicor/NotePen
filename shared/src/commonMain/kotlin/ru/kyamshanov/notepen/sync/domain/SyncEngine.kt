package ru.kyamshanov.notepen.sync.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.model.StrokeDelta
import ru.kyamshanov.notepen.sync.domain.port.PeerServer
import ru.kyamshanov.notepen.sync.domain.port.SyncClient

/**
 * Merges local stroke mutations with deltas received from a peer.
 *
 * **Strategy**: last-writer-wins by [StrokeDelta.clock] per stroke.
 * Tombstones ([StrokeDelta.Removed]) take priority over [StrokeDelta.Added]
 * with an equal or earlier clock.
 *
 * The engine maintains an in-memory pending queue. On reconnect the caller
 * must replay [pendingDeltas] to the new peer.
 *
 * **Usage**: call [applyLocal] after each user stroke/erase action, and
 * call [processPeer] when a [NetworkMessage.StrokeDeltaMessage] arrives
 * from the peer.
 *
 * Emits merged deltas on [mergedDeltas] for the presentation layer to
 * apply to [PdfDrawingState].
 */
class SyncEngine(
    val deviceId: String,
    private val scope: CoroutineScope,
    private val server: PeerServer? = null,
    private val client: SyncClient? = null,
) {
    private val clocks = mutableMapOf<String, Long>() // strokeId → last seen clock
    private val _merged = MutableSharedFlow<StrokeDelta>(extraBufferCapacity = 128)
    private var strokeSeq = 0L

    /** Incoming deltas after merge — presentation layer subscribes here. */
    val mergedDeltas: SharedFlow<StrokeDelta> = _merged.asSharedFlow()

    /** Deltas generated locally that have not yet been acknowledged. */
    val pendingDeltas = ArrayDeque<StrokeDelta>()

    private var logicalClock = 0L

    /** Call when the user draws or erases a stroke on this device. */
    fun applyLocal(delta: StrokeDelta) {
        logicalClock++
        val stamped = delta.withClock(logicalClock)
        pendingDeltas.addLast(stamped)
        scope.launch {
            val msg = NetworkMessage.StrokeDeltaMessage(stamped)
            server?.send(msg) ?: client?.send(msg)
        }
    }

    /**
     * Sends a batch of local deltas as a single, ordered burst.
     *
     * Unlike calling [applyLocal] N times — which fires N independent
     * fire-and-forget coroutines whose ordering at the wire is undefined —
     * this method stamps every delta synchronously and sends them all from
     * one coroutine sequentially. Required for erase gestures that produce
     * many [StrokeDelta.Removed] + [StrokeDelta.Added] in one shot: the
     * receiver must apply them in a predictable order, and individual sends
     * mustn't be lost to scheduling races on a saturated dispatcher.
     */
    fun applyLocalBatch(deltas: List<StrokeDelta>) {
        if (deltas.isEmpty()) return
        val stamped = deltas.map {
            logicalClock++
            val s = it.withClock(logicalClock)
            pendingDeltas.addLast(s)
            s
        }
        scope.launch {
            for (d in stamped) {
                val msg = NetworkMessage.StrokeDeltaMessage(d)
                server?.send(msg) ?: client?.send(msg)
            }
        }
    }

    /** Call for each [NetworkMessage.StrokeDeltaMessage] received from the peer. */
    fun processPeer(delta: StrokeDelta) {
        logicalClock = maxOf(logicalClock, delta.clock) + 1
        if (shouldApply(delta)) {
            clocks[delta.strokeId] = delta.clock
            scope.launch { _merged.emit(delta) }
        }
    }

    private fun shouldApply(incoming: StrokeDelta): Boolean {
        val seen = clocks[incoming.strokeId]
        return when {
            seen == null -> true
            // Tombstone always wins over add with equal clock
            incoming is StrokeDelta.Removed && incoming.clock >= seen -> true
            incoming is StrokeDelta.Added && incoming.clock > seen -> true
            else -> false
        }
    }

    fun clearPending() = pendingDeltas.clear()

    /** Generates a stroke ID that is unique within this device's session. */
    fun newStrokeId(): String = "$deviceId#${++strokeSeq}"
}

private fun StrokeDelta.withClock(clock: Long): StrokeDelta = when (this) {
    is StrokeDelta.Added -> copy(clock = clock)
    is StrokeDelta.Removed -> copy(clock = clock)
}
