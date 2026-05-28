import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.extensions.compose.lifecycle.LifecycleController
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.platform.win32.WinDef.HWND
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import notepen.app.bycompose.desktop.generated.resources.Res
import notepen.app.bycompose.desktop.generated.resources.app_icon
import org.jetbrains.compose.resources.painterResource
import ru.kyamshanov.notepen.App
import ru.kyamshanov.notepen.DefaultRootComponent
import ru.kyamshanov.notepen.RootComponent
import ru.kyamshanov.notepen.book.EbookAwarePdfDocumentLoader
import ru.kyamshanov.notepen.book.JvmEbookToPdfConverter
import ru.kyamshanov.notepen.createAnnotationRepository
import ru.kyamshanov.notepen.mainscreen.domain.usecase.AddToHistoryUseCase
import ru.kyamshanov.notepen.mainscreen.domain.usecase.CheckAvailabilityUseCase
import ru.kyamshanov.notepen.mainscreen.domain.usecase.OpenRecentFileUseCase
import ru.kyamshanov.notepen.mainscreen.infrastructure.FileAvailabilityCheckerDesktop
import ru.kyamshanov.notepen.mainscreen.infrastructure.FileHistoryRepositoryDesktop
import ru.kyamshanov.notepen.mainscreen.infrastructure.FolderRepositoryDesktop
import ru.kyamshanov.notepen.mainscreen.infrastructure.PdfThumbnailGeneratorDesktop
import ru.kyamshanov.notepen.mainscreen.infrastructure.ThumbnailRepositoryDesktop
import ru.kyamshanov.notepen.mainscreen.infrastructure.getAppDataDir
import ru.kyamshanov.notepen.mainscreen.platform.FilePicker
import ru.kyamshanov.notepen.mainscreen.ui.folder.FolderContentsComponentImpl
import ru.kyamshanov.notepen.mainscreen.ui.peer.PeerCatalogComponentImpl
import ru.kyamshanov.notepen.mainscreen.ui.screen.MainScreenComponent
import ru.kyamshanov.notepen.pdf.infrastructure.JvmDocumentLoader
import ru.kyamshanov.notepen.pdf.infrastructure.JvmImageDocumentLoader
import ru.kyamshanov.notepen.pdf.infrastructure.JvmImagePageRenderer
import ru.kyamshanov.notepen.pdf.infrastructure.JvmPageRenderer
import ru.kyamshanov.notepen.pdf.infrastructure.JvmPdfDocumentLoader
import ru.kyamshanov.notepen.pdf.infrastructure.JvmPdfPageRenderer
import ru.kyamshanov.notepen.qrconnect.HostQrPairingViewModel
import ru.kyamshanov.notepen.qrconnect.infrastructure.ZxingQrEncoder
import ru.kyamshanov.notepen.setupJbrTitleBar
import ru.kyamshanov.notepen.sync.domain.CatalogDiffOrphanDetector
import ru.kyamshanov.notepen.sync.domain.DocumentStatusCoordinator
import ru.kyamshanov.notepen.sync.domain.DocumentTransferRequestHandler
import ru.kyamshanov.notepen.sync.domain.HostAnnotationProjection
import ru.kyamshanov.notepen.sync.domain.HostHeadlessAnnotationHandler
import ru.kyamshanov.notepen.sync.domain.LocalCachedDocumentCleaner
import ru.kyamshanov.notepen.sync.domain.PendingDeltaReplayCoordinator
import ru.kyamshanov.notepen.sync.domain.RemoteCatalogClientCoordinator
import ru.kyamshanov.notepen.sync.domain.RemoteCatalogHostCoordinator
import ru.kyamshanov.notepen.sync.domain.RemoteCatalogProvider
import ru.kyamshanov.notepen.sync.domain.RemoteDocumentOpener
import ru.kyamshanov.notepen.sync.domain.SyncEngineRegistry
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.model.ServerLifecycleState
import ru.kyamshanov.notepen.sync.infrastructure.FileSystemLibraryManifestProvider
import ru.kyamshanov.notepen.sync.infrastructure.InMemoryCatalogChangeNotifier
import ru.kyamshanov.notepen.sync.infrastructure.InMemoryOpenDocumentRegistry
import ru.kyamshanov.notepen.sync.infrastructure.InMemoryRemoteCatalogCache
import ru.kyamshanov.notepen.sync.infrastructure.InMemoryRemoteDocumentStatusRegistry
import ru.kyamshanov.notepen.sync.infrastructure.JmDnsServiceRegistrar
import ru.kyamshanov.notepen.sync.infrastructure.JsonLocalDocumentIdRegistry
import ru.kyamshanov.notepen.sync.infrastructure.KtorPeerServer
import ru.kyamshanov.notepen.sync.infrastructure.KtorSyncClient
import ru.kyamshanov.notepen.sync.infrastructure.NotifyingFileHistoryRepository
import ru.kyamshanov.notepen.sync.infrastructure.NotifyingFolderRepository
import ru.kyamshanov.notepen.sync.infrastructure.SqlDelightPendingDeltaQueue
import ru.kyamshanov.notepen.sync.infrastructure.createSyncDatabaseJvm
import ru.kyamshanov.notepen.tablet.CocoaTabletInputController
import ru.kyamshanov.notepen.tablet.LocalTabletInputController
import ru.kyamshanov.notepen.tablet.NoOpTabletInputController
import ru.kyamshanov.notepen.tablet.TabletInputController
import ru.kyamshanov.notepen.tablet.WinTabTabletInputController
import ru.kyamshanov.notepen.tablet.WindowsPenFix
import ru.kyamshanov.notepen.tablet.WindowsPointerHook
import ru.kyamshanov.notepen.titlebar.LocalTitleBarEndInset
import ru.kyamshanov.notepen.titlebar.LocalTitleBarInteraction
import ru.kyamshanov.notepen.titlebar.LocalTitleBarStartInset
import ru.kyamshanov.notepen.titlebar.TitleBarInteraction
import java.io.File
import java.net.InetAddress
import java.util.UUID
import kotlin.system.exitProcess

