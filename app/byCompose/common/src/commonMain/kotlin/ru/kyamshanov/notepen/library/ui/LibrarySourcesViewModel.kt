package ru.kyamshanov.notepen.library.ui

import com.arkivanov.essenty.lifecycle.Lifecycle
import com.arkivanov.essenty.lifecycle.coroutines.withLifecycle
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import ru.kyamshanov.notepen.library.api.CloudProviderIds
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
 * disconnect and the `openLibraryAtStartup` toggle ([AppSettingsRepository]). Adding a LAN library
 * reuses the already-paired peer catalog cache — the pairing itself happens out of band through the
 * sync QR / manual flow, which is what populates [catalogsFlow].
 *
 * @param lifecycle Decompose lifecycle; scopes the hot subscriptions.
 * @param registry the central library registry, connected/disconnected through here.
 * @param settingsRepository read/write of the `openLibraryAtStartup` app setting.
 * @param catalogsFlow shared peer→catalog cache, used to list LAN peers available to add and to
 *   resolve friendly display names; `null` when sync is not wired.
 * @param onlinePeerIdsFlow reactive set of online peer ids; refines the "available peers" list.
 * @param googleDriveAuthorizer drives the Google device-flow sign-in for a Drive library; `null`
 *   when no OAuth client is configured — the "Google Drive" add option is then hidden.
 */
@Suppress("LongParameterList")
class LibrarySourcesViewModel(
    lifecycle: Lifecycle,
    private val registry: LibraryRegistry,
    private val settingsRepository: AppSettingsRepository,
    private val catalogsFlow: Flow<Map<DeviceInfo, RemoteCatalog>>?,
    private val onlinePeerIdsFlow: Flow<Set<String>>?,
    private val googleDriveAuthorizer: GoogleDriveAuthorizer? = null,
) {
    private val logger = KotlinLogging.logger {}

    private val scope = CoroutineScope(Dispatchers.Main.immediate).withLifecycle(lifecycle)

    private val _state =
        MutableStateFlow(
            LibrarySourcesUiState(
                googleDriveSupported = googleDriveAuthorizer != null,
            ),
        )

    /** The in-flight Google sign-in, so a new attempt (or cancel) supersedes the previous one. */
    private var googleSignInJob: Job? = null

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

    /**
     * Connects a GitHub library reading the `owner/name` repo's `books/` folder. A non-blank [token]
     * (PAT or OAuth token) grants the Librarian role (upload); a blank token connects read-only.
     * The connection (token included) persists via the registry's [LibraryConnection] store.
     */
    fun addGitHubLibrary(
        repo: String,
        token: String,
    ) {
        val slug = repo.trim().trim('/')
        if (!slug.contains('/')) {
            _state.update { it.copy(errorMessage = "Укажите репозиторий в формате owner/name") }
            return
        }
        scope.launch {
            registry.connect(LibraryConnection.GitHub(repo = slug, token = token.trim().ifBlank { null }))
                .onFailure { e ->
                    logger.warn(e) { "addGitHubLibrary failed: ${e::class.simpleName}" }
                    _state.update { it.copy(errorMessage = "Не удалось подключить GitHub-библиотеку") }
                }
        }
    }

    /**
     * Connects a Google Drive library reading the shared folder [folderId] as a shelf. Runs the
     * Google OAuth device flow first: the user code + verification URL surface via
     * [LibrarySourcesUiState.googleDevicePrompt] while polling, then the resulting refresh token (and
     * its scope, which sets the Reader/Librarian role) persists in the connection spec. A read-only
     * scope yields a Reader; a write scope a Librarian.
     */
    fun addGoogleDriveLibrary(folderId: String) {
        val authorizer = googleDriveAuthorizer
        val id = folderId.trim()
        if (id.isBlank()) {
            _state.update { it.copy(errorMessage = "Укажите id папки Google Drive") }
            return
        }
        if (authorizer == null) {
            _state.update { it.copy(errorMessage = "Вход через Google не настроен") }
            return
        }
        googleSignInJob?.cancel()
        googleSignInJob =
            scope.launch {
                authorizer
                    .authorize { code ->
                        _state.update {
                            it.copy(
                                googleDevicePrompt =
                                    GoogleDeviceCodeUiModel(userCode = code.userCode, verificationUri = code.verificationUri),
                            )
                        }
                    }.onSuccess { auth ->
                        registry
                            .connect(
                                LibraryConnection.Cloud(
                                    providerId = CloudProviderIds.GOOGLE_DRIVE,
                                    accountId = id,
                                    refreshToken = auth.refreshToken,
                                    scope = auth.scope,
                                ),
                            ).onFailure { e ->
                                logger.warn(e) { "addGoogleDriveLibrary connect failed: ${e::class.simpleName}" }
                                _state.update { it.copy(errorMessage = "Не удалось подключить Google Drive") }
                            }
                        _state.update { it.copy(googleDevicePrompt = null) }
                    }.onFailure { e ->
                        logger.warn(e) { "addGoogleDriveLibrary sign-in failed: ${e::class.simpleName}" }
                        _state.update { it.copy(googleDevicePrompt = null, errorMessage = "Вход через Google не выполнен") }
                    }
            }
    }

    /** Cancels an in-progress Google sign-in and dismisses its prompt. */
    fun cancelGoogleSignIn() {
        googleSignInJob?.cancel()
        googleSignInJob = null
        _state.update { it.copy(googleDevicePrompt = null) }
    }

    /**
     * Connects a local-folder library rooted at [rootPath] with the user-given [displayName]
     * (desktop only). Both must be non-blank — the UI enforces a non-blank name, so this guard is a
     * defensive backstop.
     */
    fun addLocalLibrary(
        rootPath: String,
        displayName: String,
    ) {
        if (rootPath.isBlank() || displayName.isBlank()) return
        scope.launch {
            registry.connect(LibraryConnection.Local(rootPath = rootPath, displayName = displayName.trim()))
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
        // Peer id of a PeerLan library, or null for other kinds. The id is `peerlan:<peerId>` for a
        // whole-shelf connection or `peerlan:<peerId>:<libraryId>` for a named one; the peer id is the
        // colon-free first segment either way.
        val connectedPeerIds =
            libraries
                .mapNotNull { lib ->
                    lib.descriptor.id.value
                        .takeIf { it.startsWith(PEER_LAN_ID_PREFIX) }
                        ?.removePrefix(PEER_LAN_ID_PREFIX)
                        ?.substringBefore(':')
                }.toSet()
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

    private companion object {
        const val PEER_LAN_ID_PREFIX = "peerlan:"
    }
}
