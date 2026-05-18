package ru.kyamshanov.notepen

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import ru.kyamshanov.notepen.mainscreen.platform.FilePicker
import ru.kyamshanov.notepen.tablet.AndroidTabletInputController
import ru.kyamshanov.notepen.tablet.LocalTabletInputController
import com.arkivanov.decompose.defaultComponentContext
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.mainscreen.domain.usecase.AddToHistoryUseCase
import ru.kyamshanov.notepen.mainscreen.domain.usecase.CheckAvailabilityUseCase
import ru.kyamshanov.notepen.mainscreen.domain.usecase.OpenRecentFileUseCase
import ru.kyamshanov.notepen.mainscreen.infrastructure.FileAvailabilityCheckerAndroid
import ru.kyamshanov.notepen.mainscreen.infrastructure.FileHistoryRepositoryAndroid
import ru.kyamshanov.notepen.mainscreen.infrastructure.FolderRepositoryAndroid
import ru.kyamshanov.notepen.mainscreen.infrastructure.PdfThumbnailGeneratorAndroid
import ru.kyamshanov.notepen.mainscreen.infrastructure.ThumbnailRepositoryAndroid
import ru.kyamshanov.notepen.mainscreen.ui.peer.PeerCatalogComponentImpl
import ru.kyamshanov.notepen.mainscreen.ui.screen.MainScreenComponent
import ru.kyamshanov.notepen.pdf.infrastructure.AndroidPdfDocumentLoader
import ru.kyamshanov.notepen.pdf.infrastructure.AndroidPdfPageRenderer
import ru.kyamshanov.notepen.qrconnect.ClientQrScanViewModel
import ru.kyamshanov.notepen.qrconnect.ManualConnectViewModel
import ru.kyamshanov.notepen.sync.domain.CatalogDiffOrphanDetector
import ru.kyamshanov.notepen.sync.domain.DocumentStatusCoordinator
import ru.kyamshanov.notepen.sync.domain.LocalCachedDocumentCleaner
import ru.kyamshanov.notepen.sync.domain.PendingDeltaReplayCoordinator
import ru.kyamshanov.notepen.sync.domain.RemoteCatalogProvider
import ru.kyamshanov.notepen.sync.domain.model.PairingState
import ru.kyamshanov.notepen.sync.domain.RemoteCatalogClientCoordinator
import ru.kyamshanov.notepen.sync.domain.RemoteDocumentOpener
import ru.kyamshanov.notepen.sync.domain.SyncEngineRegistry
import ru.kyamshanov.notepen.sync.infrastructure.InMemoryOpenDocumentRegistry
import ru.kyamshanov.notepen.sync.infrastructure.InMemoryRemoteCatalogCache
import ru.kyamshanov.notepen.sync.infrastructure.InMemoryRemoteDocumentStatusRegistry
import ru.kyamshanov.notepen.sync.infrastructure.JsonLocalDocumentIdRegistry
import ru.kyamshanov.notepen.sync.infrastructure.SqlDelightPendingDeltaQueue
import ru.kyamshanov.notepen.sync.infrastructure.createSyncDatabaseAndroid
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.infrastructure.KtorSyncClient
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val context = applicationContext

        val filePicker = FilePicker()
        val filePickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            filePicker.onResult(uri)
        }
        filePicker.init(filePickerLauncher)

        val pdfDocumentLoader = AndroidPdfDocumentLoader(context, Dispatchers.IO)
        val pdfPageRenderer = AndroidPdfPageRenderer(Dispatchers.IO)
        val historyRepo = FileHistoryRepositoryAndroid(context)
        val folderRepo = FolderRepositoryAndroid(context)
        val availabilityChecker = FileAvailabilityCheckerAndroid(context)
        val thumbnailRepo = ThumbnailRepositoryAndroid(context)
        val thumbnailGenerator = PdfThumbnailGeneratorAndroid(context)

        val appScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        val selfId = UUID.randomUUID().toString()
        val selfInfo = DeviceInfo(
            id = selfId,
            name = android.os.Build.MODEL ?: "NotePen Tablet",
            host = "",
            port = 0,
        )
        val wsJson = Json { classDiscriminator = "type" }
        val httpClient = HttpClient(CIO) {
            install(WebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(wsJson)
            }
        }
        val syncClient = KtorSyncClient(httpClient)
        // Persistent offline buffer (SQLDelight-backed) — survives process death
        // so edits made offline still replay after the next launch + reconnect.
        val syncDatabase = createSyncDatabaseAndroid(context = context)
        val pendingDeltaQueue = SqlDelightPendingDeltaQueue(
            database = syncDatabase,
            ioDispatcher = Dispatchers.IO,
        )
        // One registry per app; each open document is its own SyncEngine.
        val syncEngineRegistry = SyncEngineRegistry(
            deviceId = selfId,
            scope = appScope,
            server = null,
            client = syncClient,
            pendingQueue = pendingDeltaQueue,
        )
        // Trigger pending-delta replay whenever a new host enters the connected set.
        val clientConnected = syncClient.connectedHosts
            .distinctUntilChanged()
            .filter { it.isNotEmpty() }
            .map { }
        PendingDeltaReplayCoordinator(
            registry = syncEngineRegistry,
            connectionEstablished = clientConnected,
        ).start(scope = appScope)

        val clientScanViewModel = ClientQrScanViewModel(
            syncClient = syncClient,
            selfInfo = selfInfo,
            scope = appScope,
        )
        val manualConnectViewModel = ManualConnectViewModel(
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

        val receivedDir = java.io.File(context.cacheDir, "sync").absolutePath

        // Tablet side: cache the host's library catalog (refreshed on every
        // reconnect by RemoteCatalogClientCoordinator), and on tap open
        // documents on-demand via RemoteDocumentOpener.
        val remoteCatalogCache = InMemoryRemoteCatalogCache()
        RemoteCatalogClientCoordinator(client = syncClient, cache = remoteCatalogCache)
            .start(scope = appScope)
        // Симметричный ответ: если подключённый хост запросит наш каталог
        // (для своей секции «Подключённые устройства»), отдадим ему snapshot
        // нашей локальной истории.
        RemoteCatalogProvider(
            hostName = selfInfo.name,
            historyRepository = historyRepo,
            folderRepository = folderRepo,
        ).serve(client = syncClient, scope = appScope)
        val localDocumentIdRegistry = JsonLocalDocumentIdRegistry(
            manifestPath = "$receivedDir/.notepen-doc-ids.json",
            ioDispatcher = Dispatchers.IO,
        )
        val remoteDocumentOpener = RemoteDocumentOpener(
            client = syncClient,
            catalogs = remoteCatalogCache.catalogs,
            destDir = receivedDir,
            documentIdRegistry = localDocumentIdRegistry,
        )
        // Track per-document orphan flags as host signals come in.
        val remoteDocumentStatusRegistry = InMemoryRemoteDocumentStatusRegistry()
        DocumentStatusCoordinator(client = syncClient, registry = remoteDocumentStatusRegistry)
            .start(scope = appScope)
        // Also mark orphans passively: catalog drops a doc that has pending edits.
        CatalogDiffOrphanDetector(
            catalogs = remoteCatalogCache.catalogs,
            queue = pendingDeltaQueue,
            registry = remoteDocumentStatusRegistry,
        ).start(scope = appScope)

        val openDocumentRegistry = InMemoryOpenDocumentRegistry()
        LocalCachedDocumentCleaner(
            receivedPdfDir = receivedDir,
            pendingCounts = pendingDeltaQueue.pendingCounts(),
            catalogsFlow = remoteCatalogCache.catalogs,
            openDocuments = openDocumentRegistry,
            documentIdRegistry = localDocumentIdRegistry,
        ).start(scope = appScope)

        // Android is client-only: online peers = connected hosts.
        val onlinePeerIds = syncClient.connectedHosts.map { hosts -> hosts.map { it.id }.toSet() }

        val root = DefaultRootComponent(
            componentContext = defaultComponentContext(),
            historyRepository = historyRepo,
            mainComponentFactory = { componentContext, onOpenEditor, onOpenPeerCatalog ->
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
                    onOpenFilePicker = { filePicker.pickPdfFile() },
                    onOpenPeerCatalog = onOpenPeerCatalog,
                    remoteCatalogsFlow = remoteCatalogCache.catalogs,
                    onlinePeerIdsFlow = onlinePeerIds,
                )
            },
            peerCatalogComponentFactory = { ctx, peerId, displayName, onBack, onOpenEditor ->
                PeerCatalogComponentImpl(
                    componentContext = ctx,
                    peerId = peerId,
                    displayName = displayName,
                    catalogsFlow = remoteCatalogCache.catalogs,
                    onlinePeerIdsFlow = onlinePeerIds,
                    remoteDocumentOpener = remoteDocumentOpener,
                    receivedPdfDir = receivedDir,
                    onBack = onBack,
                    onOpenEditor = onOpenEditor,
                )
            },
        )

        setContent {
            // Remove when https://issuetracker.google.com/issues/364713509 is fixed
            LaunchedEffect(isSystemInDarkTheme()) {
                enableEdgeToEdge()
            }
            // Side-channel for stylus state (barrel button, eraser tip, tilt,
            // hover) that Compose's commonMain pointer pipeline doesn't expose.
            // Fed via `Modifier.stylusEventSink` attached inside DrawablePdfPage.
            val tabletController = remember { AndroidTabletInputController() }
            CompositionLocalProvider(LocalTabletInputController provides tabletController) {
                App(
                    rootComponent = root,
                    pdfDocumentLoader = pdfDocumentLoader,
                    pdfPageRenderer = pdfPageRenderer,
                    syncEngineFor = syncEngineRegistry::get,
                    peerClient = syncClient,
                    pendingDeltaCounts = pendingDeltaQueue.pendingCounts(),
                    clientScanViewModel = clientScanViewModel,
                    manualConnectViewModel = manualConnectViewModel,
                    receivedPdfDir = receivedDir,
                    openDocumentRegistry = openDocumentRegistry,
                    localDocumentIdRegistry = localDocumentIdRegistry,
                )
            }
        }
    }
}
