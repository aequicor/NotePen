package ru.kyamshanov.notepen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.material3.Surface
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.model.EraserSettings
import ru.kyamshanov.notepen.annotation.domain.model.MarkerSettings
import ru.kyamshanov.notepen.annotation.domain.model.PageExtent
import ru.kyamshanov.notepen.annotation.domain.model.PenSettings
import ru.kyamshanov.notepen.annotation.domain.port.AnnotationRepository as SharedAnnotationRepository
import ru.kyamshanov.notepen.ui.glass.GlassSurface
import ru.kyamshanov.notepen.pdf.domain.model.PdfDocument
import ru.kyamshanov.notepen.pdf.domain.port.PdfDocumentLoader
import ru.kyamshanov.notepen.pdf.domain.port.PdfPageRenderer
import ru.kyamshanov.notepen.sync.SyncBridge
import ru.kyamshanov.notepen.sync.domain.SyncEngine
import ru.kyamshanov.notepen.sync.domain.documentIdFromFilePath
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.model.PairingState
import ru.kyamshanov.notepen.sync.domain.model.RectDto
import ru.kyamshanov.notepen.sync.domain.model.StrokeDelta
import ru.kyamshanov.notepen.sync.domain.model.toDomain
import ru.kyamshanov.notepen.sync.domain.model.toDto
import ru.kyamshanov.notepen.sync.domain.port.PeerServer
import ru.kyamshanov.notepen.sync.domain.port.SyncClient
import ru.kyamshanov.notepen.magnifier.MagnifierInputPanel
import ru.kyamshanov.notepen.magnifier.MagnifierState
import ru.kyamshanov.notepen.pdfviewer.PdfPagesViewer
import ru.kyamshanov.notepen.pdfviewer.PdfViewerState
import ru.kyamshanov.notepen.pdfviewer.rememberPdfViewerState
import ru.kyamshanov.notepen.tablet.LocalTabletInputController
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.geometry.Size
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

internal const val BACK_CONTENT_DESCRIPTION = "Назад"

/**
 * Длительность анимации показа/скрытия сайдбара миниатюр. В портрете
 * этим же tween'ом синхронно сдвигается PDF + top bar — иначе spring у
 * `animateDpAsState` отставал от slideOutHorizontally сайдбара и при
 * закрытии возникал визуальный лаг.
 */
private const val SIDEBAR_ANIM_DURATION_MS = 220

/** Toolbar `+` button zoom factor (matches Ctrl+wheel zoom-in). */
private const val TOOLBAR_ZOOM_STEP_IN = 1.1f

/** Toolbar `−` button zoom factor (matches Ctrl+wheel zoom-out). */
private const val TOOLBAR_ZOOM_STEP_OUT = 1f / TOOLBAR_ZOOM_STEP_IN

/** Сколько ждём слива offline-буфера на пир после реконнекта, прежде чем считать неуспехом. */
private val REPLAY_DEADLINE = 10.seconds