/** См. [WindowsPenFix] — задержка перед повторным проходом по дереву окон Skiko. */
private const val SKIA_HWND_SETTLE_DELAY_MS: Long = 500L

/**
 * Маршрутизация запросов «открыть документ через NotePen» от ОС в навигацию приложения.
 *
 * Путь к файлу может прийти раньше, чем создан [RootComponent] (особенно на macOS,
 * где Apple Event "odoc" доставляется AWT почти сразу при старте). Поэтому до
 * установки приёмника пути буферизуются, а после — открываются сразу.
 */
private object OpenFileRouter {
    private val lock = Any()
    private val pending = mutableListOf<String>()
    private var sink: ((String) -> Unit)? = null

    /** Открыть путь, либо отложить до появления приёмника. Потокобезопасно (AWT EDT / main). */
    fun submit(path: String) {
        if (path.isBlank()) return
        val target: ((String) -> Unit)?
        synchronized(lock) {
            target = sink
            if (target == null) pending += path
        }
        target?.invoke(path)
    }

    /** Зарегистрировать приёмник и слить накопленные пути в порядке поступления. */
    fun connect(newSink: (String) -> Unit) {
        val drained: List<String>
        synchronized(lock) {
            sink = newSink
            drained = pending.toList()
            pending.clear()
        }
        drained.forEach(newSink)
    }
}

/** Расширения, которые NotePen умеет открыть из аргументов запуска (Windows/Linux). */
private val OPENABLE_EXTENSIONS = listOf("pdf", "png", "jpg", "jpeg", "epub", "fb2.zip", "fb2", "cbz", "cbr")

