package ru.kyamshanov.notepen

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import ru.kyamshanov.notepen.reflow.BuildReflowReadingUseCase
import ru.kyamshanov.notepen.reflow.ReflowPageLocator
import ru.kyamshanov.notepen.reflow.ReflowReading
import ru.kyamshanov.notepen.reflow.api.PdfReflowExtractor
import ru.kyamshanov.notepen.reflow.ui.ReflowReader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.model.EraserSettings
import ru.kyamshanov.notepen.annotation.domain.model.MarkerSettings
import ru.kyamshanov.notepen.annotation.domain.model.PageExtent
import ru.kyamshanov.notepen.annotation.domain.model.PenSettings
import ru.kyamshanov.notepen.annotation.domain.model.sanitizedForCurrentScheme
import ru.kyamshanov.notepen.annotation.domain.port.AnnotationRepository
import ru.kyamshanov.notepen.annotation.domain.port.PdfExporter
import ru.kyamshanov.notepen.magnifier.LoupeSelectionController
import ru.kyamshanov.notepen.magnifier.MagnifierInputPanel
import ru.kyamshanov.notepen.magnifier.asMagnifierGeometry
import ru.kyamshanov.notepen.pdf.domain.port.PdfDocumentLoader
import ru.kyamshanov.notepen.pdf.domain.port.PdfPageRenderer
import ru.kyamshanov.notepen.pdf.presentation.toImageBitmap
import ru.kyamshanov.notepen.shortcuts.domain.model.ShortcutBinding
import ru.kyamshanov.notepen.shortcuts.domain.model.ShortcutsSettings
import ru.kyamshanov.notepen.sync.SyncBridge
import ru.kyamshanov.notepen.sync.domain.SyncEngine
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.model.PairingState
import ru.kyamshanov.notepen.sync.domain.model.RectDto
import ru.kyamshanov.notepen.sync.domain.model.StrokeDelta
import ru.kyamshanov.notepen.sync.domain.model.toDomain
import ru.kyamshanov.notepen.sync.domain.model.toDto
import ru.kyamshanov.notepen.sync.domain.port.SyncClient
import ru.kyamshanov.notepen.pdfviewer.PdfViewerState
import ru.kyamshanov.notepen.pdfviewer.ScrollMode
import ru.kyamshanov.notepen.pdfviewer.asPageLayoutGeometry
import ru.kyamshanov.notepen.tablet.LocalTabletInputController
import ru.kyamshanov.notepen.tablet.PenPointerEventType
import ru.kyamshanov.notepen.tablet.stylusEventSink
import ru.kyamshanov.notepen.pdfviewer.PdfPagesViewer
import ru.kyamshanov.notepen.tabs.DocumentId
import ru.kyamshanov.notepen.tabs.Panel
import ru.kyamshanov.notepen.tabs.PdfDocumentState
import ru.kyamshanov.notepen.tabs.TAB_BAR_HEIGHT
import ru.kyamshanov.notepen.tabs.TabBar
import ru.kyamshanov.notepen.tabs.TabCloseResult
import ru.kyamshanov.notepen.tabs.TabSession

private val panelLogger = KotlinLogging.logger {}

/** Куда роутится текущий активный pointer-жест в [EditorPanel]. */
private enum class PanelGestureRoute { NONE, DRAWING, LOUPE, MAGNIFIER, TARGET_RECT }

private const val PANEL_TOOLBAR_ZOOM_STEP_IN = 1.1f
private const val PANEL_TOOLBAR_ZOOM_STEP_OUT = 1f / PANEL_TOOLBAR_ZOOM_STEP_IN
private val PANEL_REPLAY_DEADLINE = 10.seconds
private const val PANEL_AUTOSAVE_DEBOUNCE = 2_000L
private const val PANEL_HIGH_RES_DIM_PX = 4000
private const val FIGURE_PAGE_RENDER_WIDTH_PX = 1600
private const val PANEL_SIDEBAR_ANIM_MS = 220

/**
 * Per-panel actions and read-outs the unified toolbar drives for the focused
 * panel. Published by [EditorPanel] via `onControlsChanged` while it is
 * focused; the toolbar in `DetailsContent` reads from the latest published
 * instance. All actions run on the panel's own document state.
 */
class PanelControls(
    val scalePercent: Int,
    val currentPage: Int,
    val totalPages: Int,
    val hasAnnotations: Boolean,
    val isExporting: Boolean,
    val magnifierEnabled: Boolean,
    val showThumbnails: Boolean,
    val readingModeEnabled: Boolean,
    val quickLoupeArmed: Boolean,
    val scrollMode: ScrollMode,
    val zoomIn: () -> Unit,
    val zoomOut: () -> Unit,
    val toggleMagnifier: () -> Unit,
    val export: () -> Unit,
    val toggleThumbnails: () -> Unit,
    val toggleReadingMode: () -> Unit,
    val navigateToPage: (Int) -> Unit,
    val toggleQuickLoupe: () -> Unit,
    val cycleScrollMode: () -> Unit,
)

