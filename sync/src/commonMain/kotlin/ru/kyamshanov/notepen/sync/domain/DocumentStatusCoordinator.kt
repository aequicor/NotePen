package ru.kyamshanov.notepen.sync.domain

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.model.SyncStatus
import ru.kyamshanov.notepen.sync.domain.port.RemoteDocumentStatusRegistry
import ru.kyamshanov.notepen.sync.domain.port.SyncClient

private val logger = KotlinLogging.logger {}

private const val ORPHAN_ERROR_MARKER = "Unknown documentId"

/**
 * Tablet-side glue that watches the peer stream across **all** connected hosts
 * for host-originated "this documentId is unknown" signals and marks the
 * corresponding entry in the [registry] as [SyncStatus.OrphanedOnHost].
 *
 * Signals consumed:
 * - [NetworkMessage.DocumentNotFound] from any host.
 * - [NetworkMessage.SaveResult] with `success = false` and the canonical
 *   "Unknown documentId" error message.
 *
 * A successful [NetworkMessage.SaveResult] for the same `documentId`
 * automatically clears the orphan flag.
 */
class DocumentStatusCoordinator(
    private val client: SyncClient,
    private val registry: RemoteDocumentStatusRegistry,
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            client.incomingMessages.collect { hostMessage ->
                when (val msg = hostMessage.message) {
                    is NetworkMessage.DocumentNotFound -> {
                        logger.info {
                            "Marking ${msg.documentId} as OrphanedOnHost (host=${hostMessage.host.name}, reason=${msg.reason})"
                        }
                        registry.set(msg.documentId, SyncStatus.OrphanedOnHost)
                    }
                    is NetworkMessage.SaveResult -> {
                        if (msg.documentId.isEmpty()) return@collect
                        val isOrphan =
                            !msg.success &&
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
