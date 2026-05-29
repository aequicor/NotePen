package ru.kyamshanov.notepen.library.ui

import ru.kyamshanov.notepen.library.api.LibraryBackendKind
import ru.kyamshanov.notepen.library.api.LibraryConnectionState
import ru.kyamshanov.notepen.library.api.LibraryRole

/**
 * Immutable state of the «Источники библиотек» (LibrarySources) screen.
 *
 * @property libraries the currently connected libraries, one row each.
 * @property availablePeers LAN peers that are cached/known but not yet added as a library — the
 *   "add LAN library" picker connects one of these as a [LibraryBackendKind.PeerLan] library.
 * @property openLibraryAtStartup current value of the `openLibraryAtStartup` app setting.
 * @property serveOverLanSupported whether this platform can serve its library over LAN (desktop only;
 *   Android is client-only). Hides the "Открыть свою библиотеку" action when `false`.
 * @property serving whether serve-over-LAN has been switched on this session (drives the action label).
 * @property errorMessage a transient error to surface in a snackbar, or `null`.
 */
data class LibrarySourcesUiState(
    val libraries: List<LibrarySourceUiModel> = emptyList(),
    val availablePeers: List<AvailablePeerUiModel> = emptyList(),
    val openLibraryAtStartup: Boolean = false,
    val serveOverLanSupported: Boolean = false,
    val serving: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * A single connected library as shown on the LibrarySources screen.
 *
 * @property id stable registry id (`LibraryId.value`), used for the disconnect action.
 * @property displayName human-readable name.
 * @property kind the backend kind, driving the leading icon.
 * @property role Reader vs Librarian, driving the role badge.
 * @property connectionState live connection status, driving the status chip.
 * @property bookCount number of books currently listed by the library.
 */
data class LibrarySourceUiModel(
    val id: String,
    val displayName: String,
    val kind: LibraryBackendKind,
    val role: LibraryRole,
    val connectionState: LibraryConnectionState,
    val bookCount: Int,
)

/**
 * A LAN peer that is known (paired / cached) but not yet added as a library.
 *
 * @property peerId stable peer id ([ru.kyamshanov.notepen.sync.domain.model.DeviceInfo.id]).
 * @property displayName friendly host name.
 * @property host last-known network host of the peer, or `null` to rediscover via mDNS.
 */
data class AvailablePeerUiModel(
    val peerId: String,
    val displayName: String,
    val host: String?,
)
