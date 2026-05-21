package ru.kyamshanov.notepen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.annotation.domain.model.EraserSettings
import ru.kyamshanov.notepen.annotation.domain.model.MarkerSettings
import ru.kyamshanov.notepen.annotation.domain.model.applyStrokeWidth
import ru.kyamshanov.notepen.annotation.domain.model.PenSettings
import ru.kyamshanov.notepen.ui.glass.GlassSurface
import ru.kyamshanov.notepen.pdf.domain.port.PdfDocumentLoader
import ru.kyamshanov.notepen.pdf.domain.port.PdfPageRenderer
import ru.kyamshanov.notepen.shortcuts.ShortcutsSettingsDialog
import ru.kyamshanov.notepen.shortcuts.rememberShortcutsSettings
import ru.kyamshanov.notepen.sync.domain.SyncEngine
import ru.kyamshanov.notepen.sync.domain.documentIdFromFilePath
import ru.kyamshanov.notepen.sync.domain.model.StrokeDelta
import ru.kyamshanov.notepen.sync.domain.port.PeerServer
import ru.kyamshanov.notepen.sync.domain.port.SyncClient
import ru.kyamshanov.notepen.tablet.LocalTabletInputController
import ru.kyamshanov.notepen.tabs.DocumentId
import ru.kyamshanov.notepen.tabs.LayoutTemplate
import ru.kyamshanov.notepen.tabs.PdfDocumentState
import ru.kyamshanov.notepen.tabs.TAB_BAR_HEIGHT
import ru.kyamshanov.notepen.tabs.GridContainer
import ru.kyamshanov.notepen.tabs.LayoutPickerOverlay
import ru.kyamshanov.notepen.tabs.PanelId
import ru.kyamshanov.notepen.tabs.TabCloseResult
import ru.kyamshanov.notepen.tabs.rememberTabSession

private val logger = KotlinLogging.logger {}

internal const val BACK_CONTENT_DESCRIPTION = "Назад"

private const val TOOLBAR_ZOOM_STEP_IN = 1.1f
private const val TOOLBAR_ZOOM_STEP_OUT = 1f / 1.1f

