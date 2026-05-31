package ru.kyamshanov.notepen.library.ui

import ru.kyamshanov.notepen.library.api.LibraryBackendKind
import ru.kyamshanov.notepen.library.api.LibraryConnectionState
import ru.kyamshanov.notepen.library.api.LibraryRole
import ru.kyamshanov.notepen.qrconnect.domain.port.QrMatrix

/**
 * Immutable state of the «Источники библиотек» (LibrarySources) screen.
 *
 * @property libraries the currently connected libraries, one row each.
 * @property availablePeers LAN peers that are cached/known but not yet added as a library — the
 *   "add LAN library" picker connects one of these as a [LibraryBackendKind.PeerLan] library.
 * @property openLibraryAtStartup current value of the `openLibraryAtStartup` app setting.
 * @property googleDriveSupported whether a Google sign-in path is wired (an OAuth client is
 *   configured); hides the "Google Drive" add option when `false`.
 * @property googleDevicePrompt the active Google device-flow prompt (user code + URL) to display
 *   while waiting for the user to authorize, or `null` when no sign-in is in progress.
 * @property errorMessage a transient error to surface in a snackbar, or `null`.
 */
data class LibrarySourcesUiState(
    val libraries: List<LibrarySourceUiModel> = emptyList(),
    val availablePeers: List<AvailablePeerUiModel> = emptyList(),
    val openLibraryAtStartup: Boolean = false,
    val googleDriveSupported: Boolean = false,
    val googleDevicePrompt: GoogleDeviceCodeUiModel? = null,
    val errorMessage: String? = null,
)

/**
 * The user-facing data of an in-progress Google device-flow sign-in.
 *
 * @property userCode the code the user types at [verificationUri].
 * @property verificationUri the URL the user opens in any browser to authorize.
 */
data class GoogleDeviceCodeUiModel(
    val userCode: String,
    val verificationUri: String,
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
 * A scannable per-library share QR produced by the host for a single local library.
 *
 * @property payload the canonical `notepen://pair?…&l=<libraryId>&ln=<libraryName>` string, also
 *   offered as copyable text so a desktop client can paste it (no camera).
 * @property matrix the QR matrix to render for a scanning client.
 * @property libraryName the shared library's display name, for the dialog heading.
 */
data class SharedLibraryQr(
    val payload: String,
    val matrix: QrMatrix,
    val libraryName: String,
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
