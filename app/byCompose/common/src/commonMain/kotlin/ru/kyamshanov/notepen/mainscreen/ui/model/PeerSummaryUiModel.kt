package ru.kyamshanov.notepen.mainscreen.ui.model

/**
 * One connected peer (host *or* client) as shown in the
 * "Подключённые устройства" section of the main screen.
 *
 * The list is derived from the per-peer
 * [ru.kyamshanov.notepen.sync.domain.port.RemoteCatalogCache] entries.
 * Tapping a tile pushes a [NavigationTarget.PeerCatalog] for drill-down.
 *
 * @property peerId Stable peer id (`DeviceInfo.id`) used to address the peer
 *           over the wire and look up its catalog.
 * @property displayName Peer's display name as it announced on pairing.
 * @property itemCount Total tiles shown when the user drills in
 *           (recent files + folders) — surfaced on the card subtitle.
 * @property isOnline true когда пир сейчас в `connectedHosts`/`connectedPeers`.
 *           Если false — плитка остаётся видимой, но помечена как «не в сети».
 */
data class PeerSummaryUiModel(
    val peerId: String,
    val displayName: String,
    val itemCount: Int,
    val isOnline: Boolean,
)