/**
 * Editor screen: a unified toolbar plus a grid of 1–[ru.kyamshanov.notepen.tabs.MAX_PANELS]
 * [EditorPanel]s. Tab / panel / layout state lives in the
 * [ru.kyamshanov.notepen.tabs.TabSession]; the toolbar drives the focused
 * panel through the [PanelControls] it publishes.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DetailsContent(
    component: DetailsComponent,
    loader: PdfDocumentLoader,
    renderer: PdfPageRenderer,
    syncEngineFor: ((documentId: String) -> SyncEngine)? = null,
    @Suppress("UNUSED_PARAMETER")
    peerServer: PeerServer? = null,
    peerClient: SyncClient? = null,
    pendingDeltaCounts: kotlinx.coroutines.flow.Flow<Map<String, Int>>? = null,
    receivedPdfDir: String? = null,
    openDocumentRegistry: ru.kyamshanov.notepen.sync.domain.port.OpenDocumentRegistry? = null,
    localDocumentIdRegistry: ru.kyamshanov.notepen.sync.domain.port.LocalDocumentIdRegistry? = null,
    hostAnnotationSnapshotFor: (suspend (documentId: String) -> List<StrokeDelta.Added>)? = null,
    modifier: Modifier = Modifier,
) {
    val localWindowInfo = LocalWindowInfo.current
    val windowSizeInPx = localWindowInfo.containerSize
    val isLandscape = windowSizeInPx.width > windowSizeInPx.height
    val density = LocalDensity.current
    val model by component.model.subscribeAsState()
    val initialFilePath = remember(model.title) { model.title }

    val syncDocumentIdFor: (String) -> String =
        remember(localDocumentIdRegistry, receivedPdfDir) {
            { path ->
                val fromRegistry = if (receivedPdfDir != null && path.startsWith(receivedPdfDir)) {
                    localDocumentIdRegistry?.lookup(path)
                } else {
                    null
                }
                fromRegistry ?: documentIdFromFilePath(path)
            }
        }

    var savedExtraTabPaths by rememberSaveable { mutableStateOf("") }
    val tabSession = rememberTabSession(
        initialFilePath = initialFilePath,
        syncDocumentIdFor = syncDocumentIdFor,
    )
    val layout = tabSession.layout

    // Restore previously-opened extra tabs into the (initial) focused panel,
    // then keep savedExtraTabPaths in sync with the focused panel's tabs.
    LaunchedEffect(tabSession) {
        savedExtraTabPaths.lines().filter { it.isNotBlank() }.forEach { path ->
            tabSession.openTab(
                panelId = tabSession.layout.focusedPanelId,
                filePath = path,
                displayName = resolveDocumentDisplayName(path),
            )
        }
        snapshotFlow { tabSession.layout }
            .collect { l ->
                val focused = l.panelOf(l.focusedPanelId) ?: l.panels.firstOrNull()
                val extra = focused?.tabs?.tabs?.drop(1)?.map { it.filePath }.orEmpty()
                savedExtraTabPaths = extra.joinToString("\n")
            }
    }

    // Library "+" opens a file into the focused panel as a new tab.
    val pendingTabUri by component.pendingTabUri.subscribeAsState()
    LaunchedEffect(pendingTabUri) {
        if (pendingTabUri.isBlank()) return@LaunchedEffect
        tabSession.openTab(
            panelId = tabSession.layout.focusedPanelId,
            filePath = pendingTabUri,
            displayName = resolveDocumentDisplayName(pendingTabUri),
        )
        component.onPendingTabHandled()
    }

    DisposableEffect(tabSession) {
        onDispose { tabSession.disposeAll() }
    }

    // ---- Global tool state (shared across all panels) ---------------------
    var toolMode by remember { mutableStateOf(ToolMode.NONE) }
    var penSettings by remember { mutableStateOf(PenSettings()) }
    var markerSettings by remember { mutableStateOf(MarkerSettings()) }
    // The marker default width is auto-derived from the document's text line
    // height; once the user tweaks it or a saved width is restored, lock it.
    var markerWidthPinned by remember { mutableStateOf(false) }
    var eraserSettings by remember { mutableStateOf(EraserSettings()) }
    var pencilModeEnabled by remember { mutableStateOf(false) }
    var pencilModeManuallyTouched by remember { mutableStateOf(false) }

    // Derive the marker's default thickness from the focused document's text
    // line height (where the PDF engine exposes it). Skipped once the width is
    // pinned by a manual change or a restored saved value.
    LaunchedEffect(tabSession, markerWidthPinned) {
        if (markerWidthPinned) return@LaunchedEffect
        snapshotFlow { tabSession.focusedActiveState?.pdfDocument }
            .collect { document ->
                if (markerWidthPinned || document == null) return@collect
                val lineHeight = renderer.documentTextLineHeight(document) ?: return@collect
                markerSettings = markerSettings.applyStrokeWidth(lineHeight)
            }
    }

    val tabletController = LocalTabletInputController.current
    val barrelPressed by tabletController.barrelPressed.collectAsState()
    val eraserTipActive by tabletController.eraserTipActive.collectAsState()
    val stylusEverSeen by tabletController.stylusEverSeen.collectAsState()
    val penButtonsPressed by tabletController.penButtons.collectAsState()
    val shortcutsSettingsState = rememberShortcutsSettings()
    val shortcutsSettings = shortcutsSettingsState.value
    val barrelBoundToLoupe =
        1 in shortcutsSettings.loupeOpen.penButtons || 1 in shortcutsSettings.loupeClose.penButtons
    val eraserOverride = (barrelPressed && !barrelBoundToLoupe) || eraserTipActive

    LaunchedEffect(stylusEverSeen, pencilModeManuallyTouched) {
        if (pencilModeManuallyTouched) return@LaunchedEffect
        pencilModeEnabled = stylusEverSeen
    }

    // ---- Global keyboard modifiers + undo/redo on the focused panel -------
    var ctrlHeld by remember { mutableStateOf(false) }
    var shiftHeld by remember { mutableStateOf(false) }
    var altHeld by remember { mutableStateOf(false) }
    var metaHeld by remember { mutableStateOf(false) }
    val nonModifierKeysDown = remember { mutableStateMapOf<Long, Unit>() }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    var showShortcutsDialog by remember { mutableStateOf(false) }
    // Tab pending a "move into a new panel" once the user picks a layout.
    var pendingPanelMove by remember { mutableStateOf<Pair<PanelId, DocumentId>?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val annotationRepository = remember { createAnnotationRepository() }
    val pdfExporter = remember { createPdfExporter() }

    var landscapeToolbarWidthDp by remember { mutableStateOf(FLOATING_TOOLBAR_WIDTH) }
    var landscapePageCounterHeightDp by remember { mutableStateOf(TAB_BAR_HEIGHT) }

    // ---- Focused-panel controls bridge ------------------------------------
    var focusedControls by remember { mutableStateOf<PanelControls?>(null) }
    val controls = focusedControls
    val totalPages = controls?.totalPages ?: 0
    val currentPage = controls?.currentPage ?: 1
    val scale = controls?.scalePercent ?: 100
    val hasAnnotations = controls?.hasAnnotations ?: false
    val isExporting = controls?.isExporting ?: false
    val magnifierEnabled = controls?.magnifierEnabled ?: false
    val showThumbnails = controls?.showThumbnails ?: false

    // ---- Save helpers (across all panels) ---------------------------------
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
        ).onFailure { e -> logger.warn { "Save failed for ${state.filePath}: ${e::class.simpleName}" } }
    }
    val saveAllOpenTabs: suspend () -> Unit = {
        tabSession.layout.panels
            .flatMap { it.tabs.tabs }
            .distinctBy { it.id }
            .forEach { saveTab(tabSession.stateOf(it)) }
    }
    val onBackWithSave: () -> Unit = {
        coroutineScope.launch {
            saveAllOpenTabs()
            component.saveLastPageIndex(currentPage - 1)
            component.onBack()
        }
    }
    val onOpenLibrary: () -> Unit = {
        coroutineScope.launch {
            saveAllOpenTabs()
            component.saveLastPageIndex(currentPage - 1)
            component.openLibrary()
        }
    }

    EditorBackHandler(enabled = true) { onBackWithSave() }

    val onPencilModeChange: (Boolean) -> Unit = { enabled ->
        pencilModeEnabled = enabled
        pencilModeManuallyTouched = true
    }
    val onBackOrCloseThumbnails: () -> Unit = {
        if (controls?.showThumbnails == true) controls.toggleThumbnails() else onBackWithSave()
    }

    val statusBarsTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .focusRequester(focusRequester)
            .focusTarget()
            .onKeyEvent { e ->
                val isShift = e.key == Key.ShiftLeft || e.key == Key.ShiftRight
                val isCtrl = e.key == Key.CtrlLeft || e.key == Key.CtrlRight
                val isAlt = e.key == Key.AltLeft || e.key == Key.AltRight
                val isMeta = e.key == Key.MetaLeft || e.key == Key.MetaRight
                when {
                    isShift -> { shiftHeld = e.type == KeyEventType.KeyDown; false }
                    isCtrl -> { ctrlHeld = e.type == KeyEventType.KeyDown; false }
                    isAlt -> { altHeld = e.type == KeyEventType.KeyDown; false }
                    isMeta -> { metaHeld = e.type == KeyEventType.KeyDown; false }
                    e.type == KeyEventType.KeyDown && e.key == Key.Z && e.isCtrlPressed && !shiftHeld -> {
                        tabSession.focusedActiveState?.let { st ->
                            if (st.undoStack.isNotEmpty()) {
                                val entry = st.undoStack.removeLast()
                                val current = st.drawingStates[entry.pageIndex]?.currentPaths?.toList() ?: emptyList()
                                st.redoStack.addLast(PdfDocumentState.UndoEntry(entry.pageIndex, current))
                                st.drawingStates[entry.pageIndex]?.restoreSnapshot(entry.paths)
                            }
                        }
                        true
                    }
                    e.type == KeyEventType.KeyDown && e.key == Key.Z && e.isCtrlPressed && shiftHeld -> {
                        tabSession.focusedActiveState?.let { st ->
                            if (st.redoStack.isNotEmpty()) {
                                val entry = st.redoStack.removeLast()
                                st.undoStack.addLast(PdfDocumentState.UndoEntry(entry.pageIndex, entry.paths))
                                st.drawingStates[entry.pageIndex]?.restoreSnapshot(entry.paths)
                            }
                        }
                        true
                    }
                    else -> {
                        when (e.type) {
                            KeyEventType.KeyDown -> nonModifierKeysDown[e.key.keyCode] = Unit
                            KeyEventType.KeyUp -> nonModifierKeysDown.remove(e.key.keyCode)
                            else -> Unit
                        }
                        false
                    }
                }
            },
    ) {
        // Workspace grid. Each panel draws its own tab strip at its top; the
        // grid only clears the status bar so the top-row tab strips sit below it.
        Box(Modifier.fillMaxSize().padding(top = statusBarsTop)) {
            GridContainer(
                layout = layout,
                onSetRatio = { index, value -> tabSession.setRatio(index, value) },
                onFocusPanel = { tabSession.focusPanel(it) },
            ) { panel ->
                val templates = tabSession.availableTemplatesForAdd()
                EditorPanel(
                    panel = panel,
                    tabSession = tabSession,
                    isFocused = panel.id == layout.focusedPanelId,
                    loader = loader,
                    renderer = renderer,
                    toolMode = toolMode,
                    penSettings = penSettings,
                    markerSettings = markerSettings,
                    eraserSettings = eraserSettings,
                    pencilModeEnabled = pencilModeEnabled,
                    eraserOverride = eraserOverride,
                    shortcutsSettings = shortcutsSettings,
                    ctrlHeld = ctrlHeld,
                    shiftHeld = shiftHeld,
                    altHeld = altHeld,
                    metaHeld = metaHeld,
                    nonModifierKeysDown = nonModifierKeysDown.keys,
                    penButtonsPressed = penButtonsPressed,
                    annotationRepository = annotationRepository,
                    pdfExporter = pdfExporter,
                    syncEngineFor = syncEngineFor,
                    peerClient = peerClient,
                    pendingDeltaCounts = pendingDeltaCounts,
                    receivedPdfDir = receivedPdfDir,
                    openDocumentRegistry = openDocumentRegistry,
                    hostAnnotationSnapshotFor = hostAnnotationSnapshotFor,
                    showSnackbar = { msg -> coroutineScope.launch { snackbarHostState.showSnackbar(msg) } },
                    onRestoreToolSettings = { pen, marker, eraser ->
                        penSettings = pen
                        markerSettings = marker
                        // A restored width is the user's own choice — don't override it.
                        markerWidthPinned = true
                        eraserSettings = eraser
                    },
                    onAddTab = onOpenLibrary,
                    onAllTabsClosed = onBackWithSave,
                    onOpenPanelPicker = if (templates.isNotEmpty() && panel.tabs.tabs.size > 1) {
                        { tabId ->
                            tabSession.focusPanel(panel.id)
                            pendingPanelMove = panel.id to tabId
                        }
                    } else {
                        null
                    },
                    onClosePanel = if (layout.panels.size > 1) {
                        {
                            val anyTab = panel.tabs.tabs.firstOrNull()
                            if (anyTab != null) {
                                // Close every tab in this panel → panel is removed.
                                panel.tabs.tabs.toList().forEach { tabSession.closeTab(panel.id, it.id) }
                            }
                        }
                    } else {
                        null
                    },
                    onControlsChanged = { c -> if (panel.id == layout.focusedPanelId) focusedControls = c },
                )
            }
        }

        // ---- Unified toolbar: overlay floating just below the tab strip ------
        Box(
            Modifier
                .fillMaxSize()
                .consumeWindowInsets(WindowInsets.statusBars)
                .padding(top = TAB_BAR_HEIGHT + statusBarsTop),
        ) {
        if (isLandscape) {
            AnimatedVisibility(
                visible = true,
                enter = slideInHorizontally { -it } + fadeIn(),
                exit = slideOutHorizontally { -it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = 8.dp),
            ) {
                GlassSurface(
                    shape = CircleShape,
                    modifier = Modifier.size(width = landscapeToolbarWidthDp, height = landscapePageCounterHeightDp),
                ) {
                    IconButton(onClick = onBackOrCloseThumbnails) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = BACK_CONTENT_DESCRIPTION,
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = true,
                enter = slideInHorizontally { -it } + fadeIn(),
                exit = slideOutHorizontally { -it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp),
            ) {
                LandscapeToolRail(
                    toolMode = toolMode,
                    onToolModeChange = { toolMode = it },
                    penSettings = penSettings,
                    onPenSettingsChange = { penSettings = it },
                    markerSettings = markerSettings,
                    onMarkerSettingsChange = {
                        if (it.strokeWidth != markerSettings.strokeWidth) markerWidthPinned = true
                        markerSettings = it
                    },
                    eraserSettings = eraserSettings,
                    onEraserSettingsChange = { eraserSettings = it },
                    hasAnnotations = hasAnnotations,
                    isExporting = isExporting,
                    onExport = { controls?.export?.invoke() },
                    scale = scale,
                    onZoomIn = {
                        tabSession.focusedActiveState?.pdfViewerState?.let { vs ->
                            vs.zoomBy(TOOLBAR_ZOOM_STEP_IN, Offset(vs.viewportSize.width / 2f, vs.viewportSize.height / 2f))
                        }
                    },
                    onZoomOut = {
                        tabSession.focusedActiveState?.pdfViewerState?.let { vs ->
                            vs.zoomBy(TOOLBAR_ZOOM_STEP_OUT, Offset(vs.viewportSize.width / 2f, vs.viewportSize.height / 2f))
                        }
                    },
                    showThumbnails = showThumbnails,
                    onToggleThumbnails = { controls?.toggleThumbnails?.invoke() },
                    showPencilModeButton = SupportsPencilMode,
                    pencilModeEnabled = pencilModeEnabled,
                    onPencilModeChange = onPencilModeChange,
                    magnifierEnabled = magnifierEnabled,
                    onMagnifierToggle = { controls?.toggleMagnifier?.invoke() },
                    onOpenShortcutsSettings = { showShortcutsDialog = true },
                    onRailWidthChanged = { landscapeToolbarWidthDp = it },
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 8.dp),
            ) {
                AnimatedVisibility(
                    visible = totalPages > 0,
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut(),
                ) {
                    PageIndicatorAirbar(
                        currentPage = currentPage,
                        totalPages = totalPages,
                        onNavigateToPage = { controls?.navigateToPage?.invoke(it) },
                        modifier = Modifier.onSizeChanged {
                            landscapePageCounterHeightDp = with(density) { it.height.toDp() }
                        },
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth(),
            ) {
                PortraitTopBar(
                    currentPage = currentPage,
                    totalPages = totalPages,
                    onNavigateToPage = { controls?.navigateToPage?.invoke(it) },
                    toolMode = toolMode,
                    onToolModeChange = { toolMode = it },
                    penSettings = penSettings,
                    onPenSettingsChange = { penSettings = it },
                    markerSettings = markerSettings,
                    onMarkerSettingsChange = {
                        if (it.strokeWidth != markerSettings.strokeWidth) markerWidthPinned = true
                        markerSettings = it
                    },
                    eraserSettings = eraserSettings,
                    onEraserSettingsChange = { eraserSettings = it },
                    hasAnnotations = hasAnnotations,
                    isExporting = isExporting,
                    onExport = { controls?.export?.invoke() },
                    scale = scale,
                    onZoomIn = {
                        tabSession.focusedActiveState?.pdfViewerState?.let { vs ->
                            vs.zoomBy(TOOLBAR_ZOOM_STEP_IN, Offset(vs.viewportSize.width / 2f, vs.viewportSize.height / 2f))
                        }
                    },
                    onZoomOut = {
                        tabSession.focusedActiveState?.pdfViewerState?.let { vs ->
                            vs.zoomBy(TOOLBAR_ZOOM_STEP_OUT, Offset(vs.viewportSize.width / 2f, vs.viewportSize.height / 2f))
                        }
                    },
                    showThumbnails = showThumbnails,
                    onToggleThumbnails = { controls?.toggleThumbnails?.invoke() },
                    showPencilModeButton = SupportsPencilMode,
                    pencilModeEnabled = pencilModeEnabled,
                    onPencilModeChange = onPencilModeChange,
                    magnifierEnabled = magnifierEnabled,
                    onMagnifierToggle = { controls?.toggleMagnifier?.invoke() },
                    onOpenShortcutsSettings = { showShortcutsDialog = true },
                    onBack = onBackOrCloseThumbnails,
                )
            }
        }
        }

        if (SupportsQuickLoupe) {
            val armed = controls?.quickLoupeArmed == true
            GlassSurface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 88.dp, end = 16.dp),
                shape = CircleShape,
                tint = if (armed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            ) {
                IconButton(onClick = { controls?.toggleQuickLoupe?.invoke() }) {
                    Icon(
                        imageVector = Icons.Default.ZoomIn,
                        contentDescription = "Быстрая лупа: выделить область",
                        tint = if (armed) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars),
        )

        if (showShortcutsDialog) {
            ShortcutsSettingsDialog(
                settings = shortcutsSettings,
                onChange = { shortcutsSettingsState.value = it },
                onDismiss = { showShortcutsDialog = false },
                penButtons = tabletController.penButtons,
            )
        }

        pendingPanelMove?.let { (fromPanelId, tabId) ->
            LayoutPickerOverlay(
                templates = tabSession.availableTemplatesForAdd(),
                onPick = { template: LayoutTemplate ->
                    tabSession.moveTabToNewPanel(template, fromPanelId, tabId)
                    pendingPanelMove = null
                },
                onDismiss = { pendingPanelMove = null },
            )
        }
    }
}
