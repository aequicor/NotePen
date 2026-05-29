package ru.kyamshanov.notepen.library.impl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.library.api.Library
import ru.kyamshanov.notepen.library.api.LibraryBackendKind
import ru.kyamshanov.notepen.library.api.LibraryBookId
import ru.kyamshanov.notepen.library.api.LibraryCapabilities
import ru.kyamshanov.notepen.library.api.LibraryConnectionState
import ru.kyamshanov.notepen.library.api.LibraryDescriptor
import ru.kyamshanov.notepen.library.api.LibraryEntry
import ru.kyamshanov.notepen.library.api.LibraryId
import ru.kyamshanov.notepen.library.api.LibraryRole
import ru.kyamshanov.notepen.library.api.NotLibrarianException
import ru.kyamshanov.notepen.library.api.OpenableDocument
import ru.kyamshanov.notepen.sync.domain.LibraryMutationClient
import ru.kyamshanov.notepen.sync.domain.RemoteDocumentOpener
import ru.kyamshanov.notepen.sync.domain.RemoteDocumentResult
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.RemoteCatalog
import ru.kyamshanov.notepen.sync.domain.model.RemoteEntry
import ru.kyamshanov.notepen.sync.domain.model.RemoteLibraryRole

/**
 * A read-only [Library] surfacing the shared catalog of a single LAN peer.
 *
 * The peer's catalog is observed through the existing sync client-side cache
 * ([catalogs], whose desktop/Android implementation is
 * `InMemoryRemoteCatalogCache.catalogs`): a snapshot keyed by [DeviceInfo].
 * This library filters that snapshot to the one peer identified by [peerId] and
 * maps the peer's [RemoteCatalog.recent] entries into [LibraryEntry]s.
 *
 * The user's role over a peer's library is whatever the **host advertises** in
 * [RemoteCatalog.grantedRole] (M5b): a [LibraryRole.Reader] by default, or a
 * [LibraryRole.Librarian] once the host operator approves this device as a
 * librarian. When Librarian, the mutating operations stream the book to the host
 * via [LibraryMutationClient] (the client half of the Librarian-over-LAN
 * protocol); when Reader they keep the [NotLibrarianException] default.
 *
 * The role is read at connect time and re-checked on every mutation against the
 * live catalog snapshot, so a grant pushed by the host (`pushCatalogTo`) takes
 * effect on the next mutation even without a reconnect; the immutable
 * [descriptor] / [capabilities] reflect the connect-time snapshot for the shelf
 * affordances.
 *
 * Book identity ([LibraryEntry.identity]) is left `null`: the LAN catalog wire
 * format ([RemoteEntry]) does not yet carry the content-addressed
 * [ru.kyamshanov.notepen.document.domain.model.CanonicalBookId]. It is computed
 * locally only after the file is materialized via [open].
 *
 * @param descriptor immutable description of this library (id derived from the peer id; its
 *   [LibraryDescriptor.role] is the connect-time advertised role).
 * @param peerId stable id of the peer whose catalog this library represents (matches
 *   [DeviceInfo.id]).
 * @param catalogs client-side per-peer catalog cache shared with the rest of the sync stack.
 * @param documentOpener provider of the live [RemoteDocumentOpener]; returns `null` while the
 *   heavy sync stack is still wiring (the opener is built lazily once sync is enabled).
 * @param mutationClientProvider factory for the [LibraryMutationClient] addressed to this peer;
 *   returns `null` when the sync client is not ready (mutations then fail with a clear error).
 *   Reader-role libraries never call it.
 * @param onlinePeerIds optional reactive set of currently-online peer ids, used to derive
 *   [connectionState]; when `null`, connectivity is inferred from catalog presence (the LAN
 *   catalog cache is intentionally not cleared on peer departure, so this fallback is coarse).
 * @param scope long-lived scope keeping [books] / [connectionState] mirrored for the
 *   lifetime of the connection.
 */
