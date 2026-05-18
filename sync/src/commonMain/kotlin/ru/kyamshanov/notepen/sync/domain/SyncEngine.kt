package ru.kyamshanov.notepen.sync.domain

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.model.StrokeDelta
import ru.kyamshanov.notepen.sync.domain.port.PeerServer
import ru.kyamshanov.notepen.sync.domain.port.PendingDeltaQueue
import ru.kyamshanov.notepen.sync.domain.port.SyncClient

private val logger = KotlinLogging.logger {}

/**
 * Merges local stroke mutations with deltas received from a peer for a single
 * logical document.
 *
 * **Strategy**: last-writer-wins by [StrokeDelta.clock] per stroke.
 * Tombstones ([StrokeDelta.Removed]) take priority over [StrokeDelta.Added]
 * with an equal or earlier clock.
 *
 * **Offline buffering**: every local mutation is appended to [pendingQueue]
 * before being sent. A successful send (`runCatching { send } == success`)
 * is treated as an implicit ack and immediately drops the entry from the
 * queue. On reconnect, callers invoke [drainAndReplay] to flush whatever the
 * disconnected sends left behind.
 *
 * **Scope**: one engine per [documentId]. When more than one document can be
 * open in parallel, use [SyncEngineRegistry] to look up the right engine for
 * each incoming [NetworkMessage.StrokeDeltaMessage].
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
    /** Logical document this engine is bound to. Wire-stamped on every outgoing message. */
    val documentId: String,
    private val scope: CoroutineScope,
    private val server: PeerServer? = null,
    private val client: SyncClient? = null,
    /**
     * Persistent (or in-memory) queue used to buffer local deltas while the
     * peer is unreachable. Null disables buffering — used by tests and the
     * desktop host that never goes offline against itself.
     */
    private val pendingQueue: PendingDeltaQueue? = null,
) {
    private val clocks = mutableMapOf<String, Long>() // strokeId → last seen clock
    private val _merged = MutableSharedFlow<StrokeDelta>(extraBufferCapacity = 128)
    private var strokeSeq = 0L

    /** Incoming deltas after merge — presentation layer subscribes here. */
    val mergedDeltas: SharedFlow<StrokeDelta> = _merged.asSharedFlow()

    private var logicalClock = 0L

    /** Call when the user draws or erases a stroke on this device. */
    fun applyLocal(delta: StrokeDelta) {
        logicalClock++
        val stamped = delta.withClock(logicalClock)
        scope.launch {
            // Mirror local mutations onto [mergedDeltas] so passive listeners
            // (e.g. host-side HostAnnotationProjection) see one complete
            // stream of every change, peer-originated or local. SyncBridge
            // on the active DetailsContent dedupes by strokeId, so the
            // drawing UI never double-applies.
            _merged.emit(stamped)
            pendingQueue?.enqueue(documentId, stamped)
            sendStamped(listOf(stamped))
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
            it.withClock(logicalClock)
        }
        scope.launch {
            for (d in stamped) {
                _merged.emit(d)
                pendingQueue?.enqueue(documentId, d)
            }
            sendStamped(stamped)
        }
    }

    /**
     * Replays everything currently buffered in [pendingQueue]. Idempotent;
     * safe to invoke repeatedly (e.g. on every transition into
     * [ru.kyamshanov.notepen.sync.domain.model.PairingState.Connected]).
     *
     * Implicit-ack semantics: the queue prefix is dropped only after the WebSocket
     * `send` call returns without throwing — otherwise the entries stay buffered
     * for the next reconnect.
     */
    suspend fun drainAndReplay() {
        val queue = pendingQueue ?: return
        val pending = queue.peek(documentId)
        if (pending.isEmpty()) return
        if (!hasReachablePeer()) {
            logger.info { "drainAndReplay skipped — no reachable peer for doc=$documentId, " +
                "${pending.size} delta(s) stay buffered" }
            return
        }
        logger.info { "Replaying ${pending.size} pending delta(s) for doc=$documentId" }
        val result = runCatching {
            for (d in pending) {
                val msg = NetworkMessage.StrokeDeltaMessage(delta = d, documentId = documentId)
                server?.broadcast(msg) ?: client?.broadcast(msg)
            }
        }
        if (result.isSuccess) {
            queue.markSent(documentId, pending.last().clock)
        } else {
            logger.warn { "drainAndReplay failed for doc=$documentId: ${result.exceptionOrNull()?.message}" }
        }
    }

    private suspend fun sendStamped(stamped: List<StrokeDelta>) {
        // Без реального получателя `broadcast` молча проходит мимо — `runCatching`
        // вернёт success, и `markSent` сотрёт правки, которые на самом деле
        // никуда не ушли. Поэтому сначала проверяем, есть ли с кем синкаться.
        if (!hasReachablePeer()) return
        val result = runCatching {
            for (d in stamped) {
                val msg = NetworkMessage.StrokeDeltaMessage(delta = d, documentId = documentId)
                server?.broadcast(msg) ?: client?.broadcast(msg)
            }
        }
        if (result.isSuccess) {
            pendingQueue?.markSent(documentId, stamped.last().clock)
        }
        // On failure (typically connection down) the entries remain queued
        // for the next [drainAndReplay] cycle.
    }

    /**
     * True если у нас есть хотя бы один подключённый пир, кому broadcast реально дойдёт.
     * Используем `.first()` поверх Flow-обёртки над StateFlow — возвращает текущий
     * снапшот без блокировки.
     */
    private suspend fun hasReachablePeer(): Boolean {
        val srv = server
        if (srv != null && srv.connectedPeers.first().isNotEmpty()) return true
        val cli = client
        if (cli != null && cli.connectedHosts.first().isNotEmpty()) return true
        return false
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

    /** Generates a stroke ID that is unique within this device's session. */
    fun newStrokeId(): String = "$deviceId#${++strokeSeq}"
}

private fun StrokeDelta.withClock(clock: Long): StrokeDelta = when (this) {
    is StrokeDelta.Added -> copy(clock = clock)
    is StrokeDelta.Removed -> copy(clock = clock)
}
