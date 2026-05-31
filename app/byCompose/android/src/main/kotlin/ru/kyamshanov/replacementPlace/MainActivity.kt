package ru.kyamshanov.notepen

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.IntentCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.arkivanov.decompose.defaultComponentContext
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import ru.kyamshanov.notepen.appsettings.SettingsComponentImpl
import ru.kyamshanov.notepen.appsettings.defaultAppSettingsRepository
import ru.kyamshanov.notepen.book.AndroidEbookToPdfConverter
import ru.kyamshanov.notepen.book.EbookAwarePdfDocumentLoader
import ru.kyamshanov.notepen.document.infrastructure.CachingDocumentIdentityProvider
import ru.kyamshanov.notepen.document.infrastructure.RegistryFileIdentityCache
import ru.kyamshanov.notepen.library.api.LibraryConnection
import ru.kyamshanov.notepen.library.impl.DefaultLibraryRegistry
import ru.kyamshanov.notepen.library.impl.GitHubLibraryBackend
import ru.kyamshanov.notepen.library.impl.PeerLanLibraryBackend
import ru.kyamshanov.notepen.library.impl.google.GoogleDeviceAuthenticator
import ru.kyamshanov.notepen.library.impl.google.GoogleDriveLibraryBackend
import ru.kyamshanov.notepen.library.impl.google.GoogleDriveStore
import ru.kyamshanov.notepen.library.impl.google.GoogleOAuthConfig
import ru.kyamshanov.notepen.library.impl.google.RefreshingAccessTokenSource
import ru.kyamshanov.notepen.library.impl.google.runGoogleDeviceFlow
import ru.kyamshanov.notepen.library.infrastructure.AndroidLibraryConnectionStore
import ru.kyamshanov.notepen.library.ui.GoogleDriveAuthorization
import ru.kyamshanov.notepen.library.ui.GoogleDriveAuthorizer
import ru.kyamshanov.notepen.library.ui.LibrarySourcesComponentImpl
import ru.kyamshanov.notepen.mainscreen.domain.usecase.AddToHistoryUseCase
import ru.kyamshanov.notepen.mainscreen.domain.usecase.CheckAvailabilityUseCase
import ru.kyamshanov.notepen.mainscreen.domain.usecase.OpenRecentFileUseCase
import ru.kyamshanov.notepen.mainscreen.infrastructure.AndroidThumbnailGenerator
import ru.kyamshanov.notepen.mainscreen.infrastructure.FileAvailabilityCheckerAndroid
import ru.kyamshanov.notepen.mainscreen.infrastructure.FileHistoryRepositoryAndroid
import ru.kyamshanov.notepen.mainscreen.infrastructure.FolderRepositoryAndroid
import ru.kyamshanov.notepen.mainscreen.infrastructure.ImageThumbnailGeneratorAndroid
import ru.kyamshanov.notepen.mainscreen.infrastructure.PdfThumbnailGeneratorAndroid
import ru.kyamshanov.notepen.mainscreen.infrastructure.ThumbnailRepositoryAndroid
import ru.kyamshanov.notepen.mainscreen.platform.FilePicker
import ru.kyamshanov.notepen.mainscreen.ui.folder.FolderContentsComponentImpl
import ru.kyamshanov.notepen.mainscreen.ui.library.LibraryFolderContentsComponentImpl
import ru.kyamshanov.notepen.mainscreen.ui.peer.PeerCatalogComponentImpl
import ru.kyamshanov.notepen.mainscreen.ui.screen.MainScreenComponent
import ru.kyamshanov.notepen.pdf.infrastructure.AndroidDocumentLoader
import ru.kyamshanov.notepen.pdf.infrastructure.AndroidImageDocumentLoader
import ru.kyamshanov.notepen.pdf.infrastructure.AndroidImagePageRenderer
import ru.kyamshanov.notepen.pdf.infrastructure.AndroidPageRenderer
import ru.kyamshanov.notepen.pdf.infrastructure.AndroidPdfDocumentLoader
import ru.kyamshanov.notepen.pdf.infrastructure.AndroidPdfPageRenderer
import ru.kyamshanov.notepen.qrconnect.ClientQrScanViewModel
import ru.kyamshanov.notepen.qrconnect.HostDiscoveryViewModel
import ru.kyamshanov.notepen.qrconnect.ManualConnectViewModel
import ru.kyamshanov.notepen.qrconnect.domain.PairingUri
import ru.kyamshanov.notepen.qrconnect.peerLanConnectionFor
import ru.kyamshanov.notepen.qrconnect.peerLibraryRegistrar
import ru.kyamshanov.notepen.sync.cloud.infrastructure.GitHubContentsCloudProvider
import ru.kyamshanov.notepen.sync.domain.CatalogDiffOrphanDetector
import ru.kyamshanov.notepen.sync.domain.DocumentStatusCoordinator
import ru.kyamshanov.notepen.sync.domain.LibraryMutationClient
import ru.kyamshanov.notepen.sync.domain.LiveDocumentSyncController
import ru.kyamshanov.notepen.sync.domain.LocalCachedDocumentCleaner
import ru.kyamshanov.notepen.sync.domain.PendingDeltaReplayCoordinator
import ru.kyamshanov.notepen.sync.domain.RemoteCatalogClientCoordinator
import ru.kyamshanov.notepen.sync.domain.RemoteCatalogProvider
import ru.kyamshanov.notepen.sync.domain.RemoteDocumentOpener
import ru.kyamshanov.notepen.sync.domain.SyncEngineRegistry
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.port.AnnotationResyncRequester
import ru.kyamshanov.notepen.sync.infrastructure.InMemoryCatalogChangeNotifier
import ru.kyamshanov.notepen.sync.infrastructure.InMemoryOpenDocumentRegistry
import ru.kyamshanov.notepen.sync.infrastructure.InMemoryRemoteCatalogCache
import ru.kyamshanov.notepen.sync.infrastructure.InMemoryRemoteDocumentStatusRegistry
import ru.kyamshanov.notepen.sync.infrastructure.JsonLocalDocumentIdRegistry
import ru.kyamshanov.notepen.sync.infrastructure.KtorSyncClient
import ru.kyamshanov.notepen.sync.infrastructure.NotifyingFileHistoryRepository
import ru.kyamshanov.notepen.sync.infrastructure.NotifyingFolderRepository
import ru.kyamshanov.notepen.sync.infrastructure.NsdPeerDiscovery
import ru.kyamshanov.notepen.sync.infrastructure.RecentsLibraryManifestProvider
import ru.kyamshanov.notepen.sync.infrastructure.SqlDelightPendingDeltaQueue
import ru.kyamshanov.notepen.sync.infrastructure.createSyncDatabaseAndroid
import ru.kyamshanov.notepen.tablet.AndroidTabletInputController
import ru.kyamshanov.notepen.tablet.LocalTabletInputController
import java.util.UUID

