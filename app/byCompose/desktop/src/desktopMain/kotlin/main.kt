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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import notepen.app.bycompose.desktop.generated.resources.Res
import notepen.app.bycompose.desktop.generated.resources.app_icon
import org.jetbrains.compose.resources.painterResource
import ru.kyamshanov.notepen.App
import ru.kyamshanov.notepen.DefaultRootComponent
import ru.kyamshanov.notepen.RootComponent
import ru.kyamshanov.notepen.book.EbookAwarePdfDocumentLoader
import ru.kyamshanov.notepen.book.JvmEbookToPdfConverter
import ru.kyamshanov.notepen.mainscreen.domain.usecase.AddToHistoryUseCase
import ru.kyamshanov.notepen.mainscreen.domain.usecase.CheckAvailabilityUseCase
import ru.kyamshanov.notepen.mainscreen.domain.usecase.OpenRecentFileUseCase
import ru.kyamshanov.notepen.mainscreen.infrastructure.FileAvailabilityCheckerDesktop
import ru.kyamshanov.notepen.mainscreen.infrastructure.FileHistoryRepositoryDesktop
import ru.kyamshanov.notepen.mainscreen.infrastructure.FileSystemLibraryFolder
import ru.kyamshanov.notepen.mainscreen.infrastructure.FolderRepositoryDesktop
import ru.kyamshanov.notepen.mainscreen.infrastructure.PdfThumbnailGeneratorDesktop
import ru.kyamshanov.notepen.mainscreen.infrastructure.ThumbnailRepositoryDesktop
import ru.kyamshanov.notepen.mainscreen.infrastructure.getAppDataDir
import ru.kyamshanov.notepen.mainscreen.platform.FilePicker
import ru.kyamshanov.notepen.mainscreen.ui.folder.FolderContentsComponentImpl
import ru.kyamshanov.notepen.mainscreen.ui.library.LibraryFolderContentsComponentImpl
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
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.infrastructure.FileSystemLibraryManifestProvider
import ru.kyamshanov.notepen.sync.infrastructure.InMemoryCatalogChangeNotifier
import ru.kyamshanov.notepen.sync.infrastructure.InMemoryOpenDocumentRegistry
import ru.kyamshanov.notepen.sync.infrastructure.InMemoryRemoteCatalogCache
import ru.kyamshanov.notepen.sync.infrastructure.InMemoryRemoteDocumentStatusRegistry
import ru.kyamshanov.notepen.sync.infrastructure.JsonLocalDocumentIdRegistry
import ru.kyamshanov.notepen.sync.infrastructure.NotifyingFileHistoryRepository
import ru.kyamshanov.notepen.sync.infrastructure.NotifyingFolderRepository
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
    val isBook: (File) -> Boolean = { file ->
        val name = file.name.lowercase()
        OPENABLE_EXTENSIONS.any { name.endsWith(".$it") }
    }
    val libraryManifestProvider =
        FileSystemLibraryManifestProvider(
            root = libraryRoot,
            isBook = isBook,
        )
    val libraryFolder =
        FileSystemLibraryFolder(
            root = libraryRoot,
            isBook = isBook,
            notifier = catalogChangeNotifier,
            scope = appScope,
            ioDispatcher = Dispatchers.IO,
        )

    // In-memory state holders are cheap; we expose them to the UI from the
    // light path so that the main screen can subscribe before the sync stack
    // finishes wiring. SyncRuntime fills these as peers connect once the user
    // enables sync via the QR pairing button.
    val remoteCatalogCache = InMemoryRemoteCatalogCache()
    val remoteDocumentStatusRegistry = InMemoryRemoteDocumentStatusRegistry()
    val openDocumentRegistry = InMemoryOpenDocumentRegistry()

    val receivedDir = System.getProperty("java.io.tmpdir") + "/notepen-sync"
    val localDocumentIdRegistry =
        JsonLocalDocumentIdRegistry(
            manifestPath = "$receivedDir/.notepen-doc-ids.json",
            ioDispatcher = Dispatchers.IO,
        )

    // Lazy owner of the heavy sync/network stack: Ktor server/client, CIO engine,
    // jmDNS registrar, SQLite pending-delta queue, projection, coordinators —
    // all build inside SyncRuntime.enable(), and are torn down on disable().
    // Until the user opens "Разрешить подключение по QR" the runtime stays idle
    // (no class-loading, no log noise from catalog broadcast / projection).
    val syncRuntime =
        SyncRuntime(
            selfInfo = selfInfo,
            selfId = selfId,
            selfName = selfName,
            parentScope = appScope,
            receivedDir = receivedDir,
            syncDatabasePath = getAppDataDir().resolve("sync.db").absolutePath,
            libraryManifestProvider = libraryManifestProvider,
            folderRepository = folderRepo,
            catalogChangeNotifier = catalogChangeNotifier,
            remoteCatalogCache = remoteCatalogCache,
            remoteDocumentStatusRegistry = remoteDocumentStatusRegistry,
            openDocumentRegistry = openDocumentRegistry,
            localDocumentIdRegistry = localDocumentIdRegistry,
        )

    // The QR pairing VM stays light: it only holds state flows for the panel
    // and forwards user actions. The peer-server reference is fetched lazily on
    // start() — that's the moment we trigger SyncRuntime.enable(). stopServer()
    // tears the runtime back down.
    val hostQrViewModel =
        HostQrPairingViewModel(
            peerServerProvider = { syncRuntime.enable().peerServer },
            qrEncoder = ZxingQrEncoder(),
            hostDeviceName = selfName,
            scope = appScope,
            onStop = { syncRuntime.disable() },
        )

    // Single instance of the combined online-peers flow. Empty when the runtime
    // is disabled; flips to the live host+client union once enable() lands.
    val onlinePeerIdsFlow = syncRuntime.onlinePeerIds()

    // Always create the root component outside Compose on the UI thread
    val root: RootComponent =
        runOnUiThread {
            DefaultRootComponent(
                componentContext = DefaultComponentContext(lifecycle = lifecycle),
                historyRepository = historyRepo,
                mainComponentFactory = { componentContext, onOpenEditor, onOpenPeerCatalog, onOpenFolder, onOpenLib ->
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
                        onOpenLibraryFolder = onOpenLib,
                        remoteCatalogsFlow = remoteCatalogCache.catalogs,
                        onlinePeerIdsFlow = onlinePeerIdsFlow,
                        libraryFolder = libraryFolder,
                    )
                },
                peerCatalogComponentFactory = { ctx, peerId, displayName, onBack, onOpenEditor ->
                    PeerCatalogComponentImpl(
                        componentContext = ctx,
                        peerId = peerId,
                        displayName = displayName,
                        catalogsFlow = remoteCatalogCache.catalogs,
                        onlinePeerIdsFlow = onlinePeerIdsFlow,
                        // The only way the user navigates into the peer catalog is by
                        // having peers in the cache, which requires sync to be on. By
                        // then SyncRuntime.session is set and the opener is live.
                        remoteDocumentOpener = syncRuntime.session.value?.remoteDocumentOpener,
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
                libraryFolderComponentFactory = { ctx, onBack, onOpenEditor ->
                    LibraryFolderContentsComponentImpl(
                        componentContext = ctx,
                        libraryFolder = libraryFolder,
                        onBack = onBack,
                        onOpenEditor = onOpenEditor,
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
                syncRuntime.session.value?.serviceRegistrar?.unregister()
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

            // SyncSession is null until the user enables sync via the QR
            // button; the App composable handles null gracefully for all sync
            // params and recomposes downstream consumers when it flips.
            val syncSession by syncRuntime.session.collectAsState()
            // pendingDeltaCounts is a single stable flow — null when no offline
            // queue exists yet. (We could pass the runtime's flow unconditionally,
            // but App accepts null and a missing queue means "no banner".)
            val pendingDeltaCounts =
                remember(syncSession) { syncSession?.pendingDeltaQueue?.pendingCounts() }

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
                    syncEngineFor = syncSession?.syncEngineRegistry?.let { reg -> reg::get },
                    peerServer = syncSession?.peerServer,
                    peerClient = syncSession?.syncClient,
                    pendingDeltaCounts = pendingDeltaCounts,
                    // hostQrViewModel is constructed eagerly with a lazy
                    // peerServerProvider; the panel is usable from a cold start
                    // even though the heavy stack isn't built yet.
                    hostQrViewModel = hostQrViewModel,
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
                        syncSession?.hostAnnotationProjection?.let { projection ->
                            { docId -> projection.snapshotDtos(docId).orEmpty() }
                        },
                )
            }
        }
    }
}
