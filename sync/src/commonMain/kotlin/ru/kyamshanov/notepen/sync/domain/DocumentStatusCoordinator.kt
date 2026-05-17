package ru.kyamshanov.notepen.sync.domain

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.model.SyncStatus
import ru.kyamshanov.notepen.sync.domain.port.RemoteDocumentStatusRegistry
import ru.kyamshanov.notepen.sync.domain.port.SyncClient

private val logger = KotlinLogging.logger {}

private const val ORPHAN_ERROR_MARKER = "Unknown documentId"

/**
 * Tablet-side glue that watches the peer stream for host-originated
 * "this documentId is unknown" signals and marks the corresponding entry
 * in the [registry] as [SyncStatus.OrphanedOnHost].
 *
 * Signals consumed:
 * - [NetworkMessage.DocumentNotFound] — fired by the host for any operation
 *   on a `documentId` outside its current catalog.
 * - [NetworkMessage.SaveResult] with `success = false` and the canonical
 *   "Unknown documentId" error message — the headless save path returns this
 *   instead of a separate [NetworkMessage.DocumentNotFound] for save flows.
 *
 * A successful [NetworkMessage.SaveResult] for the same `documentId`
 * automatically clears the orphan flag — useful if the host re-adds the
 * file to its library between attempts.
 */
class DocumentStatusCoordinator(
    private val client: SyncClient,
    private val registry: RemoteDocumentStatusRegistry,
) {

    fun start(scope: CoroutineScope) {
        scope.launch {
            merge(
                client.incomingMessages.filterIsInstance<NetworkMessage.DocumentNotFound>(),
                client.incomingMessages
                    .filterIsInstance<NetworkMessage.SaveResult>()
                    .filter { it.documentId.isNotEmpty() },
            ).collect { msg ->
                when (msg) {
                    is NetworkMessage.DocumentNotFound -> {
                        logger.info { "Marking ${msg.documentId} as OrphanedOnHost (reason: ${msg.reason})" }
                        registry.set(msg.documentId, SyncStatus.OrphanedOnHost)
                    }
                    is NetworkMessage.SaveResult -> {
                        val isOrphan = !msg.success &&
                            msg.errorMessage?.contains(ORPHAN_ERROR_MARKER, ignoreCase = true) == true
                        if (isOrphan) {
                            logger.info { "Marking ${msg.documentId} as OrphanedOnHost (via SaveResult)" }
                            registry.set(msg.documentId, SyncStatus.OrphanedOnHost)
                        } else if (msg.success) {
                            registry.set(msg.documentId, SyncStatus.Synced)
                        }
                    }
                    else -> Unit
                }
            }
        }
    }
}
