import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.platform.win32.WinDef.HWND
import ru.kyamshanov.notepen.tablet.CocoaTabletInputController
import ru.kyamshanov.notepen.tablet.LocalTabletInputController
import ru.kyamshanov.notepen.tablet.NoOpTabletInputController
import ru.kyamshanov.notepen.tablet.TabletInputController
import ru.kyamshanov.notepen.tablet.WinTabTabletInputController
import ru.kyamshanov.notepen.tablet.WindowsPenFix
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.extensions.compose.lifecycle.LifecycleController
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import notepen.app.bycompose.desktop.generated.resources.Res
import notepen.app.bycompose.desktop.generated.resources.app_icon
import org.jetbrains.compose.resources.painterResource
import ru.kyamshanov.notepen.App
import ru.kyamshanov.notepen.DefaultRootComponent
import ru.kyamshanov.notepen.RootComponent
import ru.kyamshanov.notepen.mainscreen.domain.usecase.AddToHistoryUseCase
import ru.kyamshanov.notepen.mainscreen.domain.usecase.CheckAvailabilityUseCase
import ru.kyamshanov.notepen.mainscreen.domain.usecase.OpenRecentFileUseCase
import ru.kyamshanov.notepen.mainscreen.infrastructure.FileAvailabilityCheckerDesktop
import ru.kyamshanov.notepen.mainscreen.infrastructure.FileHistoryRepositoryDesktop
import ru.kyamshanov.notepen.mainscreen.infrastructure.FolderRepositoryDesktop
import ru.kyamshanov.notepen.mainscreen.infrastructure.PdfThumbnailGeneratorDesktop
import ru.kyamshanov.notepen.mainscreen.infrastructure.ThumbnailRepositoryDesktop
import ru.kyamshanov.notepen.mainscreen.platform.FilePicker
import ru.kyamshanov.notepen.mainscreen.ui.screen.MainScreenComponent
import ru.kyamshanov.notepen.pdf.infrastructure.JvmPdfDocumentLoader
import ru.kyamshanov.notepen.pdf.infrastructure.JvmPdfPageRenderer
import ru.kyamshanov.notepen.sync.infrastructure.KtorPeerServer
import ru.kyamshanov.notepen.sync.HostViewModel
import ru.kyamshanov.notepen.sync.SyncViewModel
import ru.kyamshanov.notepen.sync.domain.CatalogDiffOrphanDetector
import ru.kyamshanov.notepen.sync.domain.DocumentStatusCoordinator
import ru.kyamshanov.notepen.sync.domain.DocumentTransferRequestHandler
import ru.kyamshanov.notepen.sync.domain.HostAnnotationProjection
import ru.kyamshanov.notepen.sync.domain.HostHeadlessAnnotationHandler
import ru.kyamshanov.notepen.sync.domain.PendingDeltaReplayCoordinator
import ru.kyamshanov.notepen.sync.domain.RemoteCatalogClientCoordinator
import ru.kyamshanov.notepen.sync.domain.RemoteCatalogProvider
import ru.kyamshanov.notepen.sync.domain.RemoteDocumentOpener
import ru.kyamshanov.notepen.sync.domain.SyncEngineRegistry
import ru.kyamshanov.notepen.sync.infrastructure.InMemoryRemoteCatalogCache
import ru.kyamshanov.notepen.sync.infrastructure.InMemoryRemoteDocumentStatusRegistry
import ru.kyamshanov.notepen.sync.infrastructure.SqlDelightPendingDeltaQueue
import ru.kyamshanov.notepen.sync.infrastructure.createSyncDatabaseJvm
import ru.kyamshanov.notepen.createAnnotationRepository
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.model.PairingState
import ru.kyamshanov.notepen.sync.infrastructure.JmDnsDeviceDiscovery
import ru.kyamshanov.notepen.sync.infrastructure.JmDnsServiceRegistrar
import ru.kyamshanov.notepen.sync.infrastructure.KtorSyncClient
import java.net.InetAddress
import java.util.UUID

