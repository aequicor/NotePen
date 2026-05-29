package ru.kyamshanov.notepen.library.ui

import com.arkivanov.essenty.lifecycle.Lifecycle
import com.arkivanov.essenty.lifecycle.coroutines.withLifecycle
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.appsettings.domain.port.AppSettingsRepository
import ru.kyamshanov.notepen.library.api.Library
import ru.kyamshanov.notepen.library.api.LibraryConnection
import ru.kyamshanov.notepen.library.api.LibraryId
import ru.kyamshanov.notepen.library.api.LibraryRegistry
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.RemoteCatalog

/**
 * ViewModel of the «Источники библиотек» (LibrarySources) screen.
 *
 * Reactively projects [LibraryRegistry.libraries] into the screen state and drives connect /
 * disconnect, the `openLibraryAtStartup` toggle ([AppSettingsRepository]) and (desktop only) the
 * serve-over-LAN action. Adding a LAN library reuses the already-paired peer catalog cache — the
 * pairing itself happens out of band through the sync QR / manual flow, which is what populates
 * [catalogsFlow].
 *
 * @param lifecycle Decompose lifecycle; scopes the hot subscriptions.
 * @param registry the central library registry, connected/disconnected through here.
 * @param settingsRepository read/write of the `openLibraryAtStartup` app setting.
 * @param catalogsFlow shared peer→catalog cache, used to list LAN peers available to add and to
 *   resolve friendly display names; `null` when sync is not wired.
 * @param onlinePeerIdsFlow reactive set of online peer ids; refines the "available peers" list.
 * @param onServeOverLan platform action enabling serve-over-LAN (desktop: `SyncRuntime.enable()`);
 *   `null` on platforms that cannot host (Android, client-only) — the action is then hidden.
 */
@Suppress("LongParameterList")
class LibrarySourcesViewModel(
    lifecycle: Lifecycle,
    private val registry: LibraryRegistry,
    private val settingsRepository: AppSettingsRepository,
    private val catalogsFlow: Flow<Map<DeviceInfo, RemoteCatalog>>?,
    private val onlinePeerIdsFlow: Flow<Set<String>>?,
    private val onServeOverLan: (() -> Unit)?,
) {
    private val logger = KotlinLogging.logger {}

    private val scope = CoroutineScope(Dispatchers.Main.immediate).withLifecycle(lifecycle)

    private val _state =
        MutableStateFlow(
            LibrarySourcesUiState(serveOverLanSupported = onServeOverLan != null),
        )

    /** Screen state (read-only). */
    val state: StateFlow<LibrarySourcesUiState> = _state.asStateFlow()

    init {
        // Project the connected libraries (and each one's live connection-state + book count)
        // into rows. flatMapLatest re-subscribes whenever the library *set* changes.
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        scope.launch {
            registry.libraries
                .flatMapLatest { libs -> librariesAsRows(libs) }
                .collect { rows -> _state.update { it.copy(libraries = rows) } }
        }
        // Available LAN peers = known peers not already added as a library.
        if (catalogsFlow != null) {
            scope.launch {
                val onlineFlow = onlinePeerIdsFlow ?: flowOf(null)
                combine(catalogsFlow, onlineFlow, registry.libraries) { catalogs, online, libs ->
                    availablePeers(catalogs, online, libs)
                }.collect { peers -> _state.update { it.copy(availablePeers = peers) } }
            }
        }
        scope.launch {
            settingsRepository.settings.collect { settings ->
                _state.update { it.copy(openLibraryAtStartup = settings.openLibraryAtStartup) }
            }
        }
    }

    /** Connects the LAN peer [peerId] (last seen at [host]) as a read-only [LibraryConnection.PeerLan]. */
    fun addLanLibrary(
        peerId: String,
        host: String?,
    ) {
        scope.launch {
            registry.connect(LibraryConnection.PeerLan(peerId = peerId, host = host))
                .onFailure { e ->
                    logger.warn(e) { "addLanLibrary failed: ${e::class.simpleName}" }
                    _state.update { it.copy(errorMessage = "Не удалось подключить библиотеку") }
                }
        }
    }

    /** Connects a local-folder library rooted at [rootPath] (desktop only). */
    fun addLocalLibrary(rootPath: String) {
        if (rootPath.isBlank()) return
        scope.launch {
            registry.connect(LibraryConnection.Local(rootPath = rootPath))
                .onFailure { e ->
                    logger.warn(e) { "addLocalLibrary failed: ${e::class.simpleName}" }
                    _state.update { it.copy(errorMessage = "Не удалось открыть папку") }
                }
        }
    }

    /** Disconnects (and de-persists) the library with the given registry id. */
    fun disconnect(libraryId: String) {
        scope.launch { registry.disconnect(LibraryId(libraryId)) }
    }

    /** Persists the `openLibraryAtStartup` flag. */
    fun setOpenLibraryAtStartup(enabled: Boolean) {
        scope.launch {
            settingsRepository.save(settingsRepository.settings.value.copy(openLibraryAtStartup = enabled))
        }
    }

    /**
     * Enables serve-over-LAN on supported platforms (desktop). No-op (and the action is hidden) where
     * unsupported. The transport itself is owned by the platform layer; this only flips it on.
     */
    fun openMyLibrary() {
        val serve = onServeOverLan ?: return
        serve()
        _state.update { it.copy(serving = true) }
    }

    /** Resets the one-shot error message after the UI shows it. */
    fun onErrorShown() {
        _state.update { it.copy(errorMessage = null) }
    }

    /**
     * Combines each library's `connectionState` + `books` into a row, so the screen reflects status
     * and book counts live. Empty library set yields an empty list immediately.
     */
    private fun librariesAsRows(libraries: List<Library>): Flow<List<LibrarySourceUiModel>> =
        if (libraries.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(
                libraries.map { lib ->
                    combine(lib.connectionState, lib.books) { connState, books ->
                        LibrarySourceUiModel(
                            id = lib.descriptor.id.value,
                            displayName = lib.descriptor.displayName,
                            kind = lib.descriptor.kind,
                            role = lib.descriptor.role,
                            connectionState = connState,
                            bookCount = books.size,
                        )
                    }
                },
            ) { rows -> rows.toList() }
        }

    private fun availablePeers(
        catalogs: Map<DeviceInfo, RemoteCatalog>,
        online: Set<String>?,
        libraries: List<Library>,
    ): List<AvailablePeerUiModel> {
        val connectedPeerIds =
            libraries
                .mapNotNull { lib -> peerIdOfLibrary(lib.descriptor.id.value) }
                .toSet()
        return catalogs.entries
            .filter { (device, _) -> device.id !in connectedPeerIds }
            .filter { (device, _) -> online == null || device.id in online }
            .map { (device, catalog) ->
                AvailablePeerUiModel(
                    peerId = device.id,
                    displayName = catalog.hostName.ifBlank { device.name.ifBlank { device.id } },
                    host = device.host.ifBlank { null },
                )
            }
            .sortedBy { it.displayName.lowercase() }
    }

    /** Extracts the peer id from a PeerLan library id (`peerlan:<peerId>`), or `null` for other kinds. */
    private fun peerIdOfLibrary(libraryId: String): String? =
        libraryId.takeIf { it.startsWith(PEER_LAN_ID_PREFIX) }?.removePrefix(PEER_LAN_ID_PREFIX)

    private companion object {
        const val PEER_LAN_ID_PREFIX = "peerlan:"
    }
}