internal class PeerLanLibrary(
    override val descriptor: LibraryDescriptor,
    private val peerId: String,
    private val catalogs: StateFlow<Map<DeviceInfo, RemoteCatalog>>,
    private val documentOpener: () -> RemoteDocumentOpener?,
    private val mutationClientProvider: (peerId: String) -> LibraryMutationClient? = { null },
    onlinePeerIds: Flow<Set<String>>? = null,
    scope: CoroutineScope,
) : Library {
    override val capabilities: LibraryCapabilities = LibraryCapabilities.fromRole(descriptor.role)

    // Latest online-peer snapshot, fed by the optional onlinePeerIds flow. Null until the first
    // emission (or forever, if no flow was supplied) → connectionState falls back to catalog presence.
    private var onlineSnapshot: Set<String>? = null

    private val booksState = MutableStateFlow(catalogFor(catalogs.value).toLibraryEntries())
    override val books: StateFlow<List<LibraryEntry>> = booksState.asStateFlow()

    private val connectionStateFlow =
        MutableStateFlow(connectionStateFor(catalogs.value, onlineSnapshot))
    override val connectionState: StateFlow<LibraryConnectionState> = connectionStateFlow.asStateFlow()

    init {
        scope.launch {
            catalogs.collect { snapshot ->
                booksState.value = catalogFor(snapshot).toLibraryEntries()
                connectionStateFlow.value = connectionStateFor(snapshot, onlineSnapshot)
            }
        }
        if (onlinePeerIds != null) {
            scope.launch {
                onlinePeerIds.collect { online ->
                    onlineSnapshot = online
                    connectionStateFlow.value = connectionStateFor(catalogs.value, online)
                }
            }
        }
    }

    // The catalog is pushed reactively by the host (RemoteCatalogChanged → re-request); there is no
    // separate pull endpoint to invoke, so refresh is a no-op beyond keeping the contract.
    override suspend fun refresh() = Unit

    override suspend fun open(id: LibraryBookId): Result<OpenableDocument> =
        runCatching {
            val opener =
                documentOpener()
                    ?: error("Sync stack not ready: no RemoteDocumentOpener for peer '$peerId'")
            val displayName =
                catalogFor(catalogs.value)?.recent?.firstOrNull { it.documentId == id.value }?.displayName
            when (val result = opener.open(documentId = id.value, displayName = displayName)) {
                is RemoteDocumentResult.Success ->
                    OpenableDocument(
                        localPath = result.localPath,
                        // The LAN catalog does not carry the canonical id; it is computed locally
                        // once the file lands. Left null here (see class KDoc).
                        identity = null,
                        // A peer's library is read-only for the Reader role.
                        readOnly = true,
                    )
                is RemoteDocumentResult.NotFound ->
                    error("Document '${id.value}' not found on peer '$peerId': ${result.reason}")
                is RemoteDocumentResult.Timeout ->
                    error("Timed out fetching document '${id.value}' from peer '$peerId'")
                is RemoteDocumentResult.Failure -> throw result.cause
            }
        }

    override suspend fun addBook(src: String): Result<LibraryEntry> =
        withMutationClient { client ->
            val displayName = src.substringAfterLast('/').substringAfterLast('\\')
            client.addBook(targetLibraryId = "", localPath = src, displayName = displayName)
                .map { outcome ->
                    LibraryEntry(
                        libraryBookId = LibraryBookId(outcome.newLibraryBookId ?: src),
                        displayName = displayName,
                    )
                }
        }

    override suspend fun replaceBook(
        id: LibraryBookId,
        src: String,
    ): Result<LibraryEntry> =
        withMutationClient { client ->
            val displayName = src.substringAfterLast('/').substringAfterLast('\\')
            client.replaceBook(
                targetLibraryId = "",
                libraryBookId = id.value,
                localPath = src,
                displayName = displayName,
            ).map { outcome ->
                LibraryEntry(
                    libraryBookId = LibraryBookId(outcome.newLibraryBookId ?: id.value),
                    displayName = displayName,
                )
            }
        }

    override suspend fun removeBook(id: LibraryBookId): Result<Unit> =
        withMutationClient { client ->
            // The host's local-folder backend reports removal as unsupported today;
            // that surfaces here as a Result.failure with the host's reason.
            client.removeBook(targetLibraryId = "", libraryBookId = id.value).map { }
        }

    /**
     * Runs [block] with a [LibraryMutationClient] when this device is the live
     * Librarian of the peer's library, else fails:
     *  - a Reader role throws [NotLibrarianException] (the API default contract);
     *  - a missing client (sync not ready) is a clear [Result.failure].
     */
    private suspend fun <T> withMutationClient(block: suspend (LibraryMutationClient) -> Result<T>): Result<T> =
        when {
            currentRole() != RemoteLibraryRole.Librarian -> Result.failure(NotLibrarianException())
            else ->
                when (val client = mutationClientProvider(peerId)) {
                    null -> Result.failure(IllegalStateException("Sync client not ready for peer '$peerId'"))
                    else -> runCatching { block(client).getOrThrow() }
                }
        }

    /** This device's currently-advertised role over the peer's library (Reader if unknown). */
    private fun currentRole(): RemoteLibraryRole = catalogFor(catalogs.value)?.grantedRole ?: RemoteLibraryRole.Reader

    /** The catalog of this library's peer in [snapshot], or `null` if the peer is absent. */
    private fun catalogFor(snapshot: Map<DeviceInfo, RemoteCatalog>): RemoteCatalog? =
        snapshot.entries.firstOrNull { it.key.id == peerId }?.value

    private fun connectionStateFor(
        snapshot: Map<DeviceInfo, RemoteCatalog>,
        online: Set<String>?,
    ): LibraryConnectionState =
        when {
            // When the online-peer set is known, it is the authoritative link signal.
            online != null -> if (peerId in online) LibraryConnectionState.Connected else LibraryConnectionState.Disconnected
            // Fallback: a cached catalog implies we have heard from the peer.
            catalogFor(snapshot) != null -> LibraryConnectionState.Connected
            else -> LibraryConnectionState.Disconnected
        }

    companion object {
        /** Builds a [LibraryId] for the LAN library hosted by [peerId]. */
        fun idForPeer(peerId: String): LibraryId = LibraryId("peerlan:$peerId")
    }
}

