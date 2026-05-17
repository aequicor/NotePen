package ru.kyamshanov.notepen.sync.domain

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.sync.domain.model.PairingState

private val logger = KotlinLogging.logger {}

/**
 * Watches a peer-state stream and, on every transition into
 * [PairingState.Connected], calls [SyncEngine.drainAndReplay] on every engine
 * currently in the [registry].
 *
 * Works for both sides:
 * - On the tablet, pass `client.state` to flush deltas the user drew while the
 *   WebSocket was down.
 * - On the host, pass `server.state` to do the same once a peer (re)pairs.
 *
 * Only engines that already exist in the registry are drained — there's no
 * point creating an engine just to replay an empty queue. The registry grows
 * on demand from incoming/outgoing messages, so by the time a reconnect lands
 * every actively-used document is already represented.
 */
class PendingDeltaReplayCoordinator(
    private val registry: SyncEngineRegistry,
    private val stateFlow: Flow<PairingState>,
) {

    /** Subscribes to [stateFlow] until [scope] is cancelled. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            stateFlow
                .distinctUntilChanged()
                .collect { st ->
                    if (st !is PairingState.Connected) return@collect
                    val engines = registry.snapshot().values
                    if (engines.isEmpty()) return@collect
                    logger.info { "Pairing connected — draining ${engines.size} engine(s)" }
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
