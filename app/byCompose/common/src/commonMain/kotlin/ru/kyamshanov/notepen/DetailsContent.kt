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
import androidx.compose.foundation.layout.fillMaxSize
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
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.model.PairingState
import ru.kyamshanov.notepen.sync.domain.model.StrokeDelta
import ru.kyamshanov.notepen.sync.domain.model.toDomain
import ru.kyamshanov.notepen.sync.domain.model.toDto
import ru.kyamshanov.notepen.sync.domain.port.PeerServer
import ru.kyamshanov.notepen.sync.domain.port.SyncClient
import ru.kyamshanov.notepen.sync.infrastructure.WebSocketFileTransfer
import ru.kyamshanov.notepen.pdfviewer.PdfPagesViewer
import ru.kyamshanov.notepen.pdfviewer.PdfViewerState
import ru.kyamshanov.notepen.pdfviewer.rememberPdfViewerState
import ru.kyamshanov.notepen.tablet.LocalTabletInputController
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
    syncEngine: SyncEngine? = null,
    peerServer: PeerServer? = null,
    peerClient: SyncClient? = null,
    modifier: Modifier = Modifier,
) {
    val localWindowInfo = LocalWindowInfo.current
    val windowSizeInPx = localWindowInfo.containerSize
    val model by component.model.subscribeAsState()
    val filePath = remember(model.title) { model.title }

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
    var showThumbnails by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    val drawingStates = remember { mutableStateMapOf<Int, PdfDrawingState>() }
    val hasAnnotations by remember {
        derivedStateOf { drawingStates.values.any { it.currentPaths.isNotEmpty() } }
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

    // Host side: stream the open PDF to the tablet as soon as a peer pairs.
    LaunchedEffect(peerServer, filePath) {
        val server = peerServer ?: return@LaunchedEffect
        server.state.collect { st ->
            if (st is PairingState.Connected) {
                val transferId = "tx-${Random.nextLong().toString(16)}"
                runCatching {
                    WebSocketFileTransfer(server = server)
                        .send(sourcePath = filePath, transferId = transferId)
                        .collect { /* progress ignored on host */ }
                }.onFailure { e ->
                    logger.warn { "Failed to stream PDF to peer: ${e::class.simpleName}" }
                }
            }
        }
    }

    // Host side: react to a SaveRequest from the tablet and persist locally.
    val performHostSave: suspend () -> Result<Unit> = {
        val annotations = drawingStates.mapValues { (_, state) -> state.currentPaths.toList() }
        annotationRepository.save(
            pdfPath = filePath,
            annotations = annotations,
            scale = currentScalePercent,
            pen = penSettings,
            marker = markerSettings,
            eraser = eraserSettings,
            currentPage = firstVisiblePage,
            currentPageOffset = currentPageOffsetPx,
        )
    }

    LaunchedEffect(peerServer) {
        val server = peerServer ?: return@LaunchedEffect
        server.incomingMessages.filterIsInstance<NetworkMessage.SaveRequest>().collect { req ->
            val result = performHostSave()
            server.send(
                NetworkMessage.SaveResult(
                    requestId = req.requestId,
                    success = result.isSuccess,
                    errorMessage = result.exceptionOrNull()?.message,
                ),
            )
        }
    }

    // Host side: on tablet's snapshot request, send all current strokes.
    // Backfills missing strokeIds in-place so future erase/sync operations
    // can target the same stroke by id.
    LaunchedEffect(peerServer, syncEngine) {
        val server = peerServer ?: return@LaunchedEffect
        server.incomingMessages
            .filterIsInstance<NetworkMessage.AnnotationSnapshotRequest>()
            .collect {
                val deviceId = syncEngine?.deviceId ?: "host"
                val collected = mutableListOf<StrokeDelta.Added>()
                drawingStates.forEach { (pageIndex, state) ->
                    val paths = state.currentPaths
                    for (i in paths.indices) {
                        val original = paths[i]
                        val id = original.strokeId.ifEmpty {
                            syncEngine?.newStrokeId() ?: "$deviceId#legacy-$pageIndex-$i"
                        }
                        if (original.strokeId.isEmpty()) {
                            paths[i] = original.copy(strokeId = id)
                        }
                        collected.add(
                            StrokeDelta.Added(
                                strokeId = id,
                                pageIndex = pageIndex,
                                authorDeviceId = deviceId,
                                clock = 0,
                                path = paths[i].toDto(id),
                            ),
                        )
                    }
                }
                logger.info { "Sending annotation snapshot: ${collected.size} strokes" }
                server.send(NetworkMessage.AnnotationSnapshot(strokes = collected))
            }
    }

    // Tablet side: after the local bundle load, ask the host for its snapshot
    // and merge it into drawingStates (dedup by strokeId).
    LaunchedEffect(peerClient, filePath) {
        val client = peerClient ?: return@LaunchedEffect
        // Give the LaunchedEffect(filePath) annotation-load a chance to populate
        // drawingStates before requesting — the dedup later filters duplicates,
        // so order is not strictly required, but this keeps logs cleaner.
        runCatching { client.send(NetworkMessage.AnnotationSnapshotRequest) }
            .onFailure { e ->
                logger.warn { "Failed to request annotation snapshot: ${e::class.simpleName}" }
            }
        client.incomingMessages
            .filterIsInstance<NetworkMessage.AnnotationSnapshot>()
            .collect { snapshot ->
                logger.info { "Received annotation snapshot: ${snapshot.strokes.size} strokes" }
                snapshot.strokes.forEach { added ->
                    val state = drawingStates.getOrPut(added.pageIndex) { PdfDrawingState() }
                    if (state.currentPaths.none { it.strokeId == added.strokeId }) {
                        state.currentPaths.add(added.path.toDomain())
                        state.markHistoryChanged()
                    }
                }
            }
    }

    // Tablet side: surface connection loss.
    var showLostConnectionDialog by remember { mutableStateOf(false) }
    LaunchedEffect(peerClient) {
        val client = peerClient ?: return@LaunchedEffect
        client.state.collect { st ->
            showLostConnectionDialog = st is PairingState.LostConnection
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
                    DrawablePdfPage(
                        bitmap = bm,
                        pdfDrawingState = pdfDrawingState,
                        toolMode = effectiveToolMode,
                        penSettings = penSettings,
                        markerSettings = markerSettings,
                        eraserSettings = eraserSettings,
                        eraserOverride = eraserOverrideProvider,
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
            )
        }

        AnimatedVisibility(
            visible = true,
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomStart)
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
                onSave = {
                    isSaving = true
                    coroutineScope.launch {
                        val message = if (peerClient != null) {
                            val requestId = "save-${Random.nextLong().toString(16)}"
                            peerClient.send(NetworkMessage.SaveRequest(requestId))
                            val reply = withTimeoutOrNull(5.seconds) {
                                peerClient.incomingMessages
                                    .filterIsInstance<NetworkMessage.SaveResult>()
                                    .filter { it.requestId == requestId }
                                    .first()
                            }
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
                },
                onExport = {
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
                },
                scale = currentScalePercent,
                onZoomIn = {
                    val centre = Offset(
                        windowSizeInPx.width / 2f,
                        windowSizeInPx.height / 2f,
                    )
                    pdfViewerState.zoomBy(TOOLBAR_ZOOM_STEP_IN, centre)
                },
                onZoomOut = {
                    val centre = Offset(
                        windowSizeInPx.width / 2f,
                        windowSizeInPx.height / 2f,
                    )
                    pdfViewerState.zoomBy(TOOLBAR_ZOOM_STEP_OUT, centre)
                },
            )
        }

        val panelMaxWidth = with(LocalDensity.current) { windowSizeInPx.width.toDp() - 32.dp }
        ToolSettingsFloatingPanel(
            toolMode = toolMode,
            penSettings = penSettings,
            onPenSettingsChange = { penSettings = it },
            markerSettings = markerSettings,
            onMarkerSettingsChange = { markerSettings = it },
            eraserSettings = eraserSettings,
            onEraserSettingsChange = { eraserSettings = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = panelMaxWidth),
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        AnimatedVisibility(
            visible = pages.isNotEmpty(),
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp),
        ) {
            PageIndicatorAirbar(
                currentPage = firstVisiblePage + 1,
                totalPages = pages.size,
            )
        }

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

        IconButton(
            onClick = {
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
            },
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
