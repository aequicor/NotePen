package ru.kyamshanov.notepen.sync.domain

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.kyamshanov.notepen.mainscreen.domain.port.FolderRepository
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.model.RemoteCatalog
import ru.kyamshanov.notepen.sync.domain.model.RemoteEntry
import ru.kyamshanov.notepen.sync.domain.model.RemoteFolder
import ru.kyamshanov.notepen.sync.domain.model.RemoteFolderLink
import ru.kyamshanov.notepen.sync.domain.model.RemoteLibraryRole
import ru.kyamshanov.notepen.sync.domain.port.CatalogChangeNotifier
import ru.kyamshanov.notepen.sync.domain.port.LibrarianGrantStore
import ru.kyamshanov.notepen.sync.domain.port.LibraryManifestProvider
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
 * @param manifestProvider Source of the published library — the books a peer
 *   may see and the id→path resolution used when streaming a document.
 * @param folderRepository Source of user folders and folder/file links.
 * @param grantStore Optional durable store of the librarian write allow-set
 *   (M5b). When non-null, grants are persisted on [grantLibrarian]/[revokeLibrarian]
 *   and restored via [restoreLibrarianGrants]; when null the allow-set is
 *   in-memory only (M5a behaviour, e.g. Android which never hosts over LAN).
 */
