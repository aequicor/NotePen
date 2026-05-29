package ru.kyamshanov.notepen

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.os.Bundle
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
import ru.kyamshanov.notepen.mainscreen.ui.peer.PeerCatalogComponentImpl
import ru.kyamshanov.notepen.mainscreen.ui.screen.MainScreenComponent
import ru.kyamshanov.notepen.pdf.infrastructure.AndroidDocumentLoader
import ru.kyamshanov.notepen.pdf.infrastructure.AndroidImageDocumentLoader
import ru.kyamshanov.notepen.pdf.infrastructure.AndroidImagePageRenderer
import ru.kyamshanov.notepen.pdf.infrastructure.AndroidPageRenderer
import ru.kyamshanov.notepen.pdf.infrastructure.AndroidPdfDocumentLoader
import ru.kyamshanov.notepen.pdf.infrastructure.AndroidPdfPageRenderer
import ru.kyamshanov.notepen.qrconnect.ClientQrScanViewModel
import ru.kyamshanov.notepen.qrconnect.ManualConnectViewModel
import ru.kyamshanov.notepen.sync.domain.CatalogDiffOrphanDetector
import ru.kyamshanov.notepen.sync.domain.DocumentStatusCoordinator
import ru.kyamshanov.notepen.sync.domain.LocalCachedDocumentCleaner
import ru.kyamshanov.notepen.sync.domain.PendingDeltaReplayCoordinator
import ru.kyamshanov.notepen.sync.domain.RemoteCatalogClientCoordinator
import ru.kyamshanov.notepen.sync.domain.RemoteCatalogProvider
import ru.kyamshanov.notepen.sync.domain.RemoteDocumentOpener
import ru.kyamshanov.notepen.sync.domain.SyncEngineRegistry
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.infrastructure.InMemoryCatalogChangeNotifier
import ru.kyamshanov.notepen.sync.infrastructure.InMemoryOpenDocumentRegistry
import ru.kyamshanov.notepen.sync.infrastructure.InMemoryRemoteCatalogCache
import ru.kyamshanov.notepen.sync.infrastructure.InMemoryRemoteDocumentStatusRegistry
import ru.kyamshanov.notepen.document.infrastructure.CachingDocumentIdentityProvider
import ru.kyamshanov.notepen.document.infrastructure.RegistryFileIdentityCache
import ru.kyamshanov.notepen.sync.infrastructure.JsonLocalDocumentIdRegistry
import ru.kyamshanov.notepen.sync.infrastructure.KtorSyncClient
import ru.kyamshanov.notepen.sync.infrastructure.NotifyingFileHistoryRepository
import ru.kyamshanov.notepen.sync.infrastructure.NotifyingFolderRepository
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
    val remoteDocumentOpener: RemoteDocumentOpener,
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

            val clientScanViewModel =
                ClientQrScanViewModel(
                    syncClient = syncClient,
                    selfInfo = selfInfo,
                    scope = appScope,
                )
            val manualConnectViewModel =
                ManualConnectViewModel(
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

            heavyDepsFlow.value =
                HeavyDeps(
                    syncClient = syncClient,
                    syncEngineRegistry = syncEngineRegistry,
                    pendingDeltaQueue = pendingDeltaQueue,
                    clientScanViewModel = clientScanViewModel,
                    manualConnectViewModel = manualConnectViewModel,
                    remoteDocumentOpener = remoteDocumentOpener,
                )
        }

        val root =
            DefaultRootComponent(
                componentContext = defaultComponentContext(),
                historyRepository = historyRepo,
                mainComponentFactory = { componentContext, onOpenEditor, onOpenPeerCatalog, onOpenFolder, _, onOpenSettings ->
                    // libraryFolder=null на Android → onOpenLibraryFolder тоже не нужен;
                    // компонент не показывает карточку «Библиотека», навигация туда
                    // никогда не инициируется.
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
                        remoteCatalogsFlow = remoteCatalogCache.catalogs,
                        onlinePeerIdsFlow = onlinePeerIdsFlow,
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
                settingsComponentFactory = { ctx, onBack ->
                    SettingsComponentImpl(
                        componentContext = ctx,
                        repository = defaultAppSettingsRepository(),
                        onBackListener = onBack,
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
                    receivedPdfDir = receivedDir,
                    openDocumentRegistry = openDocumentRegistry,
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
