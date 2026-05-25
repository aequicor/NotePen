package ru.kyamshanov.notepen.sync.domain

import kotlinx.coroutines.CoroutineScope
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.port.PeerServer
import ru.kyamshanov.notepen.sync.domain.port.PendingDeltaQueue
import ru.kyamshanov.notepen.sync.domain.port.SyncClient

/**
 * Lazily creates and caches one [SyncEngine] per `documentId`.
 *
 * Multi-document sync uses this registry to demultiplex incoming
 * [NetworkMessage.StrokeDeltaMessage]s: the message carries its
 * own `documentId`, the dispatcher looks the engine up and forwards
 * the delta to [SyncEngine.processPeer].
 *
 * Phase 1 introduces the registry as a thin wrapper around the same
 * single-engine factory; full multi-engine routing arrives in Phase 3
 * when documents stop sharing a single in-flight session.
 *
 * Thread-safety: callers are expected to obtain engines from a single
 * coordinator coroutine. The internal map is not synchronised because
 * NotePen's sync wiring already serialises access through one scope.
 */
class SyncEngineRegistry(
    private val deviceId: String,
    private val scope: CoroutineScope,
    private val server: PeerServer? = null,
    private val client: SyncClient? = null,
    /** Shared offline buffer threaded into every engine the registry produces. */
    private val pendingQueue: PendingDeltaQueue? = null,
) {
    private val engines = mutableMapOf<String, SyncEngine>()

    /** Returns the engine for [documentId], creating it on first request. */
    fun get(documentId: String): SyncEngine =
        engines.getOrPut(documentId) {
            SyncEngine(
                deviceId = deviceId,
                documentId = documentId,
                scope = scope,
                server = server,
                client = client,
                pendingQueue = pendingQueue,
            )
        }

    /** Returns a snapshot of currently registered engines. */
    fun snapshot(): Map<String, SyncEngine> = engines.toMap()

    /** Removes the engine for [documentId] (if any). Caller is responsible for cancelling its work. */
    fun remove(documentId: String): SyncEngine? = engines.remove(documentId)
}
