package ru.kyamshanov.notepen.library.ui

import com.arkivanov.decompose.ComponentContext
import kotlinx.coroutines.flow.Flow
import ru.kyamshanov.notepen.LibrarySourcesComponent
import ru.kyamshanov.notepen.appsettings.domain.port.AppSettingsRepository
import ru.kyamshanov.notepen.library.api.LibraryRegistry
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.RemoteCatalog

/**
 * Decompose component of the «Источники библиотек» (LibrarySources) screen.
 *
 * Hosts the [LibrarySourcesViewModel] and binds it to the Decompose lifecycle. It is the `:common`
 * realization of the [LibrarySourcesComponent] contract from `:shared`, so [DefaultRootComponent]
 * can navigate to it without `:shared` depending on `:library` or Compose.
 *
 * @param componentContext Decompose context.
 * @param registry the central [LibraryRegistry] (connect/disconnect/list).
 * @param settingsRepository read/write of the `openLibraryAtStartup` app setting.
 * @param catalogsFlow shared peer→catalog cache for listing addable LAN peers; `null` when sync off.
 * @param onlinePeerIdsFlow online-peer set, refining the addable-peer list.
 * @param onServeOverLan platform serve-over-LAN action (desktop); `null` hides it (Android).
 * @param onPickLocalFolder platform folder picker for adding a local-folder library (desktop);
 *   `null` hides the "local folder" add option (Android — client-only, no local backend).
 * @param googleDriveAuthorizer drives the Google device-flow sign-in for a Drive library; `null`
 *   hides the "Google Drive" add option when no OAuth client is configured.
 * @param connectLibraryByQr connects a LAN library from a pasted `notepen://pair?…` QR payload —
 *   self-contained (enables sync, dials the host directly, registers the [PeerLan][
 *   ru.kyamshanov.notepen.library.api.LibraryConnection.PeerLan] library), returning the connected
 *   library's name. Bypasses mDNS, so it works under VPN / AP isolation. `null` hides the action.
 * @param shareLibraryByQr builds a per-library share QR for a local library this host serves (the
 *   desktop host only); `null` hides the per-row "Share via QR" action (e.g. Android, client-only).
 * @param onBack navigation back to the main screen.
 */
@Suppress("LongParameterList")
class LibrarySourcesComponentImpl(
    componentContext: ComponentContext,
    registry: LibraryRegistry,
    settingsRepository: AppSettingsRepository,
    catalogsFlow: Flow<Map<DeviceInfo, RemoteCatalog>>?,
    onlinePeerIdsFlow: Flow<Set<String>>?,
    onServeOverLan: (() -> Unit)?,
    val onPickLocalFolder: (suspend () -> String?)?,
    googleDriveAuthorizer: GoogleDriveAuthorizer? = null,
    val connectLibraryByQr: (suspend (payload: String) -> Result<String>)? = null,
    val shareLibraryByQr: (suspend (libraryId: String, libraryName: String) -> SharedLibraryQr?)? = null,
    val onBack: () -> Unit,
) : LibrarySourcesComponent,
    ComponentContext by componentContext {
    val viewModel =
        LibrarySourcesViewModel(
            lifecycle = lifecycle,
            registry = registry,
            settingsRepository = settingsRepository,
            catalogsFlow = catalogsFlow,
            onlinePeerIdsFlow = onlinePeerIdsFlow,
            onServeOverLan = onServeOverLan,
            googleDriveAuthorizer = googleDriveAuthorizer,
        )

    override fun onBack() {
        onBack.invoke()
    }
}