@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DetailsContent(
    component: DetailsComponent,
    loader: PdfDocumentLoader,
    renderer: PdfPageRenderer,
    /**
     * Factory that resolves the [SyncEngine] bound to the given `documentId`.
     * Wired to [ru.kyamshanov.notepen.sync.domain.SyncEngineRegistry] at the
     * application root. Null when sync is disabled (e.g. single-device mode).
     */
    syncEngineFor: ((documentId: String) -> SyncEngine)? = null,
    @Suppress("UNUSED_PARAMETER")
    peerServer: PeerServer? = null,
    peerClient: SyncClient? = null,
    /**
     * Stream of `documentId → pendingCount` from the offline buffer.
     * Drives the "Оффлайн, N правок ждут отправки" banner shown when
     * [peerClient] is not [PairingState.Connected].
     */
    pendingDeltaCounts: kotlinx.coroutines.flow.Flow<Map<String, Int>>? = null,
    /**
     * Directory in which [ru.kyamshanov.notepen.sync.domain.RemoteDocumentOpener]
     * caches remote-opened PDFs. When the current [filePath] sits inside it the
     * editor treats the doc as remote-cached — used to gate the disconnect
     * snackbar (no point telling local-only users about a "lost connection").
     */
    receivedPdfDir: String? = null,
    /**
     * Registry, в который мы анонсируем «документ открыт». Сервис
     * [ru.kyamshanov.notepen.sync.domain.LocalCachedDocumentCleaner] отложит
     * удаление кеш-копии пока документ держится здесь.
     */
    openDocumentRegistry: ru.kyamshanov.notepen.sync.domain.port.OpenDocumentRegistry? = null,
    /**
     * Mapping `localPath → documentId` для remote-кешированных PDF. Нужен
     * чтобы tablet использовал тот же documentId, что host (а не вычислял
     * заново из локального пути — это давало бы другой hash и ломало синк).
     */
    localDocumentIdRegistry: ru.kyamshanov.notepen.sync.domain.port.LocalDocumentIdRegistry? = null,
    modifier: Modifier = Modifier,
) {
    val localWindowInfo = LocalWindowInfo.current
    val windowSizeInPx = localWindowInfo.containerSize
    val isLandscape = windowSizeInPx.width > windowSizeInPx.height
    val model by component.model.subscribeAsState()
    val filePath = remember(model.title) { model.title }
    val documentId = remember(filePath, localDocumentIdRegistry, receivedPdfDir) {
        // Для файлов, скачанных с пира, documentId должен быть тем, который
        // прислал host — иначе hash будет посчитан от tablet'ского пути и не
        // совпадёт с тем, что host использует у себя.
        val fromRegistry = if (receivedPdfDir != null && filePath.startsWith(receivedPdfDir)) {
            localDocumentIdRegistry?.lookup(filePath)
        } else {
            null
        }
        fromRegistry ?: documentIdFromFilePath(filePath)
    }
    val syncEngine = remember(syncEngineFor, documentId) {
        syncEngineFor?.invoke(documentId)
    }

    var pdfDocument by remember(filePath) { mutableStateOf<PdfDocument?>(null) }
    val pages by remember { derivedStateOf { pdfDocument?.info?.pages ?: emptyList() } }

    var toolMode by remember { mutableStateOf(ToolMode.NONE) }
    val tabletController = LocalTabletInputController.current
    val barrelPressed by tabletController.barrelPressed.collectAsState()
    val eraserTipActive by tabletController.eraserTipActive.collectAsState()
    // Hold-to-erase: while the pen's barrel button is held *or* the eraser tip
    // is touching the screen (e.g. flipped S-Pen), override the active tool
    // with ERASER. Releasing either returns to the user-selected tool. Because
    // `toolMode` is a key of `pointerInput` in DrawablePdfPage, the gesture
    // handler restarts on the override flip — any in-flight stroke is finalised
    // cleanly via the existing `LaunchedEffect(toolMode)` path.
    val eraserOverride = barrelPressed || eraserTipActive
    // Do NOT remap `toolMode` to ERASER on override — that flip restarts the
    // `pointerInput` block mid-gesture, and the new ERASER block's
    // `awaitFirstDown` misses the still-held stylus DOWN, so the stylus
    // eraser tip / barrel button silently does nothing until the user lifts
    // and re-presses. Instead, `DrawablePdfPage` reads `eraserOverride`
    // dynamically at gesture start (see its `eraserOverride` parameter) and
    // routes the gesture to the erase pipeline without restarting.
    val effectiveToolMode = toolMode
    var penSettings by remember { mutableStateOf(PenSettings()) }
    var markerSettings by remember { mutableStateOf(MarkerSettings()) }
    var eraserSettings by remember { mutableStateOf(EraserSettings()) }
    // Pencil Mode: пока активен — palm-rejection форсирован, рисует только
    // стилус, палец проходит сквозь на pan / pinch.
    var pencilModeEnabled by remember { mutableStateOf(false) }
    // Пользователь хотя бы раз руками щёлкнул по toggle. После этого
    // авто-логика по stylus-присутствию замолкает: ручной выбор уважается
    // до конца сессии, иначе ON/OFF постоянно бы перебивался состоянием
    // пера.
    var pencilModeManuallyTouched by remember { mutableStateOf(false) }
    val stylusEverSeen by tabletController.stylusEverSeen.collectAsState()
    LaunchedEffect(stylusEverSeen, pencilModeManuallyTouched) {
        if (pencilModeManuallyTouched) return@LaunchedEffect
        // Включаем по первому stylus-событию и снимаем, если контроллер
        // решил, что перо «ушло» (см. AndroidTabletInputController —
        // recovery edge для зависших S-Pen). Без авто-выключения Pencil
        // Mode оставался бы латчем и блокировал ввод пальцем до ребута.
        pencilModeEnabled = stylusEverSeen
    }
    var showThumbnails by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    val drawingStates = remember { mutableStateMapOf<Int, PdfDrawingState>() }
    val favoritePageIndices = remember { mutableStateListOf<Int>() }
    val hasAnnotations by remember {
        derivedStateOf { drawingStates.values.any { it.currentPaths.isNotEmpty() } }
    }
    val annotatedPageIndices by remember {
        derivedStateOf {
            drawingStates.asSequence()
                .filter { (_, state) -> state.currentPaths.isNotEmpty() }
                .map { (pageIndex, _) -> pageIndex }
                .toSet()
        }
    }
    // Единственный источник правды по позиции и зуму на обеих платформах.
    // Платформенные различия живут внутри expect/actual [PdfPagesViewer] +
    // [PdfViewerState]; здесь они не видны.
    val pdfViewerState: PdfViewerState = rememberPdfViewerState()
    // Прокидываем источник правды по [PageExtent] страницы в viewer-state.
    // derivedStateOf внутри layout пересчитает размеры слотов при росте
    // extent у любого PdfDrawingState.
    SideEffect {
        pdfViewerState.pageExtentProvider = { pageIndex ->
            drawingStates[pageIndex]?.extent?.value ?: PageExtent.Pdf
        }
    }
    val firstVisiblePage by remember {
        derivedStateOf { pdfViewerState.firstVisiblePageIndex }
    }
    val currentScalePercent: Int by remember {
        derivedStateOf { pdfViewerState.scalePercent }
    }
    val currentPageOffsetPx: Int by remember {
        derivedStateOf { pdfViewerState.firstVisiblePageOffsetPx }
    }
    val globalUndoStack = remember { ArrayDeque<Pair<Int, List<DrawingPath>>>() }
    val globalRedoStack = remember { ArrayDeque<Pair<Int, List<DrawingPath>>>() }
    // Состояние magnifier'а (рамка-цель + плавающая панель ввода). Создаётся
    // один раз; включается toolbar-кнопкой ниже.
    val magnifierState = remember { MagnifierState() }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val annotationRepository = remember { createAnnotationRepository() }
    val pdfExporter = remember { createPdfExporter() }
    val focusRequester = remember { FocusRequester() }
    var shiftHeld by remember { mutableStateOf(false) }
    // Высота верхней «обвязки» в портрете (top bar + опциональная панель
    // настроек активного инструмента). Меряется через onSizeChanged ниже
    // и используется как top-padding для PageThumbnailsSidebar, чтобы он
    // не уезжал под top bar / панель настроек.
    val density = LocalDensity.current
    var portraitTopChromeHeightDp by remember { mutableStateOf(0.dp) }
    // В портретном режиме сайдбар миниатюр выезжает слева на всю высоту
    // и сдвигает PDF + top bar вправо (не перекрывает их, как в landscape).
    // tween — чтобы синхронизироваться со slideOutHorizontally самого
    // сайдбара (по умолчанию ~250 ms); spring у animateDpAsState ощутимо
    // отставал, отчего при закрытии возникал визуальный лаг.
    val portraitSidebarOffset by animateDpAsState(
        targetValue = if (!isLandscape && showThumbnails && pages.isNotEmpty()) SIDEBAR_WIDTH else 0.dp,
        animationSpec = tween(durationMillis = SIDEBAR_ANIM_DURATION_MS),
        label = "portrait-sidebar-offset",
    )

    val syncBridge = remember(syncEngine) {
        syncEngine?.let { engine ->
            SyncBridge(engine = engine, drawingStates = drawingStates, scope = coroutineScope)
        }
    }

    LaunchedEffect(syncBridge) { syncBridge?.start() }

    // Phase 3: removed the on-Connected auto-push of the host's current PDF.
    // The tablet now drives document opens via NetworkMessage.DocumentOpenRequest,
    // handled centrally by DocumentTransferRequestHandler.
    //
    // Phase 6 (headless host save): SaveRequest + AnnotationSnapshotRequest
    // from the tablet are no longer handled here. They are owned by
    // HostHeadlessAnnotationHandler at app scope, which uses
    // HostAnnotationProjection (kept in sync via SyncEngine.mergedDeltas
    // mirroring local edits). This means the host doesn't need to have the
    // PDF open in DetailsContent for save/snapshot to work.

    // Tablet side: after the local bundle load, ask every connected host for
    // its snapshot and merge into drawingStates (dedup by strokeId). Multi-host:
    // broadcast the request, accept snapshots from any host (the strokes are
    // logically the same — first wins, others are deduped).
    LaunchedEffect(peerClient, filePath, documentId) {
        val client = peerClient ?: return@LaunchedEffect
        runCatching {
            client.broadcast(NetworkMessage.AnnotationSnapshotRequest(documentId = documentId))
        }.onFailure { e ->
            logger.warn { "Failed to request annotation snapshot: ${e::class.simpleName}" }
        }
        client.incomingMessages
            .filter { it.message is NetworkMessage.AnnotationSnapshot }
            .map { it.message as NetworkMessage.AnnotationSnapshot }
            .filter { it.documentId.isEmpty() || it.documentId == documentId }
            .collect { snapshot ->
                logger.info { "Received annotation snapshot for doc=$documentId: ${snapshot.strokes.size} strokes" }
                snapshot.strokes.forEach { added ->
                    val state = drawingStates.getOrPut(added.pageIndex) { PdfDrawingState() }
                    added.pageExtent?.let { extDto ->
                        state.setExtent(state.extent.value.union(extDto.toDomain()))
                    }
                    if (state.currentPaths.none { it.strokeId == added.strokeId }) {
                        state.currentPaths.add(added.path.toDomain())
                        state.markHistoryChanged()
                    }
                }
            }
    }

    // Tablet side: surface connection loss; also expose an aggregated pairing
    // state so the Save action can decide between remote (send SaveRequest to
    // the host) and local (no host paired). With multi-host: "Connected" if
    // any host is paired; "LostConnection" if every host has lost connection
    // (and at least one was tried).
    var clientPairingState by remember { mutableStateOf<PairingState?>(null) }
    var wasEverConnected by remember(filePath) { mutableStateOf(false) }
    val isRemoteOpenedDoc = remember(filePath, receivedPdfDir) {
        receivedPdfDir != null && filePath.startsWith(receivedPdfDir)
    }
    LaunchedEffect(peerClient) {
        val client = peerClient ?: return@LaunchedEffect
        client.pairingStates.collect { states ->
            val aggregate: PairingState = when {
                states.values.any { it is PairingState.Connected } ->
                    states.values.first { it is PairingState.Connected }
                states.isNotEmpty() && states.values.all { it is PairingState.LostConnection } ->
                    PairingState.LostConnection
                states.values.any { it is PairingState.Reconnecting } ->
                    states.values.first { it is PairingState.Reconnecting }
                states.values.any { it is PairingState.Error } ->
                    states.values.first { it is PairingState.Error }
                else -> PairingState.Idle
            }
            clientPairingState = aggregate
            if (aggregate is PairingState.Connected) wasEverConnected = true
        }
    }

    // Phase 5: surface pending delta count for the offline banner.
    var pendingForDoc by remember { mutableStateOf(0) }
    LaunchedEffect(pendingDeltaCounts, documentId) {
        val flow = pendingDeltaCounts ?: return@LaunchedEffect
        flow.collect { counts -> pendingForDoc = counts[documentId] ?: 0 }
    }
    // Idle = соединение ни разу не инициировалось (локальный режим) — баннер не нужен.
    val showOfflineBanner = peerClient != null &&
        clientPairingState != null &&
        clientPairingState !is PairingState.Idle &&
        clientPairingState !is PairingState.Connected &&
        pendingForDoc > 0

    // Discoonnect → автосохранение + снекбар. Запускается только для документов,
    // открытых с удалённого пира (живут в receivedPdfDir), и только когда
    // соединение хотя бы раз было установлено в этой сессии — иначе нет смысла
    // сообщать «потеряли связь», её и не было.
    val saveLocallyAndNotify: suspend (String) -> Unit = save@{ message ->
        val annotations = drawingStates.mapValues { (_, state) ->
            state.currentPaths.toList()
        }
        val extents = drawingStates.mapValues { (_, state) -> state.extent.value }
        val result = annotationRepository.save(
            pdfPath = filePath,
            annotations = annotations,
            scale = currentScalePercent,
            pen = penSettings,
            marker = markerSettings,
            eraser = eraserSettings,
            currentPage = firstVisiblePage,
            currentPageOffset = currentPageOffsetPx,
            favoritePageIndices = favoritePageIndices.toSet(),
            pageExtents = extents,
        )
        val text = if (result.isSuccess) message else "Ошибка локального сохранения"
        snackbarHostState.showSnackbar(text)
    }
    var previouslyConnected by remember(filePath) { mutableStateOf(false) }
    var previouslyOffline by remember(filePath) { mutableStateOf(false) }
    LaunchedEffect(clientPairingState, isRemoteOpenedDoc) {
        if (!isRemoteOpenedDoc) return@LaunchedEffect
        val nowConnected = clientPairingState is PairingState.Connected
        val nowOffline = clientPairingState is PairingState.Reconnecting ||
            clientPairingState is PairingState.LostConnection ||
            clientPairingState is PairingState.Error
        when {
            previouslyConnected && nowOffline -> {
                // Edge-trigger: гасим флаг сразу, чтобы повторные тики Reconnecting
                // (его state эмитит каждую секунду с countdown) не плодили снекбары.
                previouslyConnected = false
                previouslyOffline = true
                saveLocallyAndNotify("Пропало соединение. Документ сохранён локально")
            }
            previouslyOffline && nowConnected -> {
                // Reconnect-edge: PendingDeltaReplayCoordinator уже запустился на
                // переход connectedHosts → non-empty (см. main.kt). Дождёмся, пока
                // буфер для текущего документа опустеет — это и есть "успешно
                // синхронизировано". Если за разумное время не опустел —
                // сообщаем о неуспехе.
                previouslyOffline = false
                previouslyConnected = true
                val pendingAtReconnect = pendingForDoc
                if (pendingAtReconnect <= 0) {
                    snackbarHostState.showSnackbar("Соединение восстановлено")
                } else {
                    val flow = pendingDeltaCounts
                    val syncedInTime = if (flow != null) {
                        withTimeoutOrNull(REPLAY_DEADLINE) {
                            flow.first { (it[documentId] ?: 0) == 0 }
                            true
                        } ?: false
                    } else {
                        false
                    }
                    val text = if (syncedInTime) {
                        "Соединение восстановлено. Изменения синхронизированы"
                    } else {
                        "Соединение восстановлено, но не все изменения отправлены"
                    }
                    snackbarHostState.showSnackbar(text)
                }
            }
            nowConnected -> previouslyConnected = true
        }
    }

    LaunchedEffect(filePath) {
        pdfDocument?.close()
        pdfDocument = try {
            loader.load(filePath)
        } catch (e: Exception) {
            logger.warn { "Failed to open PDF: ${e::class.simpleName}" }
            null
        }
    }

    DisposableEffect(Unit) {
        onDispose { pdfDocument?.close() }
    }

    DisposableEffect(openDocumentRegistry, documentId) {
        openDocumentRegistry?.acquire(documentId)
        onDispose { openDocumentRegistry?.release(documentId) }
    }

    LaunchedEffect(filePath) {
        annotationRepository.load(filePath).getOrNull()?.let { bundle ->
            // applyInitialState откладывает scroll/zoom до момента, когда
            // viewport измерится и страницы загрузятся — работает одинаково
            // на обеих платформах, без отдельной Android-ветки с
            // ручным lazyListState.scrollToItem.
            pdfViewerState.applyInitialState(
                scalePercent = bundle.scale,
                pageIndex = bundle.currentPage,
                pageOffsetPx = bundle.currentPageOffset,
            )
            penSettings = bundle.pen
            markerSettings = bundle.marker
            eraserSettings = bundle.eraser
            bundle.pages.forEach { (pageIndex, paths) ->
                val state = drawingStates.getOrPut(pageIndex) { PdfDrawingState() }
                state.currentPaths.addAll(paths)
                state.markHistoryChanged()
            }
            bundle.pageExtents.forEach { (pageIndex, ext) ->
                val state = drawingStates.getOrPut(pageIndex) { PdfDrawingState() }
                state.setExtent(ext)
            }
            favoritePageIndices.clear()
            favoritePageIndices.addAll(bundle.favoritePageIndices)
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val onBackWithSave: () -> Unit = {
        coroutineScope.launch {
            val annotations = drawingStates.mapValues { (_, state) ->
                state.currentPaths.toList()
            }
            val extents = drawingStates.mapValues { (_, state) -> state.extent.value }
            annotationRepository.save(
                pdfPath = filePath,
                annotations = annotations,
                scale = currentScalePercent,
                pen = penSettings,
                marker = markerSettings,
                eraser = eraserSettings,
                currentPage = firstVisiblePage,
                currentPageOffset = currentPageOffsetPx,
                favoritePageIndices = favoritePageIndices.toSet(),
                pageExtents = extents,
            ).onFailure { e ->
                logger.warn { "Auto-save on back failed: ${e::class.simpleName}" }
            }
            component.saveLastPageIndex(firstVisiblePage)
            component.onBack()
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .focusRequester(focusRequester)
            .focusTarget()
            .onKeyEvent { e ->
                val isShift = e.key == Key.ShiftLeft || e.key == Key.ShiftRight
                if (isShift) {
                    shiftHeld = e.type == KeyEventType.KeyDown
                    false
                } else if (e.type == KeyEventType.KeyDown && e.key == Key.Z && e.isCtrlPressed && !shiftHeld) {
                    if (globalUndoStack.isNotEmpty()) {
                        val (pageIndex, snapshot) = globalUndoStack.removeLast()
                        val current = drawingStates[pageIndex]?.currentPaths?.toList() ?: emptyList()
                        globalRedoStack.addLast(pageIndex to current)
                        drawingStates[pageIndex]?.restoreSnapshot(snapshot)
                    }
                    true
                } else if (e.type == KeyEventType.KeyDown && e.key == Key.Z && e.isCtrlPressed && shiftHeld) {
                    if (globalRedoStack.isNotEmpty()) {
                        val (pageIndex, snapshot) = globalRedoStack.removeLast()
                        val current = drawingStates[pageIndex]?.currentPaths?.toList() ?: emptyList()
                        globalUndoStack.addLast(pageIndex to snapshot)
                        drawingStates[pageIndex]?.restoreSnapshot(snapshot)
                    }
                    true
                } else false
            },
    ) {

        PdfPagesViewer(
            state = pdfViewerState,
            pdfDocument = pdfDocument,
            pages = pages,
            renderer = renderer,
            // PDF в портрете не сдвигается при открытии сайдбара миниатюр —
            // сайдбар оверлеит его слева (так же, как в landscape). Сдвиг
            // через translationX/padding делал бы правый край PDF за экраном
            // или дёргал бы тяжёлый SubcomposeLayout каждый кадр анимации.
            modifier = Modifier.fillMaxSize(),
        ) {
            val bm = bitmap
            // Размер страницы уже задан Constraints.fixed(w,h) из
            // SubcomposeLayout в PdfPagesViewer (visualWidth/visualHeight —
            // те же пиксели в Dp). Modifier.size(Dp,Dp) был бы избыточен И
            // нестабилен: новый instance на каждом тике зума → нестабильный
            // modifier у DrawablePdfPage → тяжёлая рекомпозиция каждый кадр
            // pinch'а. fillMaxSize — singleton, стабильный по identity.
            Box(modifier = Modifier.fillMaxSize()) {
                if (bm != null) {
                    val pdfDrawingState = remember(pageIndex) {
                        drawingStates.getOrPut(pageIndex) { PdfDrawingState() }
                    }
                    // Все callback'и обёрнуты в remember с стабильными
                    // ключами — иначе каждый recomposition создаёт новые
                    // lambda-инстансы, делает параметры DrawablePdfPage
                    // нестабильными и блокирует skipping в Compose strong-
                    // skipping mode. eraserOverride — отдельно через
                    // rememberUpdatedState: лямбда стабильна по identity,
                    // но возвращает актуальное значение при вызове.
                    val onGestureStart = remember(pageIndex) {
                        { snapshot: List<DrawingPath> ->
                            globalUndoStack.addLast(pageIndex to snapshot)
                            globalRedoStack.clear()
                        }
                    }
                    val onEraseFinished = remember(pageIndex, pdfDrawingState, syncEngine) {
                        { before: List<DrawingPath>, _: List<DrawingPath> ->
                            handleEraseFinished(
                                pdfDrawingState = pdfDrawingState,
                                pageIndex = pageIndex,
                                before = before,
                                engine = syncEngine,
                            )
                        }
                    }
                    val onStrokeFinished = remember(pageIndex, pdfDrawingState, syncEngine) {
                        { path: DrawingPath ->
                            handleStrokeFinished(
                                pdfDrawingState = pdfDrawingState,
                                pageIndex = pageIndex,
                                path = path,
                                engine = syncEngine,
                            )
                        }
                    }
                    val eraserOverrideState = rememberUpdatedState(eraserOverride)
                    val eraserOverrideProvider = remember {
                        { eraserOverrideState.value }
                    }
                    val isMagnifierPage = magnifierState.enabled && magnifierState.pageIndex == pageIndex
                    if (isMagnifierPage) {
                        // Прокидываем актуальный битмап активной страницы
                        // в magnifier, чтобы панель могла рендерить
                        // увеличенный PDF-тайл без отдельного запроса.
                        SideEffect { magnifierState.updatePageBitmap(bm) }
                    }
                    DrawablePdfPage(
                        bitmap = bm,
                        pdfDrawingState = pdfDrawingState,
                        toolMode = effectiveToolMode,
                        penSettings = penSettings,
                        markerSettings = markerSettings,
                        eraserSettings = eraserSettings,
                        pdfWidth = pdfWidth,
                        pdfHeight = pdfHeight,
                        pageExtent = extent,
                        eraserOverride = eraserOverrideProvider,
                        pencilModeEnabled = pencilModeEnabled,
                        magnifierState = if (isMagnifierPage) magnifierState else null,
                        onGestureStart = onGestureStart,
                        onEraseFinished = onEraseFinished,
                        onStrokeFinished = onStrokeFinished,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text("Loading")
                }
            }
        }

        AnimatedVisibility(
            visible = showThumbnails && pages.isNotEmpty(),
            enter = slideInHorizontally(
                animationSpec = tween(durationMillis = SIDEBAR_ANIM_DURATION_MS),
            ) { -it } + fadeIn(animationSpec = tween(durationMillis = SIDEBAR_ANIM_DURATION_MS)),
            exit = slideOutHorizontally(
                animationSpec = tween(durationMillis = SIDEBAR_ANIM_DURATION_MS),
            ) { -it } + fadeOut(animationSpec = tween(durationMillis = SIDEBAR_ANIM_DURATION_MS)),
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            // В портрете сайдбар занимает всю высоту экрана и сдвигает
            // PDF + top bar — поэтому top-padding не нужен. В landscape
            // сайдбар по-прежнему оверлеит контент сбоку.
            PageThumbnailsSidebar(
                pages = pages,
                pdfDocument = pdfDocument,
                renderer = renderer,
                currentPage = firstVisiblePage,
                onPageClick = { pageIndex ->
                    pdfViewerState.scrollToPage(pageIndex, 0)
                },
                annotatedPageIndices = annotatedPageIndices,
                favoritePageIndices = favoritePageIndices.toSet(),
                onToggleFavorite = { pageIndex ->
                    if (!favoritePageIndices.remove(pageIndex)) {
                        favoritePageIndices.add(pageIndex)
                    }
                },
                // remember + .currentPaths без .toList(): возвращаем сам
                // SnapshotStateList (он стабилен по identity для одной
                // страницы), чтобы ThumbnailItem был skippable в strong-
                // skipping и не рекомпозился при каждой смене firstVisiblePage
                // во время скролла PDF.
                pagePaths = remember(drawingStates) {
                    { pageIndex ->
                        drawingStates[pageIndex]?.currentPaths ?: emptyList()
                    }
                },
            )
        }

        val onSaveCallback: () -> Unit = {
            isSaving = true
            coroutineScope.launch {
                // Remote save (через peerClient) имеет смысл только если этот
                // девайс реально подключён как клиент к чужому host-у.
                // На host-инстансе (PC) peerClient тоже не null, но не
                // подключён ни к кому — в этом случае сохраняем локально.
                val message = if (peerClient != null && clientPairingState is PairingState.Connected) {
                    val requestId = "save-${Random.nextLong().toString(16)}"
                    logger.info { "[save-diag tablet] sending SaveRequest id=$requestId doc=$documentId" }
                    // Multi-host: ask every connected host. The first reply wins —
                    // typically only one host actually holds the document.
                    peerClient.broadcast(
                        NetworkMessage.SaveRequest(
                            requestId = requestId,
                            documentId = documentId,
                        ),
                    )
                    logger.info { "[save-diag tablet] SaveRequest id=$requestId dispatched, awaiting SaveResult (5s)" }
                    val reply = withTimeoutOrNull(5.seconds) {
                        peerClient.incomingMessages
                            .filter { it.message is NetworkMessage.SaveResult }
                            .map { it.message as NetworkMessage.SaveResult }
                            .filter { it.requestId == requestId }
                            .first()
                    }
                    logger.info { "[save-diag tablet] await done id=$requestId reply=$reply" }
                    when {
                        reply == null -> "Нет ответа от ПК"
                        reply.success -> "Сохранено на ПК"
                        else -> "Ошибка на ПК: ${reply.errorMessage.orEmpty()}"
                    }
                } else {
                    val annotations = drawingStates.mapValues { (_, state) ->
                        state.currentPaths.toList()
                    }
                    val extents = drawingStates.mapValues { (_, state) -> state.extent.value }
                    val result = annotationRepository.save(
                        pdfPath = filePath,
                        annotations = annotations,
                        scale = currentScalePercent,
                        pen = penSettings,
                        marker = markerSettings,
                        eraser = eraserSettings,
                        currentPage = firstVisiblePage,
                        currentPageOffset = currentPageOffsetPx,
                        favoritePageIndices = favoritePageIndices.toSet(),
                        pageExtents = extents,
                    )
                    if (result.isSuccess) "Аннотации сохранены" else "Ошибка сохранения"
                }
                isSaving = false
                snackbarHostState.showSnackbar(message)
            }
        }

        val onExportCallback: () -> Unit = {
            isExporting = true
            coroutineScope.launch {
                val annotations = drawingStates.mapValues { (_, state) ->
                    state.currentPaths.toList()
                }
                val outputPath = filePath.removeSuffix(".pdf") + "_annotated.pdf"
                val result = pdfExporter.export(
                    sourcePdfPath = filePath,
                    annotations = annotations,
                    outputPath = outputPath,
                )
                isExporting = false
                val message = if (result.isSuccess) {
                    "Экспорт завершён: $outputPath"
                } else {
                    "Ошибка экспорта"
                }
                snackbarHostState.showSnackbar(message)
            }
        }

        val onZoomInCallback: () -> Unit = {
            pdfViewerState.zoomBy(
                TOOLBAR_ZOOM_STEP_IN,
                Offset(windowSizeInPx.width / 2f, windowSizeInPx.height / 2f),
            )
        }

        val onZoomOutCallback: () -> Unit = {
            pdfViewerState.zoomBy(
                TOOLBAR_ZOOM_STEP_OUT,
                Offset(windowSizeInPx.width / 2f, windowSizeInPx.height / 2f),
            )
        }

        val onMagnifierToggle: () -> Unit = {
            if (magnifierState.enabled) {
                magnifierState.disable()
            } else {
                magnifierState.enable(
                    onPage = firstVisiblePage,
                    viewportSize = Size(
                        windowSizeInPx.width.toFloat(),
                        windowSizeInPx.height.toFloat(),
                    ),
                )
            }
        }

        val onPencilModeChangeCallback: (Boolean) -> Unit = { enabled ->
            pencilModeEnabled = enabled
            // Ручной toggle (в т.ч. выкл) подавляет дальнейшее
            // авто-управление по stylus-событиям в этой сессии —
            // иначе off сразу же отменялся бы.
            pencilModeManuallyTouched = true
        }

        if (isLandscape) {
            // Когда сайдбар страниц открыт — отодвигаем floating-тулбар и
            // back-кнопку на его ширину, чтобы они не оказывались под ним.
            val sidebarOffset by animateDpAsState(
                targetValue = if (showThumbnails && pages.isNotEmpty()) SIDEBAR_WIDTH else 0.dp,
                label = "landscape-sidebar-offset",
            )

            // Landscape: vertical left rail toolbar + vertical settings panel in a Row.
            AnimatedVisibility(
                visible = true,
                enter = slideInHorizontally { -it } + fadeIn(),
                exit = slideOutHorizontally { -it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(start = 16.dp + sidebarOffset, top = 16.dp, end = 16.dp, bottom = 16.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    PdfFloatingToolbar(
                        toolMode = toolMode,
                        onToolModeChange = { toolMode = it },
                        hasAnnotations = hasAnnotations,
                        isSaving = isSaving,
                        isExporting = isExporting,
                        showThumbnails = showThumbnails,
                        onToggleThumbnails = { showThumbnails = !showThumbnails },
                        showPencilModeButton = SupportsPencilMode,
                        pencilModeEnabled = pencilModeEnabled,
                        onPencilModeChange = onPencilModeChangeCallback,
                        magnifierEnabled = magnifierState.enabled,
                        onMagnifierToggle = onMagnifierToggle,
                        onSave = onSaveCallback,
                        onExport = onExportCallback,
                        scale = currentScalePercent,
                        onZoomIn = onZoomInCallback,
                        onZoomOut = onZoomOutCallback,
                    )
                    Spacer(Modifier.width(12.dp))
                    ToolSettingsFloatingPanel(
                        toolMode = toolMode,
                        penSettings = penSettings,
                        onPenSettingsChange = { penSettings = it },
                        markerSettings = markerSettings,
                        onMarkerSettingsChange = { markerSettings = it },
                        eraserSettings = eraserSettings,
                        onEraserSettingsChange = { eraserSettings = it },
                        vertical = true,
                    )
                }
            }

            // Landscape: page indicator airbar + optional offline banner, stacked vertically.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 8.dp),
            ) {
                AnimatedVisibility(
                    visible = pages.isNotEmpty(),
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut(),
                ) {
                    PageIndicatorAirbar(
                        currentPage = firstVisiblePage + 1,
                        totalPages = pages.size,
                    )
                }
                if (showOfflineBanner) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .widthIn(min = 240.dp, max = 480.dp),
                    ) {
                        Text(
                            text = "Оффлайн, $pendingForDoc правок ждут отправки",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            // Landscape: floating back button at the top-start corner.
            GlassSurface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(start = 16.dp + sidebarOffset, top = 16.dp, end = 16.dp, bottom = 16.dp),
                shape = CircleShape,
            ) {
                // Симметрично портрету: пока сайдбар миниатюр раскрыт,
                // back-кнопка сначала схлопывает его, и только повторным
                // нажатием уходит со страницы.
                IconButton(onClick = {
                    if (showThumbnails) showThumbnails = false
                    else onBackWithSave()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = BACK_CONTENT_DESCRIPTION,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        } else {
            // Portrait: full-width top bar + settings strip below it.
            // При открытии сайдбара миниатюр тулбар реально сужается
            // (start padding на ширину сайдбара) — а не уезжает правым
            // краем за экран. Полоса лёгкая (несколько кнопок), remeasure
            // каждый кадр анимации не ощутим.
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(start = portraitSidebarOffset)
                    .onSizeChanged { size ->
                        portraitTopChromeHeightDp = with(density) { size.height.toDp() }
                    },
            ) {
                PortraitTopBar(
                    currentPage = firstVisiblePage + 1,
                    totalPages = pages.size,
                    toolMode = toolMode,
                    onToolModeChange = { toolMode = it },
                    hasAnnotations = hasAnnotations,
                    isSaving = isSaving,
                    isExporting = isExporting,
                    onSave = onSaveCallback,
                    onExport = onExportCallback,
                    scale = currentScalePercent,
                    onZoomIn = onZoomInCallback,
                    onZoomOut = onZoomOutCallback,
                    showThumbnails = showThumbnails,
                    onToggleThumbnails = { showThumbnails = !showThumbnails },
                    showPencilModeButton = SupportsPencilMode,
                    pencilModeEnabled = pencilModeEnabled,
                    onPencilModeChange = onPencilModeChangeCallback,
                    magnifierEnabled = magnifierState.enabled,
                    onMagnifierToggle = onMagnifierToggle,
                    onBack = {
                        // В портрете back-кнопка сначала закрывает раскрытый
                        // сайдбар миниатюр (он занимает всю высоту слева),
                        // и только следующим нажатием — уходит со страницы.
                        if (showThumbnails) showThumbnails = false
                        else onBackWithSave()
                    },
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    ToolSettingsFloatingPanel(
                        toolMode = toolMode,
                        penSettings = penSettings,
                        onPenSettingsChange = { penSettings = it },
                        markerSettings = markerSettings,
                        onMarkerSettingsChange = { markerSettings = it },
                        eraserSettings = eraserSettings,
                        onEraserSettingsChange = { eraserSettings = it },
                        vertical = false,
                        atTop = true,
                        applyInsets = false,
                    )
                }
                if (showOfflineBanner) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "Оффлайн, $pendingForDoc правок ждут отправки",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars),
        )

        if (magnifierState.enabled) {
            val magPage = magnifierState.pageIndex
            val magPdfDrawingState = remember(magPage) {
                drawingStates.getOrPut(magPage) { PdfDrawingState() }
            }
            val magOnGestureStart = remember(magPage) {
                { snapshot: List<DrawingPath> ->
                    globalUndoStack.addLast(magPage to snapshot)
                    globalRedoStack.clear()
                }
            }
            val magOnEraseFinished = remember(magPage, magPdfDrawingState, syncEngine) {
                { before: List<DrawingPath>, _: List<DrawingPath> ->
                    handleEraseFinished(
                        pdfDrawingState = magPdfDrawingState,
                        pageIndex = magPage,
                        before = before,
                        engine = syncEngine,
                    )
                }
            }
            val magOnStrokeFinished = remember(magPage, magPdfDrawingState, syncEngine) {
                { path: DrawingPath ->
                    handleStrokeFinished(
                        pdfDrawingState = magPdfDrawingState,
                        pageIndex = magPage,
                        path = path,
                        engine = syncEngine,
                    )
                }
            }
            val magEraserOverrideState = rememberUpdatedState(eraserOverride)
            val magEraserOverrideProvider = remember { { magEraserOverrideState.value } }

            MagnifierInputPanel(
                state = magnifierState,
                pdfDrawingState = magPdfDrawingState,
                toolMode = effectiveToolMode,
                penSettings = penSettings,
                markerSettings = markerSettings,
                eraserSettings = eraserSettings,
                eraserOverride = magEraserOverrideProvider,
                pencilModeEnabled = pencilModeEnabled,
                onGestureStart = magOnGestureStart,
                onStrokeFinished = magOnStrokeFinished,
                onEraseFinished = magOnEraseFinished,
                onClose = { magnifierState.disable() },
            )
        }
    }
}

/**
 * Общая логика sync-уведомления после удаления / частичного стирания
 * штрихов. Используется как desktop, так и Android-ветками viewer'а.
 */
private fun handleEraseFinished(
    pdfDrawingState: PdfDrawingState,
    pageIndex: Int,
    before: List<DrawingPath>,
    engine: SyncEngine?,
) {
    if (engine == null) return
    val beforeIds = before.mapNotNull { it.strokeId.ifEmpty { null } }.toSet()
    val beforeById = before.associateBy { it.strokeId }
    val intactIds = mutableSetOf<String>()
    for (p in pdfDrawingState.currentPaths) {
        val orig = beforeById[p.strokeId] ?: continue
        if (orig.points == p.points && p.strokeId.isNotEmpty()) {
            intactIds.add(p.strokeId)
        }
    }
    val removedOrModified = beforeIds - intactIds
    if (removedOrModified.isEmpty()) return
    val newAdded = mutableListOf<DrawingPath>()
    for (i in pdfDrawingState.currentPaths.indices) {
        val p = pdfDrawingState.currentPaths[i]
        val needsId = p.strokeId.isEmpty() || p.strokeId in removedOrModified
        if (needsId) {
            val newId = engine.newStrokeId()
            val stamped = p.copy(strokeId = newId)
            pdfDrawingState.currentPaths[i] = stamped
            newAdded.add(stamped)
        }
    }
    val ext = pdfDrawingState.extent.value
    val extDto = if (ext != PageExtent.Pdf) RectDto.fromDomain(ext) else null
    val batch = buildList<StrokeDelta> {
        for (id in removedOrModified) add(
            StrokeDelta.Removed(
                strokeId = id,
                pageIndex = pageIndex,
                authorDeviceId = engine.deviceId,
                clock = 0,
            ),
        )
        for ((idx, p) in newAdded.withIndex()) add(
            StrokeDelta.Added(
                strokeId = p.strokeId,
                pageIndex = pageIndex,
                authorDeviceId = engine.deviceId,
                clock = 0,
                path = p.toDto(p.strokeId),
                // Достаточно отправить extent один раз в первой Added; получатель union'ит.
                pageExtent = if (idx == 0) extDto else null,
            ),
        )
    }
    engine.applyLocalBatch(batch)
}

/**
 * Общая логика проштамповки strokeId и публикации нового штриха в sync.
 */
private fun handleStrokeFinished(
    pdfDrawingState: PdfDrawingState,
    pageIndex: Int,
    path: DrawingPath,
    engine: SyncEngine?,
) {
    if (engine == null) return
    val id = engine.newStrokeId()
    val stamped = path.copy(strokeId = id)
    val idx = pdfDrawingState.currentPaths.lastIndex
    if (idx >= 0) {
        pdfDrawingState.currentPaths[idx] = stamped
        pdfDrawingState.markHistoryChanged()
    }
    val ext = pdfDrawingState.extent.value
    engine.applyLocal(
        StrokeDelta.Added(
            strokeId = id,
            pageIndex = pageIndex,
            authorDeviceId = engine.deviceId,
            clock = 0,
            path = stamped.toDto(id),
            pageExtent = if (ext != PageExtent.Pdf) RectDto.fromDomain(ext) else null,
        ),
    )
}