fun main() {
    // On Windows, AWT DropTarget registration (via dragAndDropTarget) conflicts with
    // Skia's DirectX/ANGLE swap chain and breaks ImageBitmap rendering.
    // Switching to OpenGL avoids the conflict while keeping hardware acceleration.
    // macOS doesn't support OPENGL via Skiko (Metal only), so scope this to Windows.
    if (System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)) {
        System.setProperty("skiko.renderApi", "OPENGL")
    }

    val lifecycle = LifecycleRegistry()
    val appScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    val selfId = UUID.randomUUID().toString()
    val selfName = runCatching { InetAddress.getLocalHost().hostName }.getOrDefault("NotePen Desktop")
    val selfInfo = DeviceInfo(id = selfId, name = selfName, host = "", port = 0)

    val pdfDocumentLoader = JvmPdfDocumentLoader(Dispatchers.IO)
    val pdfPageRenderer = JvmPdfPageRenderer(Dispatchers.IO)

    val historyRepo = FileHistoryRepositoryDesktop()
    val folderRepo = FolderRepositoryDesktop()
    val availabilityChecker = FileAvailabilityCheckerDesktop()
    val thumbnailRepo = ThumbnailRepositoryDesktop()
    val thumbnailGenerator = PdfThumbnailGeneratorDesktop()

    val peerServer = KtorPeerServer(selfInfo = selfInfo, ioDispatcher = Dispatchers.IO)
    val serviceRegistrar = JmDnsServiceRegistrar()
    val discovery = JmDnsDeviceDiscovery(Dispatchers.IO)
    val wsJson = Json { classDiscriminator = "type" }
    val httpClient = HttpClient(CIO) {
        install(WebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(wsJson)
        }
    }
    val syncClient = KtorSyncClient(httpClient)

    // Persistent offline buffer (SQLDelight-backed) — survives app restarts so
    // edits made offline still replay after a restart + reconnect.
    val syncDatabasePath = System.getProperty("user.home") + "/.notepen/sync.db"
    val syncDatabase = createSyncDatabaseJvm(syncDatabasePath)
    val pendingDeltaQueue = SqlDelightPendingDeltaQueue(
        database = syncDatabase,
        ioDispatcher = Dispatchers.IO,
    )

    // One registry per app; each open document is its own SyncEngine.
    val syncEngineRegistry = SyncEngineRegistry(
        deviceId = selfId,
        scope = appScope,
        server = peerServer,
        client = syncClient,
        pendingQueue = pendingDeltaQueue,
    )

    // Replay buffered deltas whenever either side (re)connects.
    PendingDeltaReplayCoordinator(registry = syncEngineRegistry, stateFlow = peerServer.state)
        .start(scope = appScope)
    PendingDeltaReplayCoordinator(registry = syncEngineRegistry, stateFlow = syncClient.state)
        .start(scope = appScope)

    val hostViewModel = HostViewModel(server = peerServer, scope = appScope)
    val syncViewModel = SyncViewModel(
        discovery = discovery,
        client = syncClient,
        selfInfo = selfInfo,
        scope = appScope,
    )

    // Host: build & serve the library catalog to any paired tablet, and
    // respond to on-demand DocumentOpenRequests by streaming the file.
    val remoteCatalogProvider = RemoteCatalogProvider(
        hostName = selfName,
        historyRepository = historyRepo,
        folderRepository = folderRepo,
    )
    remoteCatalogProvider.serve(server = peerServer, scope = appScope)
    DocumentTransferRequestHandler(server = peerServer, provider = remoteCatalogProvider)
        .start(scope = appScope)

    // Host headless save: tablet-originated SaveRequest /
    // AnnotationSnapshotRequest are now fulfilled without requiring the host
    // to open the document in DetailsContent. Projection follows mergedDeltas
    // (which now also mirror local edits — see SyncEngine.applyLocal).
    val hostAnnotationRepository = createAnnotationRepository()
    val hostAnnotationProjection = HostAnnotationProjection(
        registry = syncEngineRegistry,
        provider = remoteCatalogProvider,
        repository = hostAnnotationRepository,
    )
    HostHeadlessAnnotationHandler(
        server = peerServer,
        provider = remoteCatalogProvider,
        projection = hostAnnotationProjection,
        repository = hostAnnotationRepository,
    ).start(scope = appScope)

    // Tablet role (desktop may also act as one): cache the most recent
    // catalog this device received, keep it fresh on every reconnect, and
    // open Remote documents on tap.
    val receivedDir = System.getProperty("java.io.tmpdir") + "/notepen-sync"
    val remoteCatalogCache = InMemoryRemoteCatalogCache()
    RemoteCatalogClientCoordinator(client = syncClient, cache = remoteCatalogCache)
        .start(scope = appScope)
    val remoteDocumentOpener = RemoteDocumentOpener(client = syncClient, destDir = receivedDir)
    // Track per-document orphan flags as host signals come in.
    val remoteDocumentStatusRegistry = InMemoryRemoteDocumentStatusRegistry()
    DocumentStatusCoordinator(client = syncClient, registry = remoteDocumentStatusRegistry)
        .start(scope = appScope)
    // Also mark orphans passively: catalog drops a doc that has pending edits.
    CatalogDiffOrphanDetector(
        catalog = remoteCatalogCache.catalog,
        queue = pendingDeltaQueue,
        registry = remoteDocumentStatusRegistry,
    ).start(scope = appScope)

    // Demultiplex incoming stroke deltas by documentId — one engine per doc.
    appScope.launch {
        peerServer.incomingMessages.collect { msg ->
            if (msg is NetworkMessage.StrokeDeltaMessage) {
                syncEngineRegistry.get(msg.documentId).processPeer(msg.delta)
            }
        }
    }
    appScope.launch {
        syncClient.incomingMessages.collect { msg ->
            if (msg is NetworkMessage.StrokeDeltaMessage) {
                syncEngineRegistry.get(msg.documentId).processPeer(msg.delta)
            }
        }
    }

    // Register/unregister mDNS service as the server lifecycle changes.
    appScope.launch {
        peerServer.state.collect { state ->
            withContext(Dispatchers.IO) {
                when (state) {
                    is PairingState.AwaitingConnection ->
                        serviceRegistrar.register(selfInfo.copy(host = state.host, port = state.port))
                    is PairingState.Idle -> serviceRegistrar.unregister()
                    else -> Unit
                }
            }
        }
    }

    // Always create the root component outside Compose on the UI thread
    val root: RootComponent =
        runOnUiThread {
            DefaultRootComponent(
                componentContext = DefaultComponentContext(lifecycle = lifecycle),
                historyRepository = historyRepo,
                mainComponentFactory = { componentContext, onOpenEditor ->
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
                        onOpenFilePicker = { FilePicker().pickPdfFile() },
                        remoteCatalogFlow = remoteCatalogCache.catalog,
                        remoteDocumentOpener = remoteDocumentOpener,
                        pendingDeltaCounts = pendingDeltaQueue.pendingCounts(),
                        remoteDocumentStatuses = remoteDocumentStatusRegistry.statuses,
                    )
                },
            )
        }

    application {
        val windowState = rememberWindowState(
            placement = WindowPlacement.Maximized,
        )

        LifecycleController(lifecycle, windowState)

        Window(
            onCloseRequest = {
                serviceRegistrar.unregister()
                exitApplication()
            },
            state = windowState,
            title = "NotePen",
            icon = painterResource(Res.drawable.app_icon),
        ) {
            val tabletController: TabletInputController = remember {
                when {
                    Platform.isWindows() -> WinTabTabletInputController()
                    Platform.isMac() -> CocoaTabletInputController()
                    else -> NoOpTabletInputController
                }
            }
            val composeWindow: ComposeWindow = window

            // Attach the platform-specific tablet input after the window peer
            // exists. `window.isDisplayable` is guaranteed by the time the
            // first composition runs inside `Window {}`. On Windows we also
            // disable the press-and-hold gesture (spiral-defect fix) here.
            LaunchedEffect(composeWindow) {
                when (val c = tabletController) {
                    is WinTabTabletInputController -> {
                        val hwnd = HWND(Native.getComponentPointer(composeWindow))
                        WindowsPenFix.apply(hwnd)
                        c.attach(hwnd)
                    }
                    is CocoaTabletInputController -> c.attach()
                    else -> Unit
                }
            }
            DisposableEffect(Unit) {
                onDispose {
                    when (val c = tabletController) {
                        is WinTabTabletInputController -> c.stop()
                        is CocoaTabletInputController -> c.stop()
                        else -> Unit
                    }
                }
            }

            CompositionLocalProvider(LocalTabletInputController provides tabletController) {
                App(
                    rootComponent = root,
                    pdfDocumentLoader = pdfDocumentLoader,
                    pdfPageRenderer = pdfPageRenderer,
                    hostViewModel = hostViewModel,
                    syncViewModel = syncViewModel,
                    syncEngineFor = syncEngineRegistry::get,
                    peerServer = peerServer,
                    peerClient = syncClient,
                    pendingDeltaCounts = pendingDeltaQueue.pendingCounts(),
                    receivedPdfDir = receivedDir,
                )
            }
        }
    }
}