/**
 * Renders one workspace panel: its tab strip plus the active tab's PDF viewer,
 * drawing / loupe / magnifier pipeline and per-document sync. Tool selection
 * state (mode, pen / marker / eraser, pencil-mode) is owned by the unified
 * toolbar in `DetailsContent` and passed in read-only; strokes are written to
 * this panel's active [PdfDocumentState].
 *
 * While [isFocused], publishes a [PanelControls] through [onControlsChanged]
 * so the global toolbar can drive zoom / magnifier / export / page navigation
 * on this panel.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun EditorPanel(
    panel: Panel,
    tabSession: TabSession,
    isFocused: Boolean,
    loader: PdfDocumentLoader,
    renderer: PdfPageRenderer,
    toolMode: ToolMode,
    penSettings: PenSettings,
    markerSettings: MarkerSettings,
    eraserSettings: EraserSettings,
    pencilModeEnabled: Boolean,
    eraserOverride: Boolean,
    shortcutsSettings: ShortcutsSettings,
    ctrlHeld: Boolean,
    shiftHeld: Boolean,
    altHeld: Boolean,
    metaHeld: Boolean,
    nonModifierKeysDown: Set<Long>,
    penButtonsPressed: Set<Int>,
    annotationRepository: AnnotationRepository,
    pdfExporter: PdfExporter,
    reflowExtractor: PdfReflowExtractor,
    syncEngineFor: ((documentId: String) -> SyncEngine)?,
    peerClient: SyncClient?,
    pendingDeltaCounts: kotlinx.coroutines.flow.Flow<Map<String, Int>>?,
    receivedPdfDir: String?,
    openDocumentRegistry: ru.kyamshanov.notepen.sync.domain.port.OpenDocumentRegistry?,
    hostAnnotationSnapshotFor: (suspend (documentId: String) -> List<StrokeDelta.Added>)?,
    showSnackbar: (String) -> Unit,
    onRestoreToolSettings: (PenSettings, MarkerSettings, EraserSettings) -> Unit,
    onAddTab: () -> Unit,
    onAllTabsClosed: () -> Unit,
    onOpenPanelPicker: ((DocumentId) -> Unit)?,
    onClosePanel: (() -> Unit)?,
    onControlsChanged: (PanelControls?) -> Unit,
    fitWidthStartInset: androidx.compose.ui.unit.Dp = 0.dp,
    fitWidthTopInset: androidx.compose.ui.unit.Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val openDocs = panel.tabs
    val activeTab = openDocs.activeTab
    if (activeTab == null) {
        onControlsChanged(null)
        return
    }
    val pdfState = tabSession.stateOf(activeTab)
    val filePath = pdfState.filePath
    val documentId = pdfState.documentId
    val pdfDocument = pdfState.pdfDocument
    val pages = pdfState.pages
    val drawingStates = pdfState.drawingStates
    val favoritePageIndices = pdfState.favoritePageIndices
    val pdfViewerState: PdfViewerState = pdfState.pdfViewerState
    val magnifierState = pdfState.magnifierState
    val globalUndoStack = pdfState.undoStack
    val globalRedoStack = pdfState.redoStack

    val coroutineScope = rememberCoroutineScope()
    val tabletController = LocalTabletInputController.current
    val syncEngine = remember(syncEngineFor, documentId) { syncEngineFor?.invoke(documentId) }

    var panelSizePx by remember { mutableStateOf(IntSize.Zero) }
    var showThumbnails by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }

    // ---- Reading (reflow) mode -------------------------------------------
    val reflowReadingUseCase = remember(reflowExtractor) { BuildReflowReadingUseCase(reflowExtractor) }
    val reflowListState = remember(pdfState) { LazyListState() }
    // Кэш растеризованных страниц для врезок-картинок reflow (одна страница — много фигур).
    val figurePageCache = remember(pdfState) { mutableMapOf<Int, ImageBitmap>() }
    val renderFigurePage: suspend (Int) -> ImageBitmap? = renderFig@{ pageIndex ->
        figurePageCache[pageIndex]?.let { return@renderFig it }
        val doc = pdfState.pdfDocument ?: return@renderFig null
        val info = doc.info.pages.getOrNull(pageIndex) ?: return@renderFig null
        val width = FIGURE_PAGE_RENDER_WIDTH_PX
        val height = (width / (info.aspectRatio.takeIf { it > 0f } ?: 1f)).toInt().coerceAtLeast(1)
        runCatching { renderer.renderPage(doc, pageIndex, width, height).toImageBitmap() }
            .getOrNull()
            ?.also { figurePageCache[pageIndex] = it }
    }
    var readingMode by remember(pdfState) { mutableStateOf(false) }
    var reflowReading by remember(pdfState) { mutableStateOf<ReflowReading?>(null) }
    LaunchedEffect(readingMode, pdfState) {
        if (!readingMode) {
            reflowReading = null
            return@LaunchedEffect
        }
        // Запоминаем текущую страницу до извлечения, чтобы при открытии режима
        // чтения сразу встать на соответствующий абзац.
        val targetPage = pdfViewerState.firstVisiblePageIndex
        val strokesByPage = drawingStates.mapValues { (_, state) -> state.currentPaths.toList() }
        val reading = runCatching { reflowReadingUseCase(filePath, strokesByPage) }
            .onFailure { e -> panelLogger.warn { "Reflow reading build failed: ${e::class.simpleName}" } }
            .getOrNull()
        reflowReading = reading
        if (reading != null) {
            ReflowPageLocator.blockIndexForPage(reading.document, targetPage)
                ?.let { reflowListState.scrollToItem(it) }
        }
    }
    // В режиме чтения держим текущую страницу (счётчик + позиция при выходе) в
    // синхроне с прокруткой ридера: первый видимый блок → его исходная страница.
    LaunchedEffect(readingMode, pdfState) {
        if (!readingMode) return@LaunchedEffect
        snapshotFlow { reflowListState.firstVisibleItemIndex }
            .mapNotNull { index -> reflowReading?.let { ReflowPageLocator.pageForBlock(it.document, index) } }
            .distinctUntilChanged()
            .collect { page -> pdfViewerState.scrollToPage(page, 0) }
    }

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
    SideEffect {
        pdfViewerState.pageExtentProvider = { pageIndex ->
            drawingStates[pageIndex]?.extent?.value ?: PageExtent.Pdf
        }
    }
    val density = androidx.compose.ui.platform.LocalDensity.current
    SideEffect {
        pdfViewerState.fitWidthInsetStartPx = with(density) { fitWidthStartInset.toPx() }
        pdfViewerState.fitWidthInsetTopPx = with(density) { fitWidthTopInset.toPx() }
    }
    val firstVisiblePage by remember(pdfState) { derivedStateOf { pdfViewerState.firstVisiblePageIndex } }
    val currentScalePercent by remember(pdfState) { derivedStateOf { pdfViewerState.scalePercent } }
    val currentPageOffsetPx by remember(pdfState) { derivedStateOf { pdfViewerState.firstVisiblePageOffsetPx } }

    // ---- High-res magnifier render ---------------------------------------
    val magnifierPageIndices = magnifierState.segments.map { it.pageIndex }
    LaunchedEffect(pdfState, magnifierState.enabled, magnifierPageIndices, pdfDocument) {
        if (!magnifierState.enabled) return@LaunchedEffect
        val doc = pdfDocument ?: return@LaunchedEffect
        for (pageIndex in magnifierPageIndices) {
            val pageInfo = doc.info.pages.getOrNull(pageIndex) ?: continue
            val aspect = pageInfo.aspectRatio.takeIf { it > 0f } ?: 1f
            val widthCapped = PANEL_HIGH_RES_DIM_PX
            val heightFromWidth = (widthCapped / aspect).toInt().coerceAtLeast(1)
            val (w, h) = if (heightFromWidth > PANEL_HIGH_RES_DIM_PX) {
                val hh = PANEL_HIGH_RES_DIM_PX
                val ww = (hh * aspect).toInt().coerceAtLeast(1)
                ww to hh
            } else {
                widthCapped to heightFromWidth
            }
            launch {
                runCatching {
                    val data = renderer.renderPage(doc, pageIndex, w, h)
                    magnifierState.updateHighResBitmap(pageIndex, data.toImageBitmap())
                }.onFailure { e ->
                    panelLogger.warn { "Magnifier high-res render failed for page $pageIndex: ${e::class.simpleName}" }
                }
            }
        }
    }

    val syncBridge = remember(syncEngine) {
        syncEngine?.let { SyncBridge(engine = it, drawingStates = drawingStates, scope = coroutineScope) }
    }
    LaunchedEffect(syncBridge) { syncBridge?.start() }

    // ---- Tablet: request annotation snapshot from connected hosts ---------
    LaunchedEffect(peerClient, filePath, documentId) {
        val client = peerClient ?: return@LaunchedEffect
        runCatching {
            client.broadcast(NetworkMessage.AnnotationSnapshotRequest(documentId = documentId))
        }.onFailure { e ->
            panelLogger.warn { "Failed to request annotation snapshot: ${e::class.simpleName}" }
        }
        client.incomingMessages
            .filter { it.message is NetworkMessage.AnnotationSnapshot }
            .map { it.message as NetworkMessage.AnnotationSnapshot }
            .filter { it.documentId.isEmpty() || it.documentId == documentId }
            .collect { snapshot ->
                snapshot.strokes.forEach { added ->
                    val state = drawingStates.getOrPut(added.pageIndex) { PdfDrawingState() }
                    added.pageExtent?.let { extDto -> state.setExtent(state.extent.value.union(extDto.toDomain())) }
                    if (state.currentPaths.none { it.strokeId == added.strokeId }) {
                        state.currentPaths.add(added.path.toDomain())
                        state.markHistoryChanged()
                    }
                }
            }
    }

    // ---- Tablet: aggregate pairing state + offline / reconnect snackbars --
    var clientPairingState by remember { mutableStateOf<PairingState?>(null) }
    val isRemoteOpenedDoc = remember(filePath, receivedPdfDir) {
        receivedPdfDir != null && filePath.startsWith(receivedPdfDir)
    }
    LaunchedEffect(peerClient) {
        val client = peerClient ?: return@LaunchedEffect
        client.pairingStates.collect { states ->
            clientPairingState = when {
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
        }
    }

    var pendingForDoc by remember { mutableStateOf(0) }
    LaunchedEffect(pendingDeltaCounts, documentId) {
        val flow = pendingDeltaCounts ?: return@LaunchedEffect
        flow.collect { counts -> pendingForDoc = counts[documentId] ?: 0 }
    }
    val showOfflineBanner = peerClient != null &&
        clientPairingState != null &&
        clientPairingState !is PairingState.Idle &&
        clientPairingState !is PairingState.Connected &&
        pendingForDoc > 0

    val saveLocallyAndNotify: suspend (String) -> Unit = { message ->
        val annotations = drawingStates.mapValues { (_, state) -> state.currentPaths.toList() }
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
        showSnackbar(if (result.isSuccess) message else "Ошибка локального сохранения")
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
                previouslyConnected = false
                previouslyOffline = true
                saveLocallyAndNotify("Пропало соединение. Документ сохранён локально")
            }
            previouslyOffline && nowConnected -> {
                previouslyOffline = false
                previouslyConnected = true
                val pendingAtReconnect = pendingForDoc
                if (pendingAtReconnect <= 0) {
                    showSnackbar("Соединение восстановлено")
                } else {
                    val flow = pendingDeltaCounts
                    val syncedInTime = if (flow != null) {
                        withTimeoutOrNull(PANEL_REPLAY_DEADLINE) {
                            flow.first { (it[documentId] ?: 0) == 0 }
                            true
                        } ?: false
                    } else {
                        false
                    }
                    showSnackbar(
                        if (syncedInTime) "Соединение восстановлено. Изменения синхронизированы"
                        else "Соединение восстановлено, но не все изменения отправлены",
                    )
                }
            }
            nowConnected -> previouslyConnected = true
        }
    }

    // ---- PDF load + preload ----------------------------------------------
    LaunchedEffect(pdfState) {
        if (pdfState.pdfDocument == null && !pdfState.isPdfLoading) {
            pdfState.isPdfLoading = true
            try {
                pdfState.pdfDocument = loader.load(filePath)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                panelLogger.warn { "Failed to open PDF: ${e::class.simpleName}" }
                pdfState.pdfDocument = null
            } finally {
                pdfState.isPdfLoading = false
            }
        }
    }
    LaunchedEffect(openDocs.tabs) {
        openDocs.tabs.forEach { tab ->
            val state = tabSession.stateOf(tab)
            if (state.pdfDocument == null && !state.isPdfLoading) {
                val sameFileIsLoading = openDocs.tabs.any { other ->
                    other.id != tab.id &&
                        tabSession.stateOf(other).let { s -> s.filePath == state.filePath && s.isPdfLoading }
                }
                if (sameFileIsLoading) return@forEach
                state.isPdfLoading = true
                try {
                    state.pdfDocument = loader.load(state.filePath)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    panelLogger.warn { "Preload failed for ${tab.displayName}: ${e::class.simpleName}" }
                    state.pdfDocument = null
                } finally {
                    state.isPdfLoading = false
                }
            }
        }
    }

    DisposableEffect(openDocumentRegistry, documentId) {
        openDocumentRegistry?.acquire(documentId)
        onDispose { openDocumentRegistry?.release(documentId) }
    }

    // ---- Annotation load --------------------------------------------------
    LaunchedEffect(pdfState) {
        if (pdfState.annotationsLoaded) return@LaunchedEffect
        pdfState.annotationsLoaded = true
        val restoredView = annotationRepository.loadViewState(filePath).getOrNull()?.also { view ->
            pdfViewerState.applyInitialState(
                scalePercent = view.scale,
                pageIndex = if (pdfState.skipPageRestore) 0 else view.currentPage,
                pageOffsetPx = if (pdfState.skipPageRestore) 0 else view.currentPageOffset,
            )
        }
        val projectionStrokes = hostAnnotationSnapshotFor?.let { provider ->
            runCatching { provider(documentId) }.getOrElse { e ->
                panelLogger.warn { "Host projection read failed for doc=$documentId: ${e::class.simpleName}" }
                emptyList()
            }
        }.orEmpty()

        annotationRepository.load(filePath).getOrNull()?.let { bundle ->
            if (restoredView == null) {
                pdfViewerState.applyInitialState(
                    scalePercent = bundle.scale,
                    pageIndex = if (pdfState.skipPageRestore) 0 else bundle.currentPage,
                    pageOffsetPx = if (pdfState.skipPageRestore) 0 else bundle.currentPageOffset,
                )
            }
            onRestoreToolSettings(
                bundle.pen.sanitizedForCurrentScheme(),
                bundle.marker.sanitizedForCurrentScheme(),
                bundle.eraser,
            )
            if (projectionStrokes.isEmpty()) {
                bundle.pages.forEach { (pageIndex, paths) ->
                    val state = drawingStates.getOrPut(pageIndex) { PdfDrawingState() }
                    state.currentPaths.addAll(paths)
                    state.markHistoryChanged()
                }
            }
            bundle.pageExtents.forEach { (pageIndex, ext) ->
                drawingStates.getOrPut(pageIndex) { PdfDrawingState() }.setExtent(ext)
            }
            favoritePageIndices.clear()
            favoritePageIndices.addAll(bundle.favoritePageIndices)
        }
        if (projectionStrokes.isNotEmpty()) {
            projectionStrokes.forEach { added ->
                val state = drawingStates.getOrPut(added.pageIndex) { PdfDrawingState() }
                added.pageExtent?.let { extDto -> state.setExtent(state.extent.value.union(extDto.toDomain())) }
                if (state.currentPaths.none { it.strokeId == added.strokeId }) {
                    state.currentPaths.add(added.path.toDomain())
                    state.markHistoryChanged()
                }
            }
        }
    }

    // ---- Per-tab autosave -------------------------------------------------
    val saveTab: suspend (PdfDocumentState) -> Unit = { state ->
        val annotations = state.drawingStates.mapValues { (_, s) -> s.currentPaths.toList() }
        val extents = state.drawingStates.mapValues { (_, s) -> s.extent.value }
        annotationRepository.save(
            pdfPath = state.filePath,
            annotations = annotations,
            scale = state.pdfViewerState.scalePercent,
            pen = penSettings,
            marker = markerSettings,
            eraser = eraserSettings,
            currentPage = state.pdfViewerState.firstVisiblePageIndex,
            currentPageOffset = state.pdfViewerState.firstVisiblePageOffsetPx,
            favoritePageIndices = state.favoritePageIndices.toSet(),
            pageExtents = extents,
        ).onFailure { e -> panelLogger.warn { "Auto-save failed for ${state.filePath}: ${e::class.simpleName}" } }
    }
    val requestRemoteSaveIfConnected: suspend () -> Unit = {
        if (peerClient != null && clientPairingState is PairingState.Connected) {
            val requestId = "save-${Random.nextLong().toString(16)}"
            peerClient.broadcast(NetworkMessage.SaveRequest(requestId = requestId, documentId = documentId))
            withTimeoutOrNull(5.seconds) {
                peerClient.incomingMessages
                    .filter { it.message is NetworkMessage.SaveResult }
                    .map { it.message as NetworkMessage.SaveResult }
                    .filter { it.requestId == requestId }
                    .first()
            }
        }
    }
    LaunchedEffect(pdfState) {
        snapshotFlow {
            var acc = 0
            for ((_, s) in pdfState.drawingStates) acc += s.historyVersion.value
            acc + pdfState.favoritePageIndices.size +
                penSettings.hashCode() + markerSettings.hashCode() + eraserSettings.hashCode()
        }
            .drop(1)
            .distinctUntilChanged()
            .debounce(PANEL_AUTOSAVE_DEBOUNCE)
            .collect {
                saveTab(pdfState)
                if (isRemoteOpenedDoc) coroutineScope.launch { requestRemoteSaveIfConnected() }
            }
    }

    // ---- Gesture pipeline -------------------------------------------------
    val toolModeProvider = rememberUpdatedState(toolMode)
    val penSettingsProvider = rememberUpdatedState(penSettings)
    val markerSettingsProvider = rememberUpdatedState(markerSettings)
    val eraserSettingsProvider = rememberUpdatedState(eraserSettings)
    val eraserOverrideProvider = rememberUpdatedState(eraserOverride)
    val pencilModeProvider = rememberUpdatedState(pencilModeEnabled)
    val syncEngineProvider = rememberUpdatedState(syncEngine)
    val drawingController = remember(pdfViewerState, drawingStates, magnifierState) {
        MultiPageDrawingController(
            drawingStates = drawingStates,
            geometry = pdfViewerState.asPageLayoutGeometry(),
            toolMode = { toolModeProvider.value },
            penSettings = { penSettingsProvider.value },
            markerSettings = { markerSettingsProvider.value },
            eraserSettings = { eraserSettingsProvider.value },
            eraserOverride = { eraserOverrideProvider.value },
            skipPage = { magnifierState.enabled },
            onGestureStart = { pageIndex, snapshot -> pdfState.pushUndoSnapshot(pageIndex, snapshot) },
            onStrokeFinished = { pageIndex, path ->
                val state = drawingStates[pageIndex] ?: return@MultiPageDrawingController
                handlePanelStrokeFinished(state, pageIndex, path, syncEngineProvider.value)
            },
            onEraseFinished = { pageIndex, before, _ ->
                val state = drawingStates[pageIndex] ?: return@MultiPageDrawingController
                handlePanelEraseFinished(state, pageIndex, before, syncEngineProvider.value)
            },
            scope = coroutineScope,
        )
    }
    val palmRejectionActive = remember { { pencilModeProvider.value } }

    fun bindingActive(b: ShortcutBinding): Boolean {
        if (b.isEmpty) return false
        if (b.ctrl && !ctrlHeld) return false
        if (b.shift && !shiftHeld) return false
        if (b.alt && !altHeld) return false
        if (b.meta && !metaHeld) return false
        if (!penButtonsPressed.containsAll(b.penButtons)) return false
        if (b.keyCode != 0L && b.keyCode !in nonModifierKeysDown) return false
        return true
    }
    val isOpenTriggerActive = bindingActive(shortcutsSettings.loupeOpen)
    val isCloseTriggerActive = bindingActive(shortcutsSettings.loupeClose)
    val closeArmed = remember(pdfState) { mutableStateOf(false) }
    LaunchedEffect(pdfState, magnifierState.enabled) {
        closeArmed.value = magnifierState.enabled && !isCloseTriggerActive
    }
    LaunchedEffect(pdfState, isCloseTriggerActive) {
        if (!magnifierState.enabled) return@LaunchedEffect
        if (!isCloseTriggerActive) {
            closeArmed.value = true
        } else if (closeArmed.value) {
            closeArmed.value = false
            magnifierState.disable()
        }
    }

    val pinnedRect = remember(pdfState) { mutableStateOf<Rect?>(null) }
    val magnifierTargetGestureController = remember(pdfViewerState, magnifierState) {
        ru.kyamshanov.notepen.magnifier.MagnifierTargetGestureController(
            state = magnifierState,
            viewerState = pdfViewerState,
            onMoveFinished = {
                if (magnifierState.attachment == ru.kyamshanov.notepen.magnifier.MagnifierAttachment.SCREEN) {
                    pinnedRect.value = magnifierState.targetRectInViewport(pdfViewerState)
                }
            },
        )
    }
    LaunchedEffect(pdfState, magnifierState.attachment, magnifierState.enabled) {
        if (!magnifierState.enabled) {
            pinnedRect.value = null
            return@LaunchedEffect
        }
        pinnedRect.value = when (magnifierState.attachment) {
            ru.kyamshanov.notepen.magnifier.MagnifierAttachment.SCREEN ->
                magnifierState.targetRectInViewport(pdfViewerState)
            ru.kyamshanov.notepen.magnifier.MagnifierAttachment.PAGE -> null
        }
    }
    LaunchedEffect(pdfState, magnifierState.attachment, magnifierState.enabled, pdfViewerState.pan, pdfViewerState.zoom) {
        if (!magnifierState.enabled) return@LaunchedEffect
        if (magnifierState.attachment != ru.kyamshanov.notepen.magnifier.MagnifierAttachment.SCREEN) return@LaunchedEffect
        if (magnifierTargetGestureController.isActive) return@LaunchedEffect
        val pinned = pinnedRect.value ?: return@LaunchedEffect
        magnifierState.repinFromViewportRect(pinned, pdfViewerState)
    }

    val quickLoupeArmed = remember(pdfState) { mutableStateOf(false) }
    val loupeSelectionController = remember(pdfViewerState, magnifierState) {
        LoupeSelectionController(
            viewerState = pdfViewerState,
            viewportSizeProvider = { Size(panelSizePx.width.toFloat(), panelSizePx.height.toFloat()) },
            onSelected = { segments, viewportSize, selectionSizePx, panelCenter ->
                magnifierState.enableMulti(
                    viewportSize = viewportSize,
                    segs = segments,
                    selectionSizePx = selectionSizePx,
                    panelCenter = panelCenter,
                )
                quickLoupeArmed.value = false
            },
        )
    }
    val gestureRoute = remember(pdfState) { mutableStateOf(PanelGestureRoute.NONE) }
    val openTriggerProvider = rememberUpdatedState(isOpenTriggerActive)
    val magnifierInputControllerHolder = remember(pdfState) {
        mutableStateOf<ru.kyamshanov.notepen.magnifier.MagnifierInputController?>(null)
    }

    fun routedOnDown(viewportPos: Offset, pressure: Float, tilt: Float) {
        if (magnifierState.enabled) {
            val panelLocal = ru.kyamshanov.notepen.magnifier.viewportToPanelLocal(magnifierState, viewportPos)
            val mc = magnifierInputControllerHolder.value
            if (panelLocal != null && mc != null) {
                mc.onDown(panelLocal, magnifierState.panelSize, pressure, tilt)
                gestureRoute.value = PanelGestureRoute.MAGNIFIER
                return
            }
            if (magnifierTargetGestureController.onDown(viewportPos)) {
                gestureRoute.value = PanelGestureRoute.TARGET_RECT
                return
            }
            drawingController.onDown(viewportPos, pressure, tilt)
            gestureRoute.value = PanelGestureRoute.DRAWING
            return
        }
        if (openTriggerProvider.value || quickLoupeArmed.value) {
            loupeSelectionController.onDown(viewportPos)
            gestureRoute.value = PanelGestureRoute.LOUPE
        } else {
            drawingController.onDown(viewportPos, pressure, tilt)
            gestureRoute.value = PanelGestureRoute.DRAWING
        }
    }

    fun routedOnMove(viewportPos: Offset, pressure: Float, tilt: Float) {
        when (gestureRoute.value) {
            PanelGestureRoute.LOUPE -> loupeSelectionController.onMove(viewportPos)
            PanelGestureRoute.DRAWING -> drawingController.onMove(viewportPos, pressure, tilt)
            PanelGestureRoute.MAGNIFIER -> {
                val panelLocal = ru.kyamshanov.notepen.magnifier.viewportToPanelLocal(magnifierState, viewportPos)
                val mc = magnifierInputControllerHolder.value
                if (panelLocal != null && mc != null) mc.onMove(panelLocal, magnifierState.panelSize, pressure, tilt)
            }
            PanelGestureRoute.TARGET_RECT -> magnifierTargetGestureController.onMove(viewportPos)
            PanelGestureRoute.NONE -> Unit
        }
    }

    fun routedOnUp() {
        when (gestureRoute.value) {
            PanelGestureRoute.LOUPE -> {
                loupeSelectionController.onUp()
                quickLoupeArmed.value = false
            }
            PanelGestureRoute.DRAWING -> drawingController.onUp()
            PanelGestureRoute.MAGNIFIER -> magnifierInputControllerHolder.value?.onUp(magnifierState.panelSize)
            PanelGestureRoute.TARGET_RECT -> magnifierTargetGestureController.onUp()
            PanelGestureRoute.NONE -> Unit
        }
        gestureRoute.value = PanelGestureRoute.NONE
    }

    fun routedOnCancel() {
        when (gestureRoute.value) {
            PanelGestureRoute.LOUPE -> {
                loupeSelectionController.onCancel()
                quickLoupeArmed.value = false
            }
            PanelGestureRoute.DRAWING -> drawingController.onCancel()
            PanelGestureRoute.MAGNIFIER -> magnifierInputControllerHolder.value?.onCancel()
            PanelGestureRoute.TARGET_RECT -> magnifierTargetGestureController.onCancel()
            PanelGestureRoute.NONE -> Unit
        }
        gestureRoute.value = PanelGestureRoute.NONE
    }

    // Native pen-stream → drawing pipeline (only for the focused panel, so a
    // pen event isn't routed into every panel at once).
    LaunchedEffect(drawingController, loupeSelectionController, tabletController, isFocused) {
        if (!isFocused) return@LaunchedEffect
        tabletController.penPointerEvents.collect { ev ->
            when (ev.type) {
                PenPointerEventType.DOWN -> routedOnDown(ev.position, ev.pressure, ev.tilt)
                PenPointerEventType.UPDATE -> routedOnMove(ev.position, ev.pressure, ev.tilt)
                PenPointerEventType.UP -> routedOnUp()
                PenPointerEventType.CANCEL -> routedOnCancel()
            }
        }
    }

    // ---- Toolbar control callbacks (published while focused) --------------
    val onZoomIn: () -> Unit = {
        pdfViewerState.zoomBy(PANEL_TOOLBAR_ZOOM_STEP_IN, Offset(panelSizePx.width / 2f, panelSizePx.height / 2f))
    }
    val onZoomOut: () -> Unit = {
        pdfViewerState.zoomBy(PANEL_TOOLBAR_ZOOM_STEP_OUT, Offset(panelSizePx.width / 2f, panelSizePx.height / 2f))
    }
    val onExport: () -> Unit = {
        isExporting = true
        coroutineScope.launch {
            val annotations = drawingStates.mapValues { (_, state) -> state.currentPaths.toList() }
            val outputPath = filePath.removeSuffix(".pdf") + "_annotated.pdf"
            val result = pdfExporter.export(sourcePdfPath = filePath, annotations = annotations, outputPath = outputPath)
            isExporting = false
            showSnackbar(if (result.isSuccess) "Экспорт завершён: $outputPath" else "Ошибка экспорта")
        }
    }
    val onMagnifierToggle: () -> Unit = {
        quickLoupeArmed.value = false
        if (magnifierState.enabled) {
            magnifierState.disable()
        } else {
            val layout = pdfViewerState.layout
            val zoom = pdfViewerState.zoom
            val pan = pdfViewerState.pan
            val basePageW = layout.basePageWidthPx
            val viewportW = panelSizePx.width.toFloat()
            val viewportH = panelSizePx.height.toFloat()
            val viewportCenterDocY = ((viewportH / 2f) - pan.y) / zoom
            val viewportCenterDocX = ((viewportW / 2f) - pan.x) / zoom
            val tops = layout.pageTopsPx
            val pageIdx = when {
                tops.isEmpty() -> firstVisiblePage
                viewportCenterDocY <= tops[0] -> 0
                else -> {
                    var lo = 0
                    var hi = tops.size - 1
                    while (lo < hi) {
                        val mid = (lo + hi + 1) ushr 1
                        if (tops[mid] <= viewportCenterDocY) lo = mid else hi = mid - 1
                    }
                    lo
                }
            }
            val pdfH = if (pageIdx in 0 until layout.pageHeightsPx.size) layout.pdfHeightsPx[pageIdx] else 1f
            val pageTop = if (pageIdx in tops.indices) tops[pageIdx] else 0f
            if (basePageW > 0f && zoom > 0f) {
                magnifierState.updatePageCanvasPx(widthPx = basePageW * zoom, heightPx = pdfH * zoom)
            }
            val centerN = Offset(
                x = (viewportCenterDocX / basePageW.coerceAtLeast(1f)).coerceIn(0f, 1f),
                y = ((viewportCenterDocY - pageTop) / pdfH.coerceAtLeast(1f)).coerceIn(0f, 1f),
            )
            magnifierState.enable(
                onPage = pageIdx,
                viewportSize = Size(viewportW, viewportH),
                targetCenterOnPage = centerN,
            )
        }
    }
    val onToggleQuickLoupe: () -> Unit = {
        if (magnifierState.enabled) {
            magnifierState.disable()
            quickLoupeArmed.value = false
        } else {
            quickLoupeArmed.value = !quickLoupeArmed.value
        }
    }
    val onCycleScrollMode: () -> Unit = {
        pdfViewerState.scrollMode = when (pdfViewerState.scrollMode) {
            ScrollMode.BOTH -> ScrollMode.VERTICAL
            ScrollMode.VERTICAL -> ScrollMode.NONE
            ScrollMode.NONE -> ScrollMode.BOTH
        }
    }

    if (isFocused) {
        // Read showThumbnails here in the composition (not only inside SideEffect)
        // so the composition subscribes to it: a SideEffect block's state reads do
        // not register a recomposition dependency, so without this read toggling
        // thumbnails would never re-run the effect nor republish PanelControls.
        val thumbnailsVisible = showThumbnails
        val readingModeVisible = readingMode
        // Read scrollMode in composition (not only inside SideEffect) so the
        // composition subscribes to it — toggling it republishes PanelControls.
        val currentScrollMode = pdfViewerState.scrollMode
        SideEffect {
            onControlsChanged(
                PanelControls(
                    scalePercent = currentScalePercent,
                    currentPage = firstVisiblePage + 1,
                    totalPages = pages.size,
                    hasAnnotations = hasAnnotations,
                    isExporting = isExporting,
                    magnifierEnabled = magnifierState.enabled,
                    showThumbnails = thumbnailsVisible,
                    readingModeEnabled = readingModeVisible,
                    quickLoupeArmed = quickLoupeArmed.value,
                    scrollMode = currentScrollMode,
                    zoomIn = onZoomIn,
                    zoomOut = onZoomOut,
                    toggleMagnifier = onMagnifierToggle,
                    export = onExport,
                    toggleThumbnails = { showThumbnails = !showThumbnails },
                    toggleReadingMode = { readingMode = !readingMode },
                    navigateToPage = { page ->
                        if (readingMode) {
                            reflowReading?.let { reading ->
                                ReflowPageLocator.blockIndexForPage(reading.document, page)?.let { blockIndex ->
                                    coroutineScope.launch { reflowListState.animateScrollToItem(blockIndex) }
                                }
                            }
                        } else {
                            pdfViewerState.scrollToPage(page, 0)
                        }
                    },
                    toggleQuickLoupe = onToggleQuickLoupe,
                    cycleScrollMode = onCycleScrollMode,
                ),
            )
        }
    }

    // ---- UI ---------------------------------------------------------------
    Box(modifier.fillMaxSize().onSizeChanged { panelSizePx = it }) {
        Box(Modifier.fillMaxSize().padding(top = TAB_BAR_HEIGHT)) {
            PdfPagesViewer(
                state = pdfViewerState,
                pdfDocument = pdfDocument,
                pages = pages,
                renderer = renderer,
                modifier = Modifier
                    .fillMaxSize()
                    .stylusEventSink(tabletController)
                    .pointerHoverIcon(if (toolMode == ToolMode.NONE) PointerIcon.Hand else PointerIcon.Default),
                primaryDragPanEnabled = {
                    // Палец/указатель свободен для панорамирования, когда он не
                    // является инструментом рисования: либо инструмент неактивен,
                    // либо включён режим стилуса (рисует только перо) — как в
                    // ноут-апах, где палец скроллит, а стилус рисует.
                    (toolModeProvider.value == ToolMode.NONE || pencilModeProvider.value) &&
                        !quickLoupeArmed.value &&
                        !openTriggerProvider.value
                },
                gestureModifier = Modifier.pdfMultiPageDrawingInput(
                    key = drawingController,
                    tablet = tabletController,
                    palmRejectionActive = palmRejectionActive,
                    captureGesture = { pos ->
                        quickLoupeArmed.value ||
                            openTriggerProvider.value ||
                            (magnifierState.enabled &&
                                magnifierTargetGestureController.hitTest(pos) !=
                                ru.kyamshanov.notepen.magnifier.MagnifierTargetGestureController.Mode.NONE) ||
                            (!pencilModeProvider.value &&
                                toolModeProvider.value != ToolMode.NONE &&
                                drawingController.isInsidePdfPage(pos))
                    },
                    onDown = ::routedOnDown,
                    onMove = ::routedOnMove,
                    onUp = ::routedOnUp,
                    onCancel = ::routedOnCancel,
                ),
            ) {
                val bm = bitmap
                Box(modifier = Modifier.fillMaxSize()) {
                    if (bm != null) {
                        val pdfDrawingState = remember(pageIndex) {
                            drawingStates.getOrPut(pageIndex) { PdfDrawingState() }
                        }
                        val isMagnifierPage = magnifierState.enabled &&
                            magnifierState.segments.any { it.pageIndex == pageIndex }
                        if (isMagnifierPage) {
                            SideEffect { magnifierState.updatePageBitmap(pageIndex, bm) }
                        }
                        DrawablePdfPage(
                            bitmap = bm,
                            pdfDrawingState = pdfDrawingState,
                            toolMode = toolMode,
                            penSettings = penSettings,
                            markerSettings = markerSettings,
                            eraserSettings = eraserSettings,
                            pdfWidth = pdfWidth,
                            pdfHeight = pdfHeight,
                            pageExtent = extent,
                            magnifierState = if (isMagnifierPage) magnifierState else null,
                            pageIndex = pageIndex,
                            isMagnifierGrabbing = isMagnifierPage && magnifierTargetGestureController.isActive,
                            isZooming = { pdfViewerState.gestureScale != 1f },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(width = pdfWidth, height = pdfHeight)
                                .border(
                                    width = androidx.compose.ui.unit.Dp(0.5f),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                ),
                        )
                    }
                }
            }

            if (magnifierState.enabled &&
                magnifierState.attachment == ru.kyamshanov.notepen.magnifier.MagnifierAttachment.SCREEN &&
                !magnifierTargetGestureController.isActive
            ) {
                pinnedRect.value?.let { rect ->
                    ru.kyamshanov.notepen.magnifier.MagnifierScreenPinnedOverlay(
                        viewportRect = rect,
                        frameColor = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            loupeSelectionController.selectionRect.value?.let { currentSelection ->
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 2f,
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
                    )
                    drawRect(
                        color = androidx.compose.ui.graphics.Color(30, 136, 229),
                        topLeft = Offset(currentSelection.left, currentSelection.top),
                        size = Size(currentSelection.width, currentSelection.height),
                        style = stroke,
                    )
                }
            }

            if (showOfflineBanner) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp)
                        .fillMaxWidth(0.8f),
                ) {
                    Text(
                        text = "Оффлайн, $pendingForDoc правок ждут отправки",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

            if (magnifierState.enabled) {
                val magPdfDrawingStateProvider: (Int) -> PdfDrawingState = remember(drawingStates) {
                    { pageIdx -> drawingStates.getOrPut(pageIdx) { PdfDrawingState() } }
                }
                val magOnGestureStart: (Int, List<DrawingPath>) -> Unit = remember(pdfState) {
                    { pageIdx, snapshot -> pdfState.pushUndoSnapshot(pageIdx, snapshot) }
                }
                val syncEngineRef = rememberUpdatedState(syncEngine)
                val magOnStrokeFinished: (Int, DrawingPath) -> Unit = remember(drawingStates) {
                    { pageIdx, path ->
                        drawingStates[pageIdx]?.let { handlePanelStrokeFinished(it, pageIdx, path, syncEngineRef.value) }
                    }
                }
                val magOnEraseFinished: (Int, List<DrawingPath>, List<DrawingPath>) -> Unit = remember(drawingStates) {
                    { pageIdx, before, _ ->
                        drawingStates[pageIdx]?.let { handlePanelEraseFinished(it, pageIdx, before, syncEngineRef.value) }
                    }
                }
                val magEraserOverrideState = rememberUpdatedState(eraserOverride)
                val magEraserOverrideProvider = remember { { magEraserOverrideState.value } }
                val magEraserPos = remember { mutableStateOf<ru.kyamshanov.notepen.drawing.api.EraserPosition?>(null) }
                val magToolModeProvider = rememberUpdatedState(toolMode)
                val magPenSettingsProvider = rememberUpdatedState(penSettings)
                val magMarkerSettingsProvider = rememberUpdatedState(markerSettings)
                val magEraserSettingsProvider = rememberUpdatedState(eraserSettings)
                val magnifierInputController = remember(magnifierState) {
                    ru.kyamshanov.notepen.magnifier.MagnifierInputController(
                        geometry = magnifierState.asMagnifierGeometry(),
                        pdfDrawingStateProvider = magPdfDrawingStateProvider,
                        toolMode = { magToolModeProvider.value },
                        penSettings = { magPenSettingsProvider.value },
                        markerSettings = { magMarkerSettingsProvider.value },
                        eraserSettings = { magEraserSettingsProvider.value },
                        eraserOverride = magEraserOverrideProvider,
                        eraserPos = magEraserPos,
                        onGestureStart = magOnGestureStart,
                        onStrokeFinished = magOnStrokeFinished,
                        onEraseFinished = magOnEraseFinished,
                        scope = coroutineScope,
                        pageAspect = { pageIndex ->
                            val layout = pdfViewerState.layout
                            val h = layout.pdfHeightsPx.getOrNull(pageIndex) ?: 0f
                            if (h > 0f) layout.basePageWidthPx / h else 1f
                        },
                    )
                }
                magnifierInputControllerHolder.value = magnifierInputController

                MagnifierInputPanel(
                    state = magnifierState,
                    pdfDrawingStateProvider = magPdfDrawingStateProvider,
                    toolMode = toolMode,
                    penSettings = penSettings,
                    markerSettings = markerSettings,
                    eraserSettings = eraserSettings,
                    eraserOverride = magEraserOverrideProvider,
                    pencilModeEnabled = pencilModeEnabled,
                    onGestureStart = magOnGestureStart,
                    onStrokeFinished = magOnStrokeFinished,
                    onEraseFinished = magOnEraseFinished,
                    onClose = { magnifierState.disable() },
                    externalInputController = magnifierInputController,
                )
            }

            if (readingMode) {
                val reading = reflowReading
                if (reading != null) {
                    ReflowReader(
                        document = reading.document,
                        modifier = Modifier.fillMaxSize(),
                        highlights = reading.highlights,
                        listState = reflowListState,
                        renderPage = renderFigurePage,
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                        Text(
                            text = "Готовим режим чтения…",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }
        }

        TabBar(
            side = panel.id,
            openDocs = openDocs,
            onSelect = { _, id -> tabSession.setActiveTab(panel.id, id) },
            onClose = { _, id ->
                val result = tabSession.closeTab(panel.id, id)
                if (result == TabCloseResult.AllClosed) onAllTabsClosed()
            },
            onAddTab = { onAddTab() },
            onOpenInNewPanel = onOpenPanelPicker,
            onClosePanel = onClosePanel,
            modifier = Modifier.align(Alignment.TopStart),
        )
    }
}

/** strokeId-stamp + sync publish for a finished stroke. */
private fun handlePanelStrokeFinished(
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

/** strokeId reconciliation + sync publish after an erase / partial erase. */
private fun handlePanelEraseFinished(
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
        if (orig.points == p.points && p.strokeId.isNotEmpty()) intactIds.add(p.strokeId)
    }
    val removedOrModified = beforeIds - intactIds
    if (removedOrModified.isEmpty()) return
    val newAdded = mutableListOf<DrawingPath>()
    for (i in pdfDrawingState.currentPaths.indices) {
        val p = pdfDrawingState.currentPaths[i]
        if (p.strokeId.isEmpty() || p.strokeId in removedOrModified) {
            val stamped = p.copy(strokeId = engine.newStrokeId())
            pdfDrawingState.currentPaths[i] = stamped
            newAdded.add(stamped)
        }
    }
    val ext = pdfDrawingState.extent.value
    val extDto = if (ext != PageExtent.Pdf) RectDto.fromDomain(ext) else null
    val batch = buildList {
        for (id in removedOrModified) add(
            StrokeDelta.Removed(strokeId = id, pageIndex = pageIndex, authorDeviceId = engine.deviceId, clock = 0),
        )
        for ((idx, p) in newAdded.withIndex()) add(
            StrokeDelta.Added(
                strokeId = p.strokeId,
                pageIndex = pageIndex,
                authorDeviceId = engine.deviceId,
                clock = 0,
                path = p.toDto(p.strokeId),
                pageExtent = if (idx == 0) extDto else null,
            ),
        )
    }
    engine.applyLocalBatch(batch)
}