/** Maps a peer's catalog entries to [LibraryEntry]s (identity deferred until local materialization). */
private fun RemoteCatalog?.toLibraryEntries(): List<LibraryEntry> = this?.recent?.map(RemoteEntry::toLibraryEntry).orEmpty()

private fun RemoteEntry.toLibraryEntry(): LibraryEntry =
    LibraryEntry(
        libraryBookId = LibraryBookId(documentId),
        displayName = displayName,
        sizeBytes = fileSize,
        modifiedAt = lastOpenedAt,
        // The LAN catalog wire format does not carry the canonical id yet.
        identity = null,
    )

/**
 * Builds the [LibraryDescriptor] for a peer's LAN library.
 *
 * @param peerId stable id of the peer hosting the library.
 * @param displayName label shown in the UI; defaults to the peer id when no friendlier name is known.
 * @param role the access level the peer's host advertised for this device (M5b); defaults to
 *   [LibraryRole.Reader] (the safe default before any catalog / grant arrives).
 */
internal fun peerLanDescriptor(
    peerId: String,
    displayName: String,
    role: LibraryRole = LibraryRole.Reader,
): LibraryDescriptor =
    LibraryDescriptor(
        id = PeerLanLibrary.idForPeer(peerId),
        displayName = displayName,
        kind = LibraryBackendKind.PeerLan,
        role = role,
    )

/** Maps the host-advertised [RemoteLibraryRole] to the library-layer [LibraryRole]. */
internal fun RemoteLibraryRole.toLibraryRole(): LibraryRole =
    when (this) {
        RemoteLibraryRole.Reader -> LibraryRole.Reader
        RemoteLibraryRole.Librarian -> LibraryRole.Librarian
    }
