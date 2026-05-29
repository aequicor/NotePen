package ru.kyamshanov.notepen.library.impl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import ru.kyamshanov.notepen.library.api.Library
import ru.kyamshanov.notepen.library.api.LibraryBackend
import ru.kyamshanov.notepen.library.api.LibraryBackendKind
import ru.kyamshanov.notepen.library.api.LibraryConnection
import ru.kyamshanov.notepen.library.api.LibraryDescriptor
import ru.kyamshanov.notepen.library.api.LibraryRole
import ru.kyamshanov.notepen.sync.domain.LibraryMutationClient
import ru.kyamshanov.notepen.sync.domain.RemoteDocumentOpener
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.RemoteCatalog

/**
 * [LibraryBackend] for [LibraryConnection.PeerLan] libraries (a peer's catalog shared over LAN).
 *
 * This backend is a thin adapter over the **existing** sync infrastructure — it builds no new
 * networking. It reuses the client-side per-peer catalog cache ([catalogs], i.e.
 * `InMemoryRemoteCatalogCache.catalogs`) for listing, and the [RemoteDocumentOpener] for streaming
 * + caching a tapped document.
 *
 * Works on **both** platforms as a client: Android is client-only (it connects to a peer's LAN
 * library but never hosts one), and desktop can be both host and client.
 *
 * Connecting does **not** initiate any pairing or transport — pairing happens out of band (QR /
 * manual connect), which is what populates [catalogs]. This backend merely projects the already
 * connected peer's catalog as a [Library].
 *
 * @param catalogs client-side per-peer catalog cache shared with the rest of the sync stack.
 * @param documentOpenerProvider provider of the live [RemoteDocumentOpener]; may return `null`
 *   while the heavy sync stack is still wiring (the opener is built lazily once sync is enabled).
 * @param mutationClientProvider factory for a [LibraryMutationClient] addressed to a given peer,
 *   used when the host has granted this device the Librarian role (M5b). Returns `null` while the
 *   sync client is unavailable, or always for a read-only build (the default). Mutations then fail
 *   with a clear error / [ru.kyamshanov.notepen.library.api.NotLibrarianException].
 * @param onlinePeerIds optional reactive set of currently-online peer ids, used to derive each
 *   library's connection state.
 */
public class PeerLanLibraryBackend(
    private val catalogs: StateFlow<Map<DeviceInfo, RemoteCatalog>>,
    private val documentOpenerProvider: () -> RemoteDocumentOpener?,
    private val mutationClientProvider: (peerId: String) -> LibraryMutationClient? = { null },
    private val onlinePeerIds: Flow<Set<String>>? = null,
) : LibraryBackend {
    override val kind: LibraryBackendKind = LibraryBackendKind.PeerLan

    override suspend fun connect(
        spec: LibraryConnection,
        scope: CoroutineScope,
    ): Result<Library> =
        runCatching {
            val peer = spec.requirePeerLan()
            PeerLanLibrary(
                descriptor = peerLanDescriptor(peer.peerId, displayNameFor(peer.peerId), roleFor(peer.peerId)),
                peerId = peer.peerId,
                catalogs = catalogs,
                documentOpener = documentOpenerProvider,
                mutationClientProvider = mutationClientProvider,
                onlinePeerIds = onlinePeerIds,
                scope = scope,
            )
        }

    override suspend fun probe(spec: LibraryConnection): Result<LibraryDescriptor> =
        runCatching {
            val peer = spec.requirePeerLan()
            peerLanDescriptor(peer.peerId, displayNameFor(peer.peerId), roleFor(peer.peerId))
        }

    /** The host-advertised role for [peerId] from the cached catalog (Reader if absent). */
    private fun roleFor(peerId: String): LibraryRole =
        catalogs.value.entries
            .firstOrNull { it.key.id == peerId }
            ?.value
            ?.grantedRole
            ?.toLibraryRole()
            ?: LibraryRole.Reader

    /**
     * The peer's host display name if its catalog is already cached, else the peer id itself.
     * The catalog carries the friendly `hostName`; until it arrives we fall back to the id.
     */
    private fun displayNameFor(peerId: String): String =
        catalogs.value.entries
            .firstOrNull { it.key.id == peerId }
            ?.let { it.value.hostName.ifBlank { it.key.name.ifBlank { peerId } } }
            ?: peerId

    private fun LibraryConnection.requirePeerLan(): LibraryConnection.PeerLan =
        this as? LibraryConnection.PeerLan
            ?: error("PeerLanLibraryBackend only handles LibraryConnection.PeerLan, got ${this::class.simpleName}")
}
