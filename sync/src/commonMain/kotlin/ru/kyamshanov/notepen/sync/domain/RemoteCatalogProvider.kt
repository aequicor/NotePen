package ru.kyamshanov.notepen.sync.domain

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.mainscreen.domain.port.FileHistoryRepository
import ru.kyamshanov.notepen.mainscreen.domain.port.FolderRepository
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.model.RemoteCatalog
import ru.kyamshanov.notepen.sync.domain.model.RemoteEntry
import ru.kyamshanov.notepen.sync.domain.model.RemoteFolder
import ru.kyamshanov.notepen.sync.domain.model.RemoteFolderLink
import ru.kyamshanov.notepen.sync.domain.port.PeerServer

private val logger = KotlinLogging.logger {}

/**
 * Host-side service that builds the [RemoteCatalog] from local repositories
 * and serves it to the paired peer in response to a
 * [NetworkMessage.RemoteCatalogRequest].
 *
 * **Security model**: every time a catalog is built, the set of `documentId`s
 * it contains is captured as [allowedDocumentIds]. When Phase 3 wires the
 * on-demand fetch flow, the host MUST validate every
 * [NetworkMessage.DocumentOpenRequest] against this set — otherwise a client
 * could ask the host to read arbitrary files outside its library
 * (path traversal). Until then the field is filled but unused.
 *
 * Single-client per server is assumed (the underlying [PeerServer] already
 * rejects extra connections), so a single `Set<String>` is sufficient. When
 * multi-client support arrives, key the snapshot by peer id.
 *
 * @param hostName Display name of this host, embedded in the served catalog.
 * @param historyRepository Source of "recent" files.
 * @param folderRepository Source of user folders and folder/file links.
 */
class RemoteCatalogProvider(
    private val hostName: String,
    private val historyRepository: FileHistoryRepository,
    private val folderRepository: FolderRepository,
) {

    /**
     * `documentId`s in the most recently built catalog. Updated on every
     * [buildSnapshot] call. Phase 3 reads this to authorize document opens.
     */
    @Volatile
    var allowedDocumentIds: Set<String> = emptySet()
        private set

    /**
     * `documentId` → original host file URI mapping for the last-built catalog.
     * Used by [resolveUri] to resolve incoming
     * [NetworkMessage.DocumentOpenRequest]s into a local file path the host
     * can stream over [ru.kyamshanov.notepen.sync.infrastructure.WebSocketFileTransfer].
     */
    @Volatile
    private var documentIdToUri: Map<String, String> = emptyMap()

    /**
     * Resolves [documentId] back to the local URI on the host, or `null` if
     * the document is not part of the most recent catalog snapshot. Callers
     * MUST check this against [allowedDocumentIds] (or equivalently get a
     * non-null result here) before reading any bytes from disk.
     */
    fun resolveUri(documentId: String): String? = documentIdToUri[documentId]

    /**
     * Subscribes [server] to incoming [NetworkMessage.RemoteCatalogRequest]s
     * and replies with a freshly built snapshot. Runs until [scope] is cancelled.
     */
    fun serve(server: PeerServer, scope: CoroutineScope) {
        scope.launch {
            server.incomingMessages
                .filterIsInstance<NetworkMessage.RemoteCatalogRequest>()
                .collect {
                    val catalog = runCatching { buildSnapshot() }
                        .onFailure { logger.warn { "Failed to build catalog snapshot: ${it::class.simpleName}" } }
                        .getOrElse { RemoteCatalog(hostName, emptyList(), emptyList(), emptyList()) }
                    logger.info {
                        "Serving RemoteCatalog: ${catalog.recent.size} recents, " +
                            "${catalog.folders.size} folders, ${catalog.folderLinks.size} links"
                    }
                    server.send(NetworkMessage.RemoteCatalogResponse(catalog))
                }
        }
    }

    /**
     * Builds a fresh [RemoteCatalog] from the bound repositories and refreshes
     * [allowedDocumentIds]. Exposed so the host can also push proactive updates
     * when the local library changes (Phase 5).
     */
    suspend fun buildSnapshot(): RemoteCatalog {
        val recentFiles = historyRepository.getAll()
        val recent = recentFiles.map { file ->
            RemoteEntry(
                documentId = documentIdFromFilePath(file.uri),
                displayName = file.displayName,
                fileSize = file.fileSize,
                lastOpenedAt = file.openedAt,
            )
        }
        val uriToDocumentId = recentFiles.associate { it.uri to documentIdFromFilePath(it.uri) }
        val foldersDomain = folderRepository.getAll()
        val folders = foldersDomain.map { folder ->
            RemoteFolder(folderId = folder.id, name = folder.name, createdAt = folder.createdAt)
        }
        val folderLinks = foldersDomain.flatMap { folder ->
            val uris = runCatching { folderRepository.getFilesInFolder(folder.id) }
                .getOrElse { emptyList() }
            uris.mapNotNull { uri ->
                uriToDocumentId[uri]?.let { docId ->
                    RemoteFolderLink(
                        folderId = folder.id,
                        documentId = docId,
                        // We don't have a per-link lastOpenedAt in domain — reuse file's open time.
                        lastOpenedAt = recentFiles.firstOrNull { it.uri == uri }?.openedAt ?: 0L,
                    )
                }
            }
        }
        allowedDocumentIds = recent.map { it.documentId }.toSet()
        documentIdToUri = recentFiles.associate { documentIdFromFilePath(it.uri) to it.uri }
        return RemoteCatalog(
            hostName = hostName,
            recent = recent,
            folders = folders,
            folderLinks = folderLinks,
        )
    }
}