class RemoteCatalogProvider(
    private val hostName: String,
    private val manifestProvider: LibraryManifestProvider,
    private val folderRepository: FolderRepository,
    private val grantStore: LibrarianGrantStore? = null,
) {
    private val mutex = Mutex()
    private val allowedByPeer = mutableMapOf<String, Set<String>>()
    private val uriByPeer = mutableMapOf<String, Map<String, String>>()

    // Per-peer WRITE allow-set: the ids of peers granted the Librarian role and
    // therefore permitted to mutate this host's library (add/remove/replace).
    // This is the mirror, for writes, of `allowedByPeer` (the read allow-list):
    // it is **default-deny** — a peer absent from this set may read whatever the
    // served catalog exposes but may NOT mutate anything. M5b sets this role from
    // the pairing approval UX and persists it via [grantStore]; the safe default
    // is unchanged (no peer is a librarian until explicitly granted).
    private val librarianPeerIds = mutableSetOf<String>()

    /** Resolves [documentId] for [peerId] back to the local URI, or `null` if not allowed. */
    suspend fun resolveUri(
        peerId: String,
        documentId: String,
    ): String? = mutex.withLock { uriByPeer[peerId]?.get(documentId) }

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
    suspend fun isAllowed(
        peerId: String,
        documentId: String,
    ): Boolean = mutex.withLock { allowedByPeer[peerId]?.contains(documentId) == true }

    /**
     * Grants [peerId] the Librarian role on this host — i.e. adds it to the
     * per-peer write allow-set so its library-mutation requests are honoured,
     * and (when a [grantStore] is wired) persists the grant durably so it
     * survives a host restart.
     *
     * Idempotent. Called by the pairing layer (M5b) when the operator approves a
     * peer as a librarian rather than a reader.
     */
    suspend fun grantLibrarian(peerId: String) {
        // Persist FIRST, then flip the in-memory set — so a failed durable write never
        // leaves a grant that is honoured now but silently lost on the next host
        // restart. A store failure is logged and the in-memory grant is NOT applied
        // (the operator can retry); without a store the in-memory set is the only
        // source of truth and is updated unconditionally (M5a behaviour).
        val durable =
            grantStore?.let { store ->
                runCatching { store.grant(peerId) }
                    .onFailure {
                        logger.warn { "Persisting librarian grant for $peerId failed: ${it::class.simpleName}" }
                    }.isSuccess
            } ?: true
        if (durable) mutex.withLock { librarianPeerIds.add(peerId) }
    }

    /**
     * Revokes [peerId]'s Librarian role: future mutation requests from it are
     * rejected, and the persisted grant (if any) is dropped. Idempotent. Reading
     * (the catalog allow-list) is unaffected.
     */
    suspend fun revokeLibrarian(peerId: String) {
        mutex.withLock { librarianPeerIds.remove(peerId) }
        grantStore?.let { store ->
            runCatching { store.revoke(peerId) }
                .onFailure { logger.warn { "Persisting librarian revoke for $peerId failed: ${it::class.simpleName}" } }
        }
    }

    /**
     * Re-grants every peer id persisted in [grantStore] into the in-memory write
     * allow-set. Called once on startup so durable librarian grants survive a
     * host restart. No-op when no [grantStore] is wired. Does **not** write back.
     */
    suspend fun restoreLibrarianGrants() {
        val store = grantStore ?: return
        val persisted =
            runCatching { store.load() }
                .onFailure { logger.warn { "Loading persisted librarian grants failed: ${it::class.simpleName}" } }
                .getOrDefault(emptySet())
        if (persisted.isEmpty()) return
        mutex.withLock { librarianPeerIds.addAll(persisted) }
        logger.info { "Restored ${persisted.size} persisted librarian grant(s)" }
    }

    /** Snapshot of the peer ids currently granted the Librarian role (for the connected-clients UI). */
    suspend fun librarianPeerIdsSnapshot(): Set<String> = mutex.withLock { librarianPeerIds.toSet() }

    /**
     * True if [peerId] is in the write allow-set (a granted librarian). The
     * authorisation gate for every [NetworkMessage.LibraryAddRequest] /
     * [NetworkMessage.LibraryRemoveRequest] / [NetworkMessage.LibraryReplaceRequest].
     *
     * **Default-deny**: a peer that was never explicitly granted returns `false`.
     */
    suspend fun isLibrarian(peerId: String): Boolean = mutex.withLock { peerId in librarianPeerIds }

    /**
     * Rebuilds the catalog for [peerId] (stamping its now-current
     * [RemoteCatalog.grantedRole]) and pushes it to that peer over [server].
     * Called right after a role change ([grantLibrarian]/[revokeLibrarian]) so
     * the affected client learns its new role immediately rather than only on the
     * next content change or reconnect.
     */
    suspend fun pushCatalogTo(
        server: PeerServer,
        peerId: String,
    ) {
        val catalog =
            runCatching { buildSnapshotFor(peerId) }
                .onFailure { logger.warn { "Failed to rebuild catalog for role push: ${it::class.simpleName}" } }
                .getOrNull() ?: return
        runCatching { server.send(peerId, NetworkMessage.RemoteCatalogResponse(catalog)) }
            .onFailure { logger.warn { "Role-change catalog push to $peerId failed: ${it::class.simpleName}" } }
    }

    /**
     * Subscribes [server] to incoming [NetworkMessage.RemoteCatalogRequest]s
     * and replies to the requesting peer with a freshly built snapshot.
     * Runs until [scope] is cancelled.
     */
    fun serve(
        server: PeerServer,
        scope: CoroutineScope,
    ) {
        scope.launch {
            server.incomingMessages.collect { peerMessage ->
                val msg = peerMessage.message
                if (msg !is NetworkMessage.RemoteCatalogRequest) return@collect
                val peerId = peerMessage.peer.id
                val catalog =
                    runCatching { buildSnapshotFor(peerId) }
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
    fun serve(
        client: SyncClient,
        scope: CoroutineScope,
    ) {
        scope.launch {
            client.incomingMessages.collect { hostMessage ->
                val msg = hostMessage.message
                if (msg !is NetworkMessage.RemoteCatalogRequest) return@collect
                val peerId = hostMessage.host.id
                val catalog =
                    runCatching { buildSnapshotFor(peerId) }
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
        val manifest = manifestProvider.current()
        // Resolve every book to its concrete host path once. A book that fails
        // to resolve (e.g. deleted between the walk and now) is simply dropped.
        val booksWithUri =
            manifest.books.mapNotNull { book ->
                manifestProvider.resolveAbsolutePath(book.id)?.let { uri -> book to uri }
            }
        val recent =
            booksWithUri.map { (book, _) ->
                RemoteEntry(
                    documentId = book.id.value,
                    displayName = book.displayName,
                    fileSize = book.fileSize,
                    lastOpenedAt = book.modifiedAt,
                )
            }
        val uriToDocumentId = booksWithUri.associate { (book, uri) -> uri to book.id.value }
        val modifiedByUri = booksWithUri.associate { (book, uri) -> uri to book.modifiedAt }
        val foldersDomain = folderRepository.getAll()
        val folders =
            foldersDomain.map { folder ->
                RemoteFolder(
                    folderId = folder.id,
                    name = folder.name,
                    createdAt = folder.createdAt,
                    parentFolderId = folder.parentId,
                )
            }
        val folderLinks =
            foldersDomain.flatMap { folder ->
                val uris =
                    runCatching { folderRepository.getFilesInFolder(folder.id) }
                        .getOrElse { emptyList() }
                uris.mapNotNull { uri ->
                    uriToDocumentId[uri]?.let { docId ->
                        RemoteFolderLink(
                            folderId = folder.id,
                            documentId = docId,
                            lastOpenedAt = modifiedByUri[uri] ?: 0L,
                        )
                    }
                }
            }
        val allowed = recent.map { it.documentId }.toSet()
        val docToUri = booksWithUri.associate { (book, uri) -> book.id.value to uri }
        val grantedRole =
            mutex.withLock {
                allowedByPeer[peerId] = allowed
                uriByPeer[peerId] = docToUri
                // Advertise the peer's role from the SAME authoritative write
                // allow-set the mutation handler gates on — so the client can light
                // up librarian UI/capabilities. Still re-verified on every mutation.
                if (peerId in librarianPeerIds) RemoteLibraryRole.Librarian else RemoteLibraryRole.Reader
            }
        return RemoteCatalog(
            hostName = hostName,
            recent = recent,
            folders = folders,
            folderLinks = folderLinks,
            grantedRole = grantedRole,
        )
    }
}