/**
 * Аггрегат «тяжёлых» зависимостей (Ktor-клиент + sync-стек). Создаётся в фоне
 * после показа главного окна, чтобы `onCreate` не блокировался загрузкой
 * Ktor/CIO/SQLDelight и инициализацией CIO-движка.
 */
private class HeavyDeps(
    val syncClient: KtorSyncClient,
    val syncEngineRegistry: SyncEngineRegistry,
    val pendingDeltaQueue: SqlDelightPendingDeltaQueue,
    val clientScanViewModel: ClientQrScanViewModel,
    val manualConnectViewModel: ManualConnectViewModel,
    val hostDiscoveryViewModel: HostDiscoveryViewModel,
    val remoteDocumentOpener: RemoteDocumentOpener,
    val resyncRequester: AnnotationResyncRequester,
)

class MainActivity : ComponentActivity() {
    private lateinit var rootComponent: DefaultRootComponent

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val context = applicationContext

        val filePicker = FilePicker()
        val filePickerLauncher =
            registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                filePicker.onResult(uri)
            }
        filePicker.init(filePickerLauncher)

        // Один экземпляр конвертера: он же поставляет оглавление (реализует
        // DocumentOutlineProvider) для сайдбара «Содержание».
        val ebookConverter = AndroidEbookToPdfConverter(context, Dispatchers.IO)
        val pdfDocumentLoader =
            EbookAwarePdfDocumentLoader(
                delegate =
                    AndroidDocumentLoader(
                        context = context,
                        pdfLoader = AndroidPdfDocumentLoader(context, Dispatchers.IO),
                        imageLoader = AndroidImageDocumentLoader(context, Dispatchers.IO),
                    ),
                converter = ebookConverter,
            )
        val pdfPageRenderer =
            AndroidPageRenderer(
                pdfRenderer = AndroidPdfPageRenderer(Dispatchers.IO),
                imageRenderer = AndroidImagePageRenderer(Dispatchers.IO),
            )
        val catalogChangeNotifier = InMemoryCatalogChangeNotifier()
        val historyRepo =
            NotifyingFileHistoryRepository(
                delegate = FileHistoryRepositoryAndroid(context),
                notifier = catalogChangeNotifier,
            )
        val folderRepo =
            NotifyingFolderRepository(
                delegate = FolderRepositoryAndroid(context),
                notifier = catalogChangeNotifier,
            )
        val availabilityChecker = FileAvailabilityCheckerAndroid(context)
        val thumbnailRepo = ThumbnailRepositoryAndroid(context)
        val thumbnailGenerator =
            AndroidThumbnailGenerator(
                context = context,
                pdfGenerator = PdfThumbnailGeneratorAndroid(context),
                imageGenerator = ImageThumbnailGeneratorAndroid(context),
                // Reuse the already-constructed ebook→PDF converter so EPUB/FB2/CBZ/CBR
                // recents render real first-page thumbnails instead of the broken-image
                // placeholder (same cacheDir artifact the reader produces).
                converter = ebookConverter,
            )

        val appScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        val prefs = context.getSharedPreferences("notepen", android.content.Context.MODE_PRIVATE)
        val selfId: String =
            prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also { newId ->
                prefs.edit().putString("device_id", newId).apply()
            }
        val selfInfo =
            DeviceInfo(
                id = selfId,
                name = android.os.Build.MODEL ?: "NotePen Tablet",
                host = "",
                port = 0,
            )
        val receivedDir = java.io.File(context.cacheDir, "sync").absolutePath

        // Light, sync-only refs that the main screen depends on for the very
        // first frame. The catalog cache + status/open-doc registries are
        // in-memory and need to be visible to MainScreen synchronously so it
        // can subscribe before the sync stack finishes wiring; the background
        // coroutine below fills them as the host connects.
        val remoteCatalogCache = InMemoryRemoteCatalogCache()
        val remoteDocumentStatusRegistry = InMemoryRemoteDocumentStatusRegistry()
        val openDocumentRegistry = InMemoryOpenDocumentRegistry()
        val localDocumentIdRegistry =
            JsonLocalDocumentIdRegistry(
                manifestPath = "$receivedDir/.notepen-doc-ids.json",
                ioDispatcher = Dispatchers.IO,
            )

        // Content-addressed identity. Documents are content URIs, so bytes are
        // read via the ContentResolver and the digest is cached in a registry
        // file under app-private storage (a sidecar "next to" the file is
        // impossible for content URIs). The fingerprint uses the URI's size +
        // last-modified when the provider exposes them; otherwise null disables
        // the cross-run cache for that entry (the session in-memory cache still
        // serves repeats).
        val documentIdentityProvider =
            CachingDocumentIdentityProvider(
                readBytes = { path ->
                    contentResolver.openInputStream(Uri.parse(path))?.use { it.readBytes() }
                        ?: error("Cannot open $path")
                },
                fingerprint = { path -> contentUriFingerprint(path) },
                persistentCache =
                    RegistryFileIdentityCache(
                        registryPath = java.io.File(context.filesDir, "doc-identities.tsv").absolutePath,
                        ioDispatcher = Dispatchers.IO,
                    ),
                ioDispatcher = Dispatchers.IO,
            )

        // Android is client-only: online peers = connected hosts. Proxied
        // through a MutableStateFlow so MainScreen can subscribe before the
        // sync client exists; the heavy coroutine below pipes the real flow in.
        val onlinePeerIdsFlow = MutableStateFlow<Set<String>>(emptySet())
        // Holder for the heavy sync stack. Stays null until the background
        // coroutine below finishes; consumers (App, factories) tolerate null.
        val heavyDepsFlow = MutableStateFlow<HeavyDeps?>(null)

        // The library shelf is sourced through the :library abstraction. Android is client-only
        // (decision 4): it never hosts a local-folder library, so a local-folder backend is NOT
        // registered. Two cloud/peer backends are:
        //  - PeerLan: reuses the existing sync infra — the client-side catalog cache for listing and
        //    the (lazily built) RemoteDocumentOpener for streaming. No peer is connected here; that
        //    happens via LibrarySources (M2c) calling registry.connect(PeerLan(...)).
        //  - GitHub (M3): a cloud client (Android is a GitHub client just like desktop). It reads a
        //    repo's `books/` folder via the existing GitHubContentsCloudProvider over a dedicated,
        //    lazily-built CIO client so startup stays cheap; the Librarian role follows a write token.
        // With no connected library mergedBooks stays empty, so the shelf looks identical to before.
        // Saved PeerLan/GitHub libraries persist to library_connections.json under filesDir. Android is
        // client-only, so no Local connection is ever persisted (Local backend isn't registered).
        val libraryConnectionStore = AndroidLibraryConnectionStore(context)
        val githubCacheDir = java.io.File(context.cacheDir, "github-library").absolutePath
        val driveCacheDir = java.io.File(context.cacheDir, "google-drive-library").absolutePath
        // One lazily-built CIO engine shared by every cloud client (GitHub + Google Drive).
        val cloudHttpClient by lazy { HttpClient(CIO) }
        // Google OAuth client credentials; empty when unset (sign-in cannot start, backend still registered).
        val googleOAuthConfig =
            GoogleOAuthConfig(
                clientId = System.getenv("NOTEPEN_GOOGLE_CLIENT_ID").orEmpty(),
                clientSecret = System.getenv("NOTEPEN_GOOGLE_CLIENT_SECRET").orEmpty(),
            )
        val googleDeviceAuthenticator = GoogleDeviceAuthenticator(httpClient = cloudHttpClient, config = googleOAuthConfig)
        val libraryRegistry =
            DefaultLibraryRegistry(
                backends =
                    listOf(
                        PeerLanLibraryBackend(
                            catalogs = remoteCatalogCache.catalogs,
                            documentOpenerProvider = { heavyDepsFlow.value?.remoteDocumentOpener },
                            // M5b: Android is client-only but CAN be a Librarian client of a peer.
                            // When the host grants it that role, mutations stream over the sync client.
                            mutationClientProvider = { peerId ->
                                heavyDepsFlow.value?.syncClient?.let { syncClient ->
                                    LibraryMutationClient(
                                        client = syncClient,
                                        hostId = peerId,
                                        newRequestId = { UUID.randomUUID().toString() },
                                    )
                                }
                            },
                            onlinePeerIds = onlinePeerIdsFlow,
                        ),
                        GitHubLibraryBackend(
                            providerFactory = { coords ->
                                GitHubContentsCloudProvider(
                                    httpClient = cloudHttpClient,
                                    owner = coords.owner,
                                    repo = coords.name,
                                    branch = coords.branch,
                                    bearerToken = coords.token,
                                )
                            },
                            cacheDir = githubCacheDir,
                            ioDispatcher = Dispatchers.IO,
                        ),
                        // Cloud (Google Drive): a Drive folder read as a shelf via the Drive v3 REST
                        // API; the role follows the granted OAuth scope (readonly → Reader).
                        GoogleDriveLibraryBackend(
                            storeFactory = { coords ->
                                GoogleDriveStore(
                                    httpClient = cloudHttpClient,
                                    tokenSource =
                                        RefreshingAccessTokenSource(
                                            refreshToken = coords.refreshToken,
                                            authenticator = googleDeviceAuthenticator,
                                        ),
                                )
                            },
                            cacheDir = driveCacheDir,
                            ioDispatcher = Dispatchers.IO,
                        ),
                    ),
                scope = appScope,
                ioDispatcher = Dispatchers.IO,
                connectionStore = libraryConnectionStore,
            )
        // M2b: when "open library at startup" is on, auto-reconnect saved PeerLan libraries. The
        // PeerLan backend only projects the catalog cache (tolerates a not-yet-ready opener), so this
        // is safe to run before HeavyDeps finishes wiring. Non-blocking — runs on appScope.
        appScope.launch {
            if (defaultAppSettingsRepository().load().openLibraryAtStartup) {
                libraryRegistry.savedConnections().forEach { spec ->
                    libraryRegistry.connect(spec)
                }
            }
        }

        // Build the heavy sync/network stack off the main thread so the first
        // frame can paint before Ktor/CIO/SQLDelight classes are touched.
        appScope.launch(Dispatchers.IO) {
            val wsJson = Json { classDiscriminator = "type" }
            val httpClient =
                HttpClient(CIO) {
                    install(WebSockets) {
                        contentConverter = KotlinxWebsocketSerializationConverter(wsJson)
                    }
                    // Bound only the TCP connect/handshake (NOT the long-lived WS session):
                    // when the host is gone, a reconnect attempt must fail fast so the
                    // reconnect deadline is honoured and the sync indicator clears instead
                    // of staying "reconnecting" (yellow) indefinitely on a hung connect.
                    install(io.ktor.client.plugins.HttpTimeout) {
                        connectTimeoutMillis = 4_000L
                    }
                }
            val syncClient = KtorSyncClient(httpClient)
            // Persistent offline buffer (SQLDelight-backed) — survives process death
            // so edits made offline still replay after the next launch + reconnect.
            val pendingDeltaQueue =
                SqlDelightPendingDeltaQueue(
                    databaseProvider = { createSyncDatabaseAndroid(context = context) },
                    ioDispatcher = Dispatchers.IO,
                )
            // One-time outbox reset for the content-addressed documentId change
            // (M0). Idempotent — guarded by a MigrationMarker row; runs before
            // replay so stale path-keyed deltas are never broadcast.
            pendingDeltaQueue.runContentAddressedIdMigration()
            val syncEngineRegistry =
                SyncEngineRegistry(
                    deviceId = selfId,
                    scope = appScope,
                    server = null,
                    client = syncClient,
                    pendingQueue = pendingDeltaQueue,
                )
            val clientConnected =
                syncClient.connectedHosts
                    .distinctUntilChanged()
                    .filter { it.isNotEmpty() }
                    .map { }
            PendingDeltaReplayCoordinator(
                registry = syncEngineRegistry,
                connectionEstablished = clientConnected,
            ).start(scope = appScope)

            // Bridge: a camera scan / manual paste that carries a library QR auto-adds that PeerLan
            // library to the shelf in one step (the catalog coordinator then fills in its books).
            val registerPairedLibrary = peerLibraryRegistrar { libraryRegistry }
            val clientScanViewModel =
                ClientQrScanViewModel(
                    syncClient = syncClient,
                    selfInfo = selfInfo,
                    scope = appScope,
                    onLibraryPaired = registerPairedLibrary,
                )
            val manualConnectViewModel =
                ManualConnectViewModel(
                    syncClient = syncClient,
                    selfInfo = selfInfo,
                    scope = appScope,
                    onLibraryPaired = registerPairedLibrary,
                )
            // Direct-dial saved LAN libraries so their transport relinks WITHOUT mDNS — the durability
            // half of connect-by-QR (the address + code came from the scanned QR and were persisted).
            if (defaultAppSettingsRepository().load().openLibraryAtStartup) {
                libraryRegistry
                    .savedConnections()
                    .filterIsInstance<LibraryConnection.PeerLan>()
                    .forEach { peer ->
                        val host = peer.host
                        val port = peer.port
                        val code = peer.pairingCode
                        if (!host.isNullOrBlank() && port != null && !code.isNullOrBlank()) {
                            runCatching {
                                syncClient.connect(
                                    DeviceInfo(id = "$host:$port", name = peer.libraryName, host = host, port = port),
                                    code,
                                    selfInfo,
                                )
                            }
                        }
                    }
            }
            // LAN host auto-discovery (mDNS via NsdManager): the client browses for
            // hosts the desktop advertises and connects with the code from the advert.
            val hostDiscoveryViewModel =
                HostDiscoveryViewModel(
                    discovery = NsdPeerDiscovery(context = applicationContext),
                    syncClient = syncClient,
                    selfInfo = selfInfo,
                    scope = appScope,
                )

            // Feed peer-originated stroke deltas into the right engine so SyncBridge
            // can mirror them onto the local drawing state once DetailsContent opens.
            appScope.launch {
                syncClient.incomingMessages.collect { hostMessage ->
                    val msg = hostMessage.message
                    if (msg is NetworkMessage.StrokeDeltaMessage) {
                        syncEngineRegistry.get(msg.documentId).processPeer(msg.delta)
                    }
                }
            }

            RemoteCatalogClientCoordinator(client = syncClient, cache = remoteCatalogCache)
                .start(scope = appScope)
            val remoteCatalogProvider =
                RemoteCatalogProvider(
                    hostName = selfInfo.name,
                    manifestProvider =
                        RecentsLibraryManifestProvider(
                            historyRepository = historyRepo,
                            identityProvider = documentIdentityProvider,
                        ),
                    folderRepository = folderRepo,
                )
            remoteCatalogProvider.serve(client = syncClient, scope = appScope)
            remoteCatalogProvider.broadcastChanges(
                notifier = catalogChangeNotifier,
                client = syncClient,
                scope = appScope,
            )
            val remoteDocumentOpener =
                RemoteDocumentOpener(
                    client = syncClient,
                    catalogs = remoteCatalogCache.catalogs,
                    destDir = receivedDir,
                    documentIdRegistry = localDocumentIdRegistry,
                )
            DocumentStatusCoordinator(client = syncClient, registry = remoteDocumentStatusRegistry)
                .start(scope = appScope)
            CatalogDiffOrphanDetector(
                catalogs = remoteCatalogCache.catalogs,
                queue = pendingDeltaQueue,
                registry = remoteDocumentStatusRegistry,
            ).start(scope = appScope)

            LocalCachedDocumentCleaner(
                receivedPdfDir = receivedDir,
                pendingCounts = pendingDeltaQueue.pendingCounts(),
                catalogsFlow = remoteCatalogCache.catalogs,
                openDocuments = openDocumentRegistry,
                documentIdRegistry = localDocumentIdRegistry,
            ).start(scope = appScope)

            // Pipe connected hosts into the public onlinePeerIdsFlow.
            appScope.launch {
                syncClient.connectedHosts.collect { hosts ->
                    onlinePeerIdsFlow.value = hosts.map { it.id }.toSet()
                }
            }

            // Догон при включении живой синхронизации: повторно просим у хоста полный
            // снимок аннотаций. Переиспользует существующее wire-сообщение
            // AnnotationSnapshotRequest (ответ ловит коллектор в EditorPanel и кладёт
            // штрихи в drawingStates по dedupe-strokeId). Fire-and-forget на appScope;
            // broadcast без подключённых хостов — no-op (оффлайн покрывает on-connect).
            val resyncRequester =
                AnnotationResyncRequester { documentId ->
                    appScope.launch(Dispatchers.IO) {
                        runCatching {
                            syncClient.broadcast(NetworkMessage.AnnotationSnapshotRequest(documentId = documentId))
                        }
                    }
                }

            heavyDepsFlow.value =
                HeavyDeps(
                    syncClient = syncClient,
                    syncEngineRegistry = syncEngineRegistry,
                    pendingDeltaQueue = pendingDeltaQueue,
                    clientScanViewModel = clientScanViewModel,
                    manualConnectViewModel = manualConnectViewModel,
                    hostDiscoveryViewModel = hostDiscoveryViewModel,
                    remoteDocumentOpener = remoteDocumentOpener,
                    resyncRequester = resyncRequester,
                )
        }

        val root =
            DefaultRootComponent(
                componentContext = defaultComponentContext(),
                historyRepository = historyRepo,
                mainComponentFactory = {
                    componentContext,
                    onOpenEditor,
                    onOpenPeerCatalog,
                    onOpenFolder,
                    onOpenLibraryFolder,
                    onOpenSettings,
                    onOpenLibrarySources,
                    ->
                    // Android — только клиент: локальной папки нет, но в реестре могут быть
                    // удалённые библиотеки (LAN/GitHub/Drive) — их карточки и drill-down работают.
                    MainScreenComponent(
                        componentContext = componentContext,
                        historyRepository = historyRepo,
                        folderRepository = folderRepo,
                        addToHistory = AddToHistoryUseCase(historyRepo),
                        checkAvailability = CheckAvailabilityUseCase(availabilityChecker, historyRepo),
                        openRecentFileUseCase = OpenRecentFileUseCase(availabilityChecker),
                        thumbnailRepository = thumbnailRepo,
                        thumbnailGenerator = thumbnailGenerator,
                        onOpenEditor = onOpenEditor,
                        onOpenFilePicker = { filePicker.pickDocument() },
                        onOpenPeerCatalog = onOpenPeerCatalog,
                        onOpenFolder = onOpenFolder,
                        onOpenSettings = onOpenSettings,
                        onOpenLibrarySources = onOpenLibrarySources,
                        onOpenLibraryFolder = onOpenLibraryFolder,
                        remoteCatalogsFlow = remoteCatalogCache.catalogs,
                        onlinePeerIdsFlow = onlinePeerIdsFlow,
                        libraryRegistry = libraryRegistry,
                    )
                },
                peerCatalogComponentFactory = { ctx, peerId, displayName, onBack, onOpenEditor ->
                    PeerCatalogComponentImpl(
                        componentContext = ctx,
                        peerId = peerId,
                        displayName = displayName,
                        catalogsFlow = remoteCatalogCache.catalogs,
                        onlinePeerIdsFlow = onlinePeerIdsFlow,
                        // Heavy stack may still be wiring on the first frame; by the
                        // time the user navigates here it's always ready (sync init
                        // finishes well before any tap can land).
                        remoteDocumentOpener = heavyDepsFlow.value?.remoteDocumentOpener,
                        receivedPdfDir = receivedDir,
                        onBack = onBack,
                        onOpenEditor = onOpenEditor,
                    )
                },
                folderComponentFactory = { ctx, folderId, folderName, onBack, onOpenEditor, onOpenFolder ->
                    FolderContentsComponentImpl(
                        componentContext = ctx,
                        folderId = folderId,
                        folderName = folderName,
                        historyRepository = historyRepo,
                        folderRepository = folderRepo,
                        addToHistory = AddToHistoryUseCase(historyRepo),
                        thumbnailRepository = thumbnailRepo,
                        thumbnailGenerator = thumbnailGenerator,
                        onBack = onBack,
                        onOpenFilePicker = { filePicker.pickDocument() },
                        onOpenEditor = onOpenEditor,
                        onOpenFolder = onOpenFolder,
                    )
                },
                libraryFolderComponentFactory = { ctx, libraryId, onBack, onOpenEditor ->
                    LibraryFolderContentsComponentImpl(
                        componentContext = ctx,
                        libraryId = libraryId,
                        libraryRegistry = libraryRegistry,
                        onBack = onBack,
                        onOpenEditor = onOpenEditor,
                    )
                },
                settingsComponentFactory = { ctx, onBack ->
                    SettingsComponentImpl(
                        componentContext = ctx,
                        repository = defaultAppSettingsRepository(),
                        onBackListener = onBack,
                    )
                },
                // M2c: Android is client-only (decision 4). LibrarySources manages PeerLan
                // connections + the startup toggle, but cannot serve over LAN or add a local
                // folder — both callbacks are null, hiding those actions in the UI.
                librarySourcesComponentFactory = { ctx, onBack ->
                    LibrarySourcesComponentImpl(
                        componentContext = ctx,
                        registry = libraryRegistry,
                        settingsRepository = defaultAppSettingsRepository(),
                        catalogsFlow = remoteCatalogCache.catalogs,
                        onlinePeerIdsFlow = onlinePeerIdsFlow,
                        onPickLocalFolder = null,
                        // Paste-to-connect: dial the host directly from the pasted QR string and add the
                        // targeted PeerLan library (camera scanning is the other path, via the Sync dialog).
                        connectLibraryByQr = { payload ->
                            runCatching {
                                val uri = PairingUri.parse(payload.trim()) ?: error("Некорректная строка подключения")
                                val client = heavyDepsFlow.value?.syncClient ?: error("Синхронизация ещё не готова")
                                val server = client.connect(uri.toServerDeviceInfo(), uri.code, selfInfo).getOrThrow()
                                libraryRegistry.connect(peerLanConnectionFor(uri, server)).getOrThrow()
                                uri.libraryName.ifBlank { server.name.ifBlank { uri.host } }
                            }
                        },
                        // Android cannot host a library over LAN (client-only) — no per-library share QR.
                        // Google sign-in is offered only when an OAuth client is configured.
                        googleDriveAuthorizer =
                            googleOAuthConfig.clientId
                                .takeIf { it.isNotEmpty() }
                                ?.let {
                                    GoogleDriveAuthorizer { onCode ->
                                        runGoogleDeviceFlow(
                                            authenticator = googleDeviceAuthenticator,
                                            scope = GoogleOAuthConfig.SCOPE_DRIVE_READONLY,
                                            onCode = onCode,
                                        ).mapCatching { authorized ->
                                            GoogleDriveAuthorization(
                                                refreshToken = authorized.refreshToken ?: error("Google returned no refresh token"),
                                                scope = GoogleOAuthConfig.SCOPE_DRIVE_READONLY,
                                            )
                                        }
                                    }
                                },
                        onBack = onBack,
                    )
                },
            )

        rootComponent = root
        handleIncomingIntent(intent)

        setContent {
            // Remove when https://issuetracker.google.com/issues/364713509 is fixed
            LaunchedEffect(isSystemInDarkTheme()) {
                enableEdgeToEdge()
            }
            // Side-channel for stylus state (barrel button, eraser tip, tilt,
            // hover) that Compose's commonMain pointer pipeline doesn't expose.
            // Fed via `Modifier.stylusEventSink` attached inside DrawablePdfPage.
            val tabletController = remember { AndroidTabletInputController() }
            // Heavy deps swap in once the background wiring coroutine finishes;
            // the App composable handles null gracefully for all sync params.
            val heavyDeps by heavyDepsFlow.collectAsState()
            // Per-document live-sync controller (M4). Android is a sync client, so
            // the toolbar toggle is available once the heavy stack is wired; null
            // until then (toggle hidden).
            val liveSyncController =
                remember(heavyDeps) {
                    heavyDeps?.let { deps ->
                        LiveDocumentSyncController(
                            openDocumentRegistry = openDocumentRegistry,
                            syncEngineRegistry = deps.syncEngineRegistry,
                            resyncRequester = deps.resyncRequester,
                        )
                    }
                }
            CompositionLocalProvider(LocalTabletInputController provides tabletController) {
                App(
                    rootComponent = root,
                    pdfDocumentLoader = pdfDocumentLoader,
                    pdfPageRenderer = pdfPageRenderer,
                    outlineProvider = ebookConverter,
                    syncEngineFor = heavyDeps?.syncEngineRegistry?.let { reg -> reg::get },
                    peerClient = heavyDeps?.syncClient,
                    pendingDeltaCounts = heavyDeps?.pendingDeltaQueue?.pendingCounts(),
                    clientScanViewModel = heavyDeps?.clientScanViewModel,
                    manualConnectViewModel = heavyDeps?.manualConnectViewModel,
                    hostDiscoveryViewModel = heavyDeps?.hostDiscoveryViewModel,
                    receivedPdfDir = receivedDir,
                    openDocumentRegistry = openDocumentRegistry,
                    liveSyncController = liveSyncController,
                    localDocumentIdRegistry = localDocumentIdRegistry,
                    documentIdentityProvider = documentIdentityProvider,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    /**
     * Открывает документ, пришедший извне: «Открыть с помощью» ([Intent.ACTION_VIEW])
     * или «Поделиться» ([Intent.ACTION_SEND]). URI прокидывается в общий
     * [RootComponent.openDetailsExternally]; для чтения байт достаточно временного
     * гранта чтения из самого интента.
     */
    private fun handleIncomingIntent(intent: Intent) {
        val uri = incomingUri(intent) ?: return
        if (uri.scheme == "content") {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        rootComponent.openDetailsExternally(uri.toString())
    }

    private fun incomingUri(intent: Intent): Uri? =
        when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        }

    /**
     * Cheap `size-lastModified` fingerprint for a content URI, used to guard the
     * persistent identity cache. Querying BOTH size and last-modified is required:
     * a same-size in-place edit must invalidate the cached digest, otherwise sync
     * would keep using the stale (wrong) documentId. Returns `null` when the
     * metadata isn't readable (then the cross-run cache simply misses and the
     * digest is recomputed).
     */
    private fun contentUriFingerprint(path: String): String? =
        runCatching {
            val uri = Uri.parse(path)
            contentResolver
                .query(
                    uri,
                    arrayOf(OpenableColumns.SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED),
                    null,
                    null,
                    null,
                )
                ?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use null
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else -1L
                    val mtimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    val mtime = if (mtimeIndex >= 0 && !cursor.isNull(mtimeIndex)) cursor.getLong(mtimeIndex) else 0L
                    if (size >= 0) "$size-$mtime" else null
                }
        }.getOrNull()
}
