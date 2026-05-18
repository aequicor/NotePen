package ru.kyamshanov.notepen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.model.EraserSettings
import ru.kyamshanov.notepen.annotation.domain.model.MarkerSettings
import ru.kyamshanov.notepen.annotation.domain.model.PenSettings
import ru.kyamshanov.notepen.annotation.domain.port.AnnotationRepository as SharedAnnotationRepository
import ru.kyamshanov.notepen.pdf.domain.model.PdfDocument
import ru.kyamshanov.notepen.pdf.domain.port.PdfDocumentLoader
import ru.kyamshanov.notepen.pdf.domain.port.PdfPageRenderer
import ru.kyamshanov.notepen.sync.SyncBridge
import ru.kyamshanov.notepen.sync.domain.SyncEngine
import ru.kyamshanov.notepen.sync.domain.documentIdFromFilePath
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.model.PairingState
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

/** Toolbar `+` button zoom factor (matches Ctrl+wheel zoom-in). */
private const val TOOLBAR_ZOOM_STEP_IN = 1.1f

/** Toolbar `−` button zoom factor (matches Ctrl+wheel zoom-out). */
private const val TOOLBAR_ZOOM_STEP_OUT = 1f / TOOLBAR_ZOOM_STEP_IN


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
    modifier: Modifier = Modifier,
) {
    val localWindowInfo = LocalWindowInfo.current
    val windowSizeInPx = localWindowInfo.containerSize
    val isLandscape = windowSizeInPx.width > windowSizeInPx.height
    val model by component.model.subscribeAsState()
    val filePath = remember(model.title) { model.title }
    val documentId = remember(filePath) { documentIdFromFilePath(filePath) }
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
    // Авто-включение по первому stylus-событию срабатывает один раз за
    // композицию. Ручной off также взводит этот флаг — повторного авто-on
    // в той же сессии не будет.
    var pencilModeAutoApplied by remember { mutableStateOf(false) }
    val stylusEverSeen by tabletController.stylusEverSeen.collectAsState()
    LaunchedEffect(stylusEverSeen) {
        if (stylusEverSeen && !pencilModeAutoApplied) {
            pencilModeAutoApplied = true
            pencilModeEnabled = true
        }
    }
    var showThumbnails by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    val drawingStates = remember { mutableStateMapOf<Int, PdfDrawingState>() }
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

    // Tablet side: after the local bundle load, ask the host for its snapshot
    // and merge it into drawingStates (dedup by strokeId).
    LaunchedEffect(peerClient, filePath, documentId) {
        val client = peerClient ?: return@LaunchedEffect
        // Give the LaunchedEffect(filePath) annotation-load a chance to populate
        // drawingStates before requesting — the dedup later filters duplicates,
        // so order is not strictly required, but this keeps logs cleaner.
        runCatching {
            client.send(NetworkMessage.AnnotationSnapshotRequest(documentId = documentId))
        }.onFailure { e ->
            logger.warn { "Failed to request annotation snapshot: ${e::class.simpleName}" }
        }
        client.incomingMessages
            .filterIsInstance<NetworkMessage.AnnotationSnapshot>()
            .filter { it.documentId.isEmpty() || it.documentId == documentId }
            .collect { snapshot ->
                logger.info { "Received annotation snapshot for doc=$documentId: ${snapshot.strokes.size} strokes" }
                snapshot.strokes.forEach { added ->
                    val state = drawingStates.getOrPut(added.pageIndex) { PdfDrawingState() }
                    if (state.currentPaths.none { it.strokeId == added.strokeId }) {
                        state.currentPaths.add(added.path.toDomain())
                        state.markHistoryChanged()
                    }
                }
            }
    }

    // Tablet side: surface connection loss; also expose the latest pairing
    // state so the Save action can decide between remote (send SaveRequest
    // to host) and local (host has no remote host of its own).
    var showLostConnectionDialog by remember { mutableStateOf(false) }
    var clientPairingState by remember { mutableStateOf<PairingState?>(null) }
    LaunchedEffect(peerClient) {
        val client = peerClient ?: return@LaunchedEffect
        client.state.collect { st ->
            clientPairingState = st
            showLostConnectionDialog = st is PairingState.LostConnection
        }
    }

    // Phase 5: surface pending delta count for the offline banner.
    var pendingForDoc by remember { mutableStateOf(0) }
    LaunchedEffect(pendingDeltaCounts, documentId) {
        val flow = pendingDeltaCounts ?: return@LaunchedEffect
        flow.collect { counts -> pendingForDoc = counts[documentId] ?: 0 }
    }
    val showOfflineBanner = peerClient != null &&
        clientPairingState !is PairingState.Connected &&
        pendingForDoc > 0

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
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val onBackWithSave: () -> Unit = {
        coroutineScope.launch {
            val annotations = drawingStates.mapValues { (_, state) ->
                state.currentPaths.toList()
            }
            annotationRepository.save(
                pdfPath = filePath,
                annotations = annotations,
                scale = currentScalePercent,
                pen = penSettings,
                marker = markerSettings,
                eraser = eraserSettings,
                currentPage = firstVisiblePage,
                currentPageOffset = currentPageOffsetPx,
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

        if (showOfflineBanner) {
            androidx.compose.material3.Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .widthIn(min = 240.dp, max = 480.dp),
            ) {
                Text(
                    text = "Оффлайн, $pendingForDoc правок ждут отправки",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        PdfPagesViewer(
            state = pdfViewerState,
            pdfDocument = pdfDocument,
            pages = pages,
            renderer = renderer,
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
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            PageThumbnailsSidebar(
                pages = pages,
                pdfDocument = pdfDocument,
                renderer = renderer,
                currentPage = firstVisiblePage,
                onPageClick = { pageIndex ->
                    pdfViewerState.scrollToPage(pageIndex, 0)
                },
                annotatedPageIndices = annotatedPageIndices,
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
                    peerClient.send(
                        NetworkMessage.SaveRequest(
                            requestId = requestId,
                            documentId = documentId,
                        ),
                    )
                    logger.info { "[save-diag tablet] SaveRequest id=$requestId dispatched, awaiting SaveResult (5s)" }
                    val reply = withTimeoutOrNull(5.seconds) {
                        peerClient.incomingMessages
                            .filterIsInstance<NetworkMessage.SaveResult>()
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
                    val result = annotationRepository.save(
                        pdfPath = filePath,
                        annotations = annotations,
                        scale = currentScalePercent,
                        pen = penSettings,
                        marker = markerSettings,
                        eraser = eraserSettings,
                        currentPage = firstVisiblePage,
                        currentPageOffset = currentPageOffsetPx,
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
            // авто-включение по stylus-событиям в этой сессии —
            // иначе off сразу же отменялся бы.
            pencilModeAutoApplied = true
        }

        if (isLandscape) {
            // Landscape: vertical left rail toolbar with page counter.
            AnimatedVisibility(
                visible = true,
                enter = slideInHorizontally { -it } + fadeIn(),
                exit = slideOutHorizontally { -it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(16.dp),
            ) {
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
                    currentPage = firstVisiblePage + 1,
                    totalPages = pages.size,
                )
            }

            // Landscape: compact settings strip at the top.
            val panelMaxWidth = with(LocalDensity.current) { windowSizeInPx.width.toDp() - 32.dp }
            val settingsAlignment = if (ToolMenusAtTop) Alignment.TopCenter else Alignment.BottomCenter
            ToolSettingsFloatingPanel(
                toolMode = toolMode,
                penSettings = penSettings,
                onPenSettingsChange = { penSettings = it },
                markerSettings = markerSettings,
                onMarkerSettingsChange = { markerSettings = it },
                eraserSettings = eraserSettings,
                onEraserSettingsChange = { eraserSettings = it },
                atTop = ToolMenusAtTop,
                modifier = Modifier
                    .align(settingsAlignment)
                    .widthIn(max = panelMaxWidth),
            )

            // Landscape: floating back button at the top-start corner.
            IconButton(
                onClick = onBackWithSave,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface, CircleShape),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = BACK_CONTENT_DESCRIPTION,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        } else {
            // Portrait: full-width top bar + settings strip below it.
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth(),
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
                    onBack = onBackWithSave,
                )
                AnimatedVisibility(
                    visible = toolMode != ToolMode.NONE,
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut(),
                ) {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                        ToolSettingsContent(
                            toolMode = toolMode,
                            penSettings = penSettings,
                            onPenSettingsChange = { penSettings = it },
                            markerSettings = markerSettings,
                            onMarkerSettingsChange = { markerSettings = it },
                            eraserSettings = eraserSettings,
                            onEraserSettingsChange = { eraserSettings = it },
                            expandDownward = true,
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        if (showLostConnectionDialog) {
            AlertDialog(
                onDismissRequest = { showLostConnectionDialog = false },
                title = { Text("Соединение потеряно") },
                text = { Text("Не удалось переподключиться к ПК за 10 секунд. Сохранить аннотации локально?") },
                confirmButton = {
                    TextButton(onClick = {
                        showLostConnectionDialog = false
                        coroutineScope.launch {
                            val annotations = drawingStates.mapValues { (_, state) ->
                                state.currentPaths.toList()
                            }
                            val result = annotationRepository.save(
                                pdfPath = filePath,
                                annotations = annotations,
                                scale = currentScalePercent,
                                pen = penSettings,
                                marker = markerSettings,
                                eraser = eraserSettings,
                                currentPage = firstVisiblePage,
                                currentPageOffset = currentPageOffsetPx,
                            )
                            val msg = if (result.isSuccess) "Сохранено локально" else "Ошибка локального сохранения"
                            snackbarHostState.showSnackbar(msg)
                        }
                    }) { Text("Сохранить локально") }
                },
                dismissButton = {
                    TextButton(onClick = { showLostConnectionDialog = false }) { Text("Отмена") }
                },
            )
        }

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
    val batch = buildList<StrokeDelta> {
        for (id in removedOrModified) add(
            StrokeDelta.Removed(
                strokeId = id,
                pageIndex = pageIndex,
                authorDeviceId = engine.deviceId,
                clock = 0,
            ),
        )
        for (p in newAdded) add(
            StrokeDelta.Added(
                strokeId = p.strokeId,
                pageIndex = pageIndex,
                authorDeviceId = engine.deviceId,
                clock = 0,
                path = p.toDto(p.strokeId),
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
    engine.applyLocal(
        StrokeDelta.Added(
            strokeId = id,
            pageIndex = pageIndex,
            authorDeviceId = engine.deviceId,
            clock = 0,
            path = stamped.toDto(id),
        ),
    )
}