/**
 * Аггрегат «тяжёлых» зависимостей (сетевой стек + sync). Создаётся в фоне
 * после показа главного окна, чтобы запуск не блокировался загрузкой классов
 * Ktor/CIO/jmDNS/zxing и инициализацией CIO-движка.
 *
 * Пока `null`, экраны корректно работают: peer/sync-параметры [App] — nullable,
 * `pendingDeltaCounts` тоже, а `remoteCatalogCache.catalogs` уже пустая мапа.
 */
private class HeavyDeps(
    val peerServer: KtorPeerServer,
    val syncClient: KtorSyncClient,
    val syncEngineRegistry: SyncEngineRegistry,
    val pendingDeltaQueue: SqlDelightPendingDeltaQueue,
    val hostQrViewModel: HostQrPairingViewModel,
    val remoteDocumentOpener: RemoteDocumentOpener,
    val hostAnnotationProjection: HostAnnotationProjection,
    val serviceRegistrar: JmDnsServiceRegistrar,
)

fun main(args: Array<String>) {
    // Windows/Linux: jpackage-лаунчер передаёт открываемый файл аргументом.
    // (macOS использует Apple Event "odoc", регистрируем OpenFileHandler ниже.)
    args
        .firstOrNull { arg -> OPENABLE_EXTENSIONS.any { arg.endsWith(".$it", ignoreCase = true) } }
        ?.let { OpenFileRouter.submit(java.io.File(it).absolutePath) }

    // Must be set BEFORE any AWT class is loaded — Taskbar.isTaskbarSupported() already
    // initialises AWT Toolkit, so this has to come first.
    // The system property is read by the macOS AWT port when it first touches NSApplication
    // and stays in effect for the whole process lifetime, including during shutdown.
    if (Platform.isMac()) {
        System.setProperty("apple.awt.application.name", "NotePen")
        runCatching {
            val resourcePath = "composeResources/notepen.app.bycompose.desktop.generated.resources/files/app_icon_dock.png"
            Thread
                .currentThread()
                .contextClassLoader
                .getResource(resourcePath)
                ?.takeIf { it.protocol == "file" }
                ?.let { java.io.File(it.toURI()).absolutePath }
                ?.let { System.setProperty("apple.awt.application.icon", it) }
        }

        // macOS доставляет открываемый файл не через argv, а Apple Event "odoc",
        // который AWT превращает в OpenFileHandler. Регистрируем до создания окна,
        // иначе событие, пришедшее при холодном старте, потеряется.
        runCatching {
            if (java.awt.Desktop.isDesktopSupported()) {
                val desktop = java.awt.Desktop.getDesktop()
                if (desktop.isSupported(java.awt.Desktop.Action.APP_OPEN_FILE)) {
                    desktop.setOpenFileHandler { event ->
                        event.files.forEach { OpenFileRouter.submit(it.absolutePath) }
                    }
                }
            }
        }
    }

    val lifecycle = LifecycleRegistry()
    val appScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    val selfId = UUID.randomUUID().toString()
    val selfName = runCatching { InetAddress.getLocalHost().hostName }.getOrDefault("NotePen Desktop")
    val selfInfo = DeviceInfo(id = selfId, name = selfName, host = "", port = 0)

    // Один экземпляр конвертера: он же поставляет оглавление (реализует
    // DocumentOutlineProvider) для сайдбара «Содержание».
    val ebookConverter = JvmEbookToPdfConverter(Dispatchers.IO)
    val pdfDocumentLoader =
        EbookAwarePdfDocumentLoader(
            delegate =
                JvmDocumentLoader(
                    pdfLoader = JvmPdfDocumentLoader(Dispatchers.IO),
                    imageLoader = JvmImageDocumentLoader(Dispatchers.IO),
                ),
            converter = ebookConverter,
        )
    val pdfPageRenderer =
        JvmPageRenderer(
            pdfRenderer = JvmPdfPageRenderer(Dispatchers.IO),
            imageRenderer = JvmImagePageRenderer(Dispatchers.IO),
        )

    val catalogChangeNotifier = InMemoryCatalogChangeNotifier()
    val historyRepo =
        NotifyingFileHistoryRepository(
            delegate = FileHistoryRepositoryDesktop(),
            notifier = catalogChangeNotifier,
        )
    val folderRepo =
        NotifyingFolderRepository(
            delegate = FolderRepositoryDesktop(),
            notifier = catalogChangeNotifier,
        )
    val availabilityChecker = FileAvailabilityCheckerDesktop()
    val thumbnailRepo = ThumbnailRepositoryDesktop()
    // Эскиз книги (EPUB/FB2/комикс) строится по её PDF-версии — конвертируем тем
    // же кешом, что и при открытии, иначе loadPDF падает на сыром ebook'е.
    val thumbnailGenerator = PdfThumbnailGeneratorDesktop(converter = ebookConverter, ioDispatcher = Dispatchers.IO)

    // Light, sync-only refs that the main screen depends on for the very first
    // frame. The library root + manifest provider are needed by the heavy
    // RemoteCatalogProvider, but their construction is cheap (no I/O beyond
    // mkdirs / canonicalFile), so we keep them on the main path.
    val libraryRoot =
        System.getProperty("notepen.library.root")
            ?.let(::File)
            ?: File(System.getProperty("user.home"), "NotePen Library")
    libraryRoot.mkdirs()
    val libraryManifestProvider =
        FileSystemLibraryManifestProvider(
            root = libraryRoot,
            isBook = { file ->
                val name = file.name.lowercase()
                OPENABLE_EXTENSIONS.any { name.endsWith(".$it") }
            },
        )

    // In-memory state holders are cheap; we expose them to the UI from the
    // light path so that the main screen can subscribe before the sync stack
    // finishes wiring. The heavy coroutine below fills these as peers connect.
    val remoteCatalogCache = InMemoryRemoteCatalogCache()
    val remoteDocumentStatusRegistry = InMemoryRemoteDocumentStatusRegistry()
    val openDocumentRegistry = InMemoryOpenDocumentRegistry()

    val receivedDir = System.getProperty("java.io.tmpdir") + "/notepen-sync"
    val localDocumentIdRegistry =
        JsonLocalDocumentIdRegistry(
            manifestPath = "$receivedDir/.notepen-doc-ids.json",
            ioDispatcher = Dispatchers.IO,
        )

    // "Online peers" union — proxied through a MutableStateFlow so we can
    // expose it to MainScreen synchronously and start piping real values into
    // it once peerServer / syncClient exist.
    val onlinePeerIdsFlow = MutableStateFlow<Set<String>>(emptySet())
    // Holder for the heavy sync stack. Stays null until the background
    // coroutine below finishes; consumers (App, factories) tolerate null.
    val heavyDepsFlow = MutableStateFlow<HeavyDeps?>(null)

    // Build the heavy sync/network stack off the UI thread so the main window
    // can appear immediately. Touching these refs causes class-loading of
    // Ktor server/client, CIO engine, jmDNS and zxing — collectively the
    // biggest chunk of cold-start time on desktop.
    appScope.launch(Dispatchers.IO) {
        val peerServer = KtorPeerServer(selfInfo = selfInfo, ioDispatcher = Dispatchers.IO)
        val serviceRegistrar = JmDnsServiceRegistrar()
        val wsJson = Json { classDiscriminator = "type" }
        val httpClient =
            HttpClient(CIO) {
                install(WebSockets) {
                    contentConverter = KotlinxWebsocketSerializationConverter(wsJson)
                }
                // Bound only the TCP connect/handshake (NOT the long-lived WS session) so
                // a reconnect to a vanished host fails fast and the reconnect deadline is
                // honoured instead of hanging in "reconnecting".
                install(io.ktor.client.plugins.HttpTimeout) {
                    connectTimeoutMillis = 4_000L
                }
            }
        val syncClient = KtorSyncClient(httpClient)

        // Persistent offline buffer (SQLDelight-backed) — survives app restarts so
        // edits made offline still replay after a restart + reconnect.
        val syncDatabasePath = getAppDataDir().resolve("sync.db").absolutePath
        val pendingDeltaQueue =
            SqlDelightPendingDeltaQueue(
                databaseProvider = { createSyncDatabaseJvm(syncDatabasePath) },
                ioDispatcher = Dispatchers.IO,
            )

        val syncEngineRegistry =
            SyncEngineRegistry(
                deviceId = selfId,
                scope = appScope,
                server = peerServer,
                client = syncClient,
                pendingQueue = pendingDeltaQueue,
            )

        val hostPeerConnected =
            peerServer.connectedPeers
                .distinctUntilChanged()
                .filter { it.isNotEmpty() }
                .map { }
        val clientConnected =
            syncClient.connectedHosts
                .distinctUntilChanged()
                .filter { it.isNotEmpty() }
                .map { }
        PendingDeltaReplayCoordinator(
            registry = syncEngineRegistry,
            connectionEstablished = hostPeerConnected,
        ).start(scope = appScope)
        PendingDeltaReplayCoordinator(
            registry = syncEngineRegistry,
            connectionEstablished = clientConnected,
        ).start(scope = appScope)

        val hostQrViewModel =
            HostQrPairingViewModel(
                peerServer = peerServer,
                qrEncoder = ZxingQrEncoder(),
                hostDeviceName = selfName,
                scope = appScope,
            )

        val remoteCatalogProvider =
            RemoteCatalogProvider(
                hostName = selfName,
                manifestProvider = libraryManifestProvider,
                folderRepository = folderRepo,
            )
        remoteCatalogProvider.serve(server = peerServer, scope = appScope)
        remoteCatalogProvider.serve(client = syncClient, scope = appScope)
        remoteCatalogProvider.broadcastChanges(
            notifier = catalogChangeNotifier,
            server = peerServer,
            client = syncClient,
            scope = appScope,
        )
        DocumentTransferRequestHandler(server = peerServer, provider = remoteCatalogProvider)
            .start(scope = appScope)

        val hostAnnotationRepository = createAnnotationRepository()
        val hostAnnotationProjection =
            HostAnnotationProjection(
                registry = syncEngineRegistry,
                provider = remoteCatalogProvider,
                repository = hostAnnotationRepository,
            )
        HostHeadlessAnnotationHandler(
            server = peerServer,
            provider = remoteCatalogProvider,
            projection = hostAnnotationProjection,
        ).start(scope = appScope)

        RemoteCatalogClientCoordinator(client = syncClient, cache = remoteCatalogCache)
            .start(scope = appScope)
        RemoteCatalogHostCoordinator(server = peerServer, cache = remoteCatalogCache)
            .start(scope = appScope)
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

        // Pipe combined connected hosts + peers into the public onlinePeerIdsFlow.
        appScope.launch {
            combine(
                syncClient.connectedHosts,
                peerServer.connectedPeers,
            ) { hosts, peers ->
                (hosts.map { it.id } + peers.map { it.id }).toSet()
            }.collect { onlinePeerIdsFlow.value = it }
        }

        // Demultiplex incoming stroke deltas by documentId — one engine per doc.
        appScope.launch {
            peerServer.incomingMessages.collect { peerMessage ->
                val msg = peerMessage.message
                if (msg is NetworkMessage.StrokeDeltaMessage) {
                    syncEngineRegistry.get(msg.documentId).processPeer(msg.delta)
                    // Накапливаем штрих в headless-проекции сразу, не дожидаясь
                    // первого SaveRequest — иначе правки, сделанные на планшете до
                    // запроса сохранения, не попадут в сохранённый на хосте файл.
                    hostAnnotationProjection.ingestPeerDelta(msg.documentId, msg.delta, appScope)
                }
            }
        }
        appScope.launch {
            syncClient.incomingMessages.collect { hostMessage ->
                val msg = hostMessage.message
                if (msg is NetworkMessage.StrokeDeltaMessage) {
                    syncEngineRegistry.get(msg.documentId).processPeer(msg.delta)
                    hostAnnotationProjection.ingestPeerDelta(msg.documentId, msg.delta, appScope)
                }
            }
        }

        // Register/unregister mDNS service as the server lifecycle changes.
        appScope.launch {
            peerServer.lifecycle.collect { state ->
                withContext(Dispatchers.IO) {
                    when (state) {
                        is ServerLifecycleState.Running ->
                            serviceRegistrar.register(selfInfo.copy(host = state.host, port = state.port))
                        is ServerLifecycleState.Idle,
                        is ServerLifecycleState.Stopped,
                        -> serviceRegistrar.unregister()
                        else -> Unit
                    }
                }
            }
        }

        heavyDepsFlow.value =
            HeavyDeps(
                peerServer = peerServer,
                syncClient = syncClient,
                syncEngineRegistry = syncEngineRegistry,
                pendingDeltaQueue = pendingDeltaQueue,
                hostQrViewModel = hostQrViewModel,
                remoteDocumentOpener = remoteDocumentOpener,
                hostAnnotationProjection = hostAnnotationProjection,
                serviceRegistrar = serviceRegistrar,
            )
    }

    // Always create the root component outside Compose on the UI thread
    val root: RootComponent =
        runOnUiThread {
            DefaultRootComponent(
                componentContext = DefaultComponentContext(lifecycle = lifecycle),
                historyRepository = historyRepo,
                mainComponentFactory = { componentContext, onOpenEditor, onOpenPeerCatalog, onOpenFolder ->
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
                        onOpenFilePicker = { FilePicker().pickDocument() },
                        onOpenPeerCatalog = onOpenPeerCatalog,
                        onOpenFolder = onOpenFolder,
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
                        onOpenFilePicker = { FilePicker().pickDocument() },
                        onOpenEditor = onOpenEditor,
                        onOpenFolder = onOpenFolder,
                    )
                },
            )
        }

    // Корень готов: открываем отложенные PDF (запуск «открыть с помощью») и все
    // последующие запросы ОС. openDetailsExternally мутирует навигацию — только на UI-потоке.
    OpenFileRouter.connect { path -> runOnUiThread { root.openDetailsExternally(path) } }

    application {
        val windowState =
            rememberWindowState(
                placement = WindowPlacement.Maximized,
            )

        LifecycleController(lifecycle, windowState)

        Window(
            onCloseRequest = {
                heavyDepsFlow.value?.serviceRegistrar?.unregister()
                // exitApplication() tears down Compose/AWT asynchronously, leaving the JVM
                // alive long enough for macOS to flash the parent process icon (Terminal.app)
                // in the Dock. exitProcess terminates the JVM immediately after cleanup.
                exitProcess(0)
            },
            state = windowState,
            title = "NotePen",
            icon = painterResource(Res.drawable.app_icon),
        ) {
            val tabletController: TabletInputController =
                remember {
                    when {
                        Platform.isWindows() -> WinTabTabletInputController()
                        Platform.isMac() -> CocoaTabletInputController()
                        else -> NoOpTabletInputController
                    }
                }
            val composeWindow: ComposeWindow = window

            var titleBarInteraction by remember { mutableStateOf<TitleBarInteraction?>(null) }
            var titleBarStartInset by remember { mutableStateOf(0.dp) }
            var titleBarEndInset by remember { mutableStateOf(0.dp) }

            LaunchedEffect(composeWindow) {
                setupJbrTitleBar(composeWindow)?.let { setup ->
                    titleBarInteraction = setup.interaction
                    titleBarStartInset = setup.startInset
                    titleBarEndInset = setup.endInset
                }
                // setCustomTitleBar drops the window out of the maximized placement it
                // was created with, yet windowState stays stale-Maximized — so simply
                // re-assigning windowState.placement is a no-op and Compose never
                // re-applies it. Maximize the AWT frame directly, then bring it to
                // front. (Raw extendedState was unsafe before because it recreated the
                // GPU swap chain while an AWT DropTarget was registered, dropping
                // thumbnail ImageBitmaps — but Windows DnD, and its DropTarget, is now
                // disabled, so that conflict is gone.)
                composeWindow.extendedState = java.awt.Frame.MAXIMIZED_BOTH
                composeWindow.toFront()
            }

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
                        // Skiko создаёт SkiaLayer Canvas asynchronously: на момент
                        // первого composition'а child HWND'ы могут ещё не существовать,
                        // и EnumChildWindows пройдёт мимо реального target'а пера.
                        // Re-apply через короткий delay ловит поздно-созданные окна.
                        // (Не идеально, но проще, чем подвешиваться на HierarchyListener.)
                        kotlinx.coroutines.delay(SKIA_HWND_SETTLE_DELAY_MS)
                        WindowsPenFix.apply(hwnd)
                        // DIAG: subclass WndProc на root + child'ы, чтобы посмотреть,
                        // приходят ли вообще WM_POINTER сообщения (для теории «AWT
                        // synthesizes WM_MOUSE из них с задержкой 400мс»).
                        WindowsPointerHook.install(hwnd)
                    }
                    is CocoaTabletInputController -> c.attach()
                    else -> Unit
                }
            }
            DisposableEffect(Unit) {
                onDispose {
                    WindowsPointerHook.uninstall()
                    when (val c = tabletController) {
                        is WinTabTabletInputController -> c.stop()
                        is CocoaTabletInputController -> c.stop()
                        else -> Unit
                    }
                }
            }

            // Heavy deps swap in once the background wiring coroutine finishes;
            // the App composable handles null gracefully for all sync params.
            val heavyDeps by heavyDepsFlow.collectAsState()

            CompositionLocalProvider(
                LocalTitleBarInteraction provides titleBarInteraction,
                LocalTitleBarStartInset provides titleBarStartInset,
                LocalTitleBarEndInset provides titleBarEndInset,
                LocalTabletInputController provides tabletController,
            ) {
                App(
                    rootComponent = root,
                    pdfDocumentLoader = pdfDocumentLoader,
                    pdfPageRenderer = pdfPageRenderer,
                    outlineProvider = ebookConverter,
                    syncEngineFor = heavyDeps?.syncEngineRegistry?.let { reg -> reg::get },
                    peerServer = heavyDeps?.peerServer,
                    peerClient = heavyDeps?.syncClient,
                    pendingDeltaCounts = heavyDeps?.pendingDeltaQueue?.pendingCounts(),
                    hostQrViewModel = heavyDeps?.hostQrViewModel,
                    // Client VMs are null on desktop — the scan / manual panes
                    // stay hidden, leaving only the host QR panel.
                    clientScanViewModel = null,
                    manualConnectViewModel = null,
                    receivedPdfDir = receivedDir,
                    openDocumentRegistry = openDocumentRegistry,
                    localDocumentIdRegistry = localDocumentIdRegistry,
                    // При открытии документа на ПК подмешиваем самое свежее
                    // состояние из headless-проекции (in-memory), а не только
                    // диск — так заметка, только что сделанная на планшете, видна
                    // сразу, без гонки с дисковым flush'ем.
                    hostAnnotationSnapshotFor =
                        heavyDeps?.hostAnnotationProjection?.let { projection ->
                            { docId -> projection.snapshotDtos(docId).orEmpty() }
                        },
                )
            }
        }
    }
}
