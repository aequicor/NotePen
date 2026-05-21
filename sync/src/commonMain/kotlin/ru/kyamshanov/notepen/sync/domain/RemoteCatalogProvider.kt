package ru.kyamshanov.notepen.sync.domain

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.kyamshanov.notepen.mainscreen.domain.port.FileHistoryRepository
import ru.kyamshanov.notepen.mainscreen.domain.port.FolderRepository
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.model.RemoteCatalog
import ru.kyamshanov.notepen.sync.domain.model.RemoteEntry
import ru.kyamshanov.notepen.sync.domain.model.RemoteFolder
import ru.kyamshanov.notepen.sync.domain.model.RemoteFolderLink
import ru.kyamshanov.notepen.sync.domain.port.CatalogChangeNotifier
import ru.kyamshanov.notepen.sync.domain.port.PeerServer
import ru.kyamshanov.notepen.sync.domain.port.SyncClient

private val logger = KotlinLogging.logger {}

/** Окно коалесинга всплеска сигналов «каталог изменился» перед рассылкой пирам. */
private const val CATALOG_BROADCAST_DEBOUNCE_MS = 500L

/**
 * Host-side service that builds the [RemoteCatalog] from local repositories
 * and serves it to paired peers in response to a
 * [NetworkMessage.RemoteCatalogRequest].
 *
 * **Security model**: every time a catalog is served to a peer, the set of
 * `documentId`s included is captured as that peer's allow-list. Subsequent
 * [NetworkMessage.DocumentOpenRequest]s are validated via [isAllowed] —
 * otherwise a client could ask the host to read arbitrary files outside its
 * library (path traversal).
 *
 * Multi-client safe: each peer has its own allow-list and uri map, keyed by
 * [ru.kyamshanov.notepen.sync.domain.model.DeviceInfo.id].
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

    private val mutex = Mutex()
    private val allowedByPeer = mutableMapOf<String, Set<String>>()
    private val uriByPeer = mutableMapOf<String, Map<String, String>>()

    /** Resolves [documentId] for [peerId] back to the local URI, or `null` if not allowed. */
    suspend fun resolveUri(peerId: String, documentId: String): String? =
        mutex.withLock { uriByPeer[peerId]?.get(documentId) }

    /**
     * Resolves [documentId] back to the local URI using **any** peer's map.
     * Intended for peer-agnostic host-side caches (e.g. `HostAnnotationProjection`)
     * that don't know which client originally requested the document — the URI
     * itself is identical across peers, the per-peer maps only differ in *which*
     * documents each peer is authorised to see.
     */
    suspend fun resolveUri(documentId: String): String? =
        mutex.withLock {
            uriByPeer.values.firstNotNullOfOrNull { it[documentId] }
        }

    /** True if [peerId] has been served a catalog containing [documentId]. */
    suspend fun isAllowed(peerId: String, documentId: String): Boolean =
        mutex.withLock { allowedByPeer[peerId]?.contains(documentId) == true }

    /**
     * Subscribes [server] to incoming [NetworkMessage.RemoteCatalogRequest]s
     * and replies to the requesting peer with a freshly built snapshot.
     * Runs until [scope] is cancelled.
     */
    fun serve(server: PeerServer, scope: CoroutineScope) {
        scope.launch {
            server.incomingMessages.collect { peerMessage ->
                val msg = peerMessage.message
                if (msg !is NetworkMessage.RemoteCatalogRequest) return@collect
                val peerId = peerMessage.peer.id
                val catalog = runCatching { buildSnapshotFor(peerId) }
                    .onFailure { logger.warn { "Failed to build catalog snapshot: ${it::class.simpleName}" } }
                    .getOrElse { RemoteCatalog(hostName, emptyList(), emptyList(), emptyList()) }
                logger.info {
                    "Serving RemoteCatalog to ${peerMessage.peer.name}: ${catalog.recent.size} recents, " +
                        "${catalog.folders.size} folders, ${catalog.folderLinks.size} links"
                }
                server.send(peerId, NetworkMessage.RemoteCatalogResponse(catalog))
            }
        }
    }

    /**
     * Подписывается на [notifier] и при каждом сигнале о смене локального
     * каталога рассылает [NetworkMessage.RemoteCatalogChanged] всем
     * подключённым в данный момент пирам (через `server` и/или `client`).
     *
     * Сами снапшоты пир тянет уже через `RemoteCatalogRequest` — здесь только
     * пуш-сигнал «обнови, что у тебя есть про меня». Если активного транспорта
     * нет — broadcast тихо проходит мимо (нечего рассылать).
     */
    fun broadcastChanges(
        notifier: CatalogChangeNotifier,
        server: PeerServer? = null,
        client: SyncClient? = null,
        scope: CoroutineScope,
    ) {
        require(server != null || client != null) {
            "broadcastChanges: at least one of server/client must be provided"
        }
        scope.launch {
            // Коалесинг: серии мутаций (миграция истории на старте, частые
            // автосейвы) порождали десятки RemoteCatalogChanged подряд, и каждый
            // пир переспрашивал каталог. debounce схлопывает всплеск в один сигнал.
            @OptIn(FlowPreview::class)
            notifier.changes.debounce(CATALOG_BROADCAST_DEBOUNCE_MS).collect {
                logger.info { "Local catalog changed — broadcasting RemoteCatalogChanged" }
                if (server != null) {
                    runCatching { server.broadcast(NetworkMessage.RemoteCatalogChanged) }
                        .onFailure {
                            logger.warn { "RemoteCatalogChanged broadcast via server failed: ${it::class.simpleName}" }
                        }
                }
                if (client != null) {
                    runCatching { client.broadcast(NetworkMessage.RemoteCatalogChanged) }
                        .onFailure {
                            logger.warn { "RemoteCatalogChanged broadcast via client failed: ${it::class.simpleName}" }
                        }
                }
            }
        }
    }

    /**
     * Mirror of [serve] for the client side. When this device acts as a
     * [SyncClient] and a remote host asks for our local library (the host's
     * own "peers list" UI), we reply over the same channel. Reuses the same
     * per-peer allow-list / uri map keyed by host id.
     */
    fun serve(client: SyncClient, scope: CoroutineScope) {
        scope.launch {
            client.incomingMessages.collect { hostMessage ->
                val msg = hostMessage.message
                if (msg !is NetworkMessage.RemoteCatalogRequest) return@collect
                val peerId = hostMessage.host.id
                val catalog = runCatching { buildSnapshotFor(peerId) }
                    .onFailure { logger.warn { "Failed to build catalog snapshot: ${it::class.simpleName}" } }
                    .getOrElse { RemoteCatalog(hostName, emptyList(), emptyList(), emptyList()) }
                logger.info {
                    "Serving RemoteCatalog to host ${hostMessage.host.name}: ${catalog.recent.size} recents, " +
                        "${catalog.folders.size} folders, ${catalog.folderLinks.size} links"
                }
                client.send(peerId, NetworkMessage.RemoteCatalogResponse(catalog))
            }
        }
    }

    /**
     * Builds a fresh [RemoteCatalog] from the bound repositories and registers
     * the per-peer allow-list / uri map for [peerId]. Exposed so the host can
     * push proactive updates as well (Phase 5).
     */
    suspend fun buildSnapshotFor(peerId: String): RemoteCatalog {
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
            RemoteFolder(
                folderId = folder.id,
                name = folder.name,
                createdAt = folder.createdAt,
                parentFolderId = folder.parentId,
            )
        }
        val folderLinks = foldersDomain.flatMap { folder ->
            val uris = runCatching { folderRepository.getFilesInFolder(folder.id) }
                .getOrElse { emptyList() }
            uris.mapNotNull { uri ->
                uriToDocumentId[uri]?.let { docId ->
                    RemoteFolderLink(
                        folderId = folder.id,
                        documentId = docId,
                        lastOpenedAt = recentFiles.firstOrNull { it.uri == uri }?.openedAt ?: 0L,
                    )
                }
            }
        }
        val allowed = recent.map { it.documentId }.toSet()
        val docToUri = recentFiles.associate { documentIdFromFilePath(it.uri) to it.uri }
        mutex.withLock {
            allowedByPeer[peerId] = allowed
            uriByPeer[peerId] = docToUri
        }
        return RemoteCatalog(
            hostName = hostName,
            recent = recent,
            folders = folders,
            folderLinks = folderLinks,
        )
    }
}
