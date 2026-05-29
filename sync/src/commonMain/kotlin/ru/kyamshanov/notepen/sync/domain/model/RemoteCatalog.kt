package ru.kyamshanov.notepen.sync.domain.model

import kotlinx.serialization.Serializable

/**
 * The access level the **host** has granted the receiving peer over the host's
 * library, advertised inside [RemoteCatalog].
 *
 * This is the host→client half of the Librarian-over-LAN role handshake (M5b):
 * the host's per-peer write allow-set (see
 * [ru.kyamshanov.notepen.sync.domain.RemoteCatalogProvider.isLibrarian]) is the
 * authoritative gate, but the client needs to *know* its granted role to surface
 * librarian UI/capabilities and avoid sending mutations that will be rejected.
 *
 * Default-deny and wire-compatible: an older host that never sets this field
 * deserialises as [Reader], so a pre-M5b client/host pair behaves exactly as
 * before (read-only). The host always re-verifies every mutation against its own
 * allow-set; this field is advisory for the client only.
 */
@Serializable
enum class RemoteLibraryRole {
    /** Read-only: the peer may browse and open, but not mutate the host's library. */
    Reader,

    /** Librarian: the peer may add/replace (and, where supported, remove) books. */
    Librarian,
}

/**
 * Snapshot of one host's library shared with a paired peer.
 *
 * Sent as part of [NetworkMessage.RemoteCatalogResponse]. The tablet caches
 * this and renders a "Remote (<HostName>)" section on its main screen.
 *
 * Document URIs are intentionally **not** included — the tablet only learns
 * each file's [RemoteEntry.documentId], which is the only identifier it
 * needs to request streaming via [NetworkMessage.DocumentOpenRequest].
 *
 * @property hostName Display name of the host device. Free-form, used only for the section label.
 * @property recent Files in the host's "recent" list, newest first.
 * @property folders User-defined folders on the host.
 * @property folderLinks Many-to-many `(folderId, documentId)` membership.
 * @property grantedRole the access level the host has granted the receiving peer
 *   (M5b). Defaults to [RemoteLibraryRole.Reader] so older hosts (which never
 *   populate it) keep wire compatibility — the peer is treated as a reader.
 */
@Serializable
data class RemoteCatalog(
    val hostName: String,
    val recent: List<RemoteEntry>,
    val folders: List<RemoteFolder>,
    val folderLinks: List<RemoteFolderLink>,
    val grantedRole: RemoteLibraryRole = RemoteLibraryRole.Reader,
)

/**
 * One entry in the host's library.
 *
 * @property documentId Stable cross-device id (see [ru.kyamshanov.notepen.sync.domain.documentIdFromFilePath]).
 * @property displayName File name as shown on the host.
 * @property fileSize Size in bytes, or null when unknown on the host.
 * @property lastOpenedAt Epoch millis of the most recent open on the host.
 */
@Serializable
data class RemoteEntry(
    val documentId: String,
    val displayName: String,
    val fileSize: Long? = null,
    val lastOpenedAt: Long,
)

/**
 * One user-defined folder on the host.
 *
 * @property folderId Stable folder id (UUID v4) — opaque to the tablet.
 * @property name Display name.
 * @property createdAt Epoch millis of folder creation on the host.
 * @property parentFolderId id родительской папки или `null` для папки верхнего уровня.
 *           Бэк-совместимость: старый хост без поля → все папки на верхнем уровне.
 */
@Serializable
data class RemoteFolder(
    val folderId: String,
    val name: String,
    val createdAt: Long,
    val parentFolderId: String? = null,
)

/**
 * Membership link between a [RemoteFolder] and a [RemoteEntry].
 *
 * @property folderId FK → [RemoteFolder.folderId].
 * @property documentId FK → [RemoteEntry.documentId].
 * @property lastOpenedAt Epoch millis of the most recent open from inside the folder.
 */
@Serializable
data class RemoteFolderLink(
    val folderId: String,
    val documentId: String,
    val lastOpenedAt: Long,
)
