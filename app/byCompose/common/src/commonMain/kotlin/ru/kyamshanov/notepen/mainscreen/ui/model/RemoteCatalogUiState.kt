package ru.kyamshanov.notepen.mainscreen.ui.model

/**
 * Tablet-side UI state for the "Remote (<HostName>)" section.
 *
 * Populated from
 * [ru.kyamshanov.notepen.sync.domain.port.RemoteCatalogCache] whenever the
 * host pushes (or replies with) a fresh
 * [ru.kyamshanov.notepen.sync.domain.model.RemoteCatalog]. `null` means
 * "no catalog received yet" — section hidden.
 *
 * Phase 2 renders entries read-only. Phase 3 wires the tap → `NavigationTarget.RemoteEditor(documentId)`.
 */
data class RemoteCatalogUiState(
    val hostName: String,
    val entries: List<RemoteEntryUiModel>,
    val folders: List<RemoteFolderUiModel>,
)

/**
 * One file in the host's library, as displayed in the Remote section.
 *
 * @property documentId Stable cross-device id used to address the file.
 * @property displayName File name as shown on the host.
 * @property fileSize Bytes, when known.
 * @property lastOpenedAt Epoch millis of the most recent open on the host.
 * @property pendingCount Number of local deltas buffered in
 *           [ru.kyamshanov.notepen.sync.domain.port.PendingDeltaQueue] waiting
 *           for the next reconnect. Drives the "не синхронизировано" badge.
 * @property isOrphanedOnHost True when the host has explicitly told us this
 *           `documentId` is no longer in its catalog. Pending edits will not
 *           replay until the host re-adds the file.
 */
data class RemoteEntryUiModel(
    val documentId: String,
    val displayName: String,
    val fileSize: Long?,
    val lastOpenedAt: Long,
    val pendingCount: Int = 0,
    val isOrphanedOnHost: Boolean = false,
)

/**
 * One folder in the host's library, as displayed in the Remote section.
 *
 * @property folderId Opaque host-side folder id.
 * @property name Display name.
 * @property fileCount Number of files belonging to the folder in the catalog snapshot.
 */
data class RemoteFolderUiModel(
    val folderId: String,
    val name: String,
    val fileCount: Int,
)
