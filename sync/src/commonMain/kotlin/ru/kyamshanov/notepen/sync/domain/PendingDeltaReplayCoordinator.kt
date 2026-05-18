package ru.kyamshanov.notepen.sync.domain

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * Watches a "connection (re)established" signal and, on every emission, calls
 * [SyncEngine.drainAndReplay] on every engine currently in the [registry].
 *
 * Works for both sides:
 * - On the tablet, pass a flow derived from `client.state` filtered to
 *   [ru.kyamshanov.notepen.sync.domain.model.PairingState.Connected] (mapped
 *   to `Unit`) to flush deltas the user drew while the WebSocket was down.
 * - On the host, pass a flow derived from `server.connectedPeers` that emits
 *   on every transition `empty → non-empty` so replay runs when the first
 *   peer (re)connects after the queue was filled.
 *
 * Only engines that already exist in the registry are drained — there's no
 * point creating an engine just to replay an empty queue. The registry grows
 * on demand from incoming/outgoing messages, so by the time a reconnect lands
 * every actively-used document is already represented.
 */
class PendingDeltaReplayCoordinator(
    private val registry: SyncEngineRegistry,
    private val connectionEstablished: Flow<Unit>,
) {

    /** Subscribes to [connectionEstablished] until [scope] is cancelled. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            connectionEstablished.collect {
                val engines = registry.snapshot().values
                if (engines.isEmpty()) return@collect
                logger.info { "Connection established — draining ${engines.size} engine(s)" }
                for (engine in engines) {
                    runCatching { engine.drainAndReplay() }
                        .onFailure {
                            logger.warn {
                                "drainAndReplay failed for doc=${engine.documentId}: ${it::class.simpleName}"
                            }
                        }
                }
            }
        }
    }
}
