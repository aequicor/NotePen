@file:OptIn(FlowPreview::class)

package ru.kyamshanov.notepen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.annotation.domain.model.BuiltinToolPresets
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.model.EraserSettings
import ru.kyamshanov.notepen.annotation.domain.model.MarkerSettings
import ru.kyamshanov.notepen.annotation.domain.model.PenSettings
import ru.kyamshanov.notepen.annotation.domain.model.StoredToolPresets
import ru.kyamshanov.notepen.annotation.domain.model.applyStrokeWidth
import ru.kyamshanov.notepen.blur.GlassBackdropProvider
import ru.kyamshanov.notepen.blur.GlassSurface
import ru.kyamshanov.notepen.book.DocumentOutlineProvider
import ru.kyamshanov.notepen.pdf.domain.port.PdfDocumentLoader
import ru.kyamshanov.notepen.pdf.domain.port.PdfPageRenderer
import ru.kyamshanov.notepen.pdfviewer.ScrollMode
import ru.kyamshanov.notepen.qrconnect.ClientPairingPanel
import ru.kyamshanov.notepen.qrconnect.ClientQrScanViewModel
import ru.kyamshanov.notepen.qrconnect.HostDiscoveryViewModel
import ru.kyamshanov.notepen.qrconnect.HostQrPairingPanel
import ru.kyamshanov.notepen.qrconnect.HostQrPairingViewModel
import ru.kyamshanov.notepen.qrconnect.ManualConnectViewModel
import ru.kyamshanov.notepen.reflow.api.StoredReaderSettings
import ru.kyamshanov.notepen.reflow.ui.toRenderSettings
import ru.kyamshanov.notepen.session.SessionData
import ru.kyamshanov.notepen.session.captureSession
import ru.kyamshanov.notepen.session.createSessionRepository
import ru.kyamshanov.notepen.session.restoreSession
import ru.kyamshanov.notepen.shortcuts.ShortcutsSettingsDialog
import ru.kyamshanov.notepen.shortcuts.rememberShortcutsSettings
import ru.kyamshanov.notepen.sync.domain.SyncEngine
import ru.kyamshanov.notepen.sync.domain.documentIdFromFilePath
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.PairingState
import ru.kyamshanov.notepen.sync.domain.model.ServerLifecycleState
import ru.kyamshanov.notepen.sync.domain.model.StrokeDelta
import ru.kyamshanov.notepen.sync.domain.port.PeerServer
import ru.kyamshanov.notepen.sync.domain.port.SyncClient
import ru.kyamshanov.notepen.tablet.LocalTabletInputController
import ru.kyamshanov.notepen.tabs.DIVIDER_HIT
import ru.kyamshanov.notepen.tabs.DocumentId
import ru.kyamshanov.notepen.tabs.GridContainer
import ru.kyamshanov.notepen.tabs.LayoutPickerOverlay
import ru.kyamshanov.notepen.tabs.LayoutTemplate
import ru.kyamshanov.notepen.tabs.PanelId
import ru.kyamshanov.notepen.tabs.PdfDocumentState
import ru.kyamshanov.notepen.tabs.TAB_BAR_HEIGHT
import ru.kyamshanov.notepen.tabs.WorkspaceLayout
import ru.kyamshanov.notepen.tabs.WorkspaceSnapshot
import ru.kyamshanov.notepen.tabs.rememberTabSession
import ru.kyamshanov.notepen.tabs.toSnapshot
import kotlin.math.roundToInt

private val logger = KotlinLogging.logger {}

internal const val BACK_CONTENT_DESCRIPTION = "Назад"

private const val TOOLBAR_ZOOM_STEP_IN = 1.1f
private const val TOOLBAR_ZOOM_STEP_OUT = 1f / 1.1f
private const val THUMBNAIL_SIDEBAR_ANIM_MS = 300

/** Прозрачность затемнения позади модальной боковой шторки (портретный режим). */
private const val PORTRAIT_SCRIM_ALPHA = 0.4f

/** Debounce for the crash-survival session autosave; collapses a scroll storm to one write. */
private const val SESSION_AUTOSAVE_DEBOUNCE_MS = 750L

/** Tool state snapshot saved per document (keyed by file path) when it loses focus. */
private data class ToolStateSnapshot(
    val toolMode: ToolMode,
    val penSettings: PenSettings,
    val markerSettings: MarkerSettings,
    val eraserSettings: EraserSettings,
    val markerWidthPinned: Boolean,
)

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
    outlineProvider: DocumentOutlineProvider,
    syncEngineFor: ((documentId: String) -> SyncEngine)? = null,
    peerServer: PeerServer? = null,
    peerClient: SyncClient? = null,
    pendingDeltaCounts: kotlinx.coroutines.flow.Flow<Map<String, Int>>? = null,
    hostQrViewModel: HostQrPairingViewModel? = null,
    clientScanViewModel: ClientQrScanViewModel? = null,
    manualConnectViewModel: ManualConnectViewModel? = null,
    hostDiscoveryViewModel: HostDiscoveryViewModel? = null,
    receivedPdfDir: String? = null,
    openDocumentRegistry: ru.kyamshanov.notepen.sync.domain.port.OpenDocumentRegistry? = null,
    liveSyncController: ru.kyamshanov.notepen.sync.domain.LiveDocumentSyncController? = null,
    localDocumentIdRegistry: ru.kyamshanov.notepen.sync.domain.port.LocalDocumentIdRegistry? = null,
    documentIdentityProvider: ru.kyamshanov.notepen.document.domain.port.DocumentIdentityProvider? = null,
    hostAnnotationSnapshotFor: (suspend (documentId: String) -> List<StrokeDelta.Added>)? = null,
    openDocumentsSink: ((List<ru.kyamshanov.notepen.sync.domain.model.OpenDocumentInfo>) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // Размер окна редактора берём из достоверного для платформы источника
    // ([currentWindowSizePx]): на Android — из активной Configuration, которая
    // обновляется при каждом повороте даже когда Activity сама обрабатывает смену
    // конфигурации (configChanges) и не пересоздаётся. Раньше использовался
    // LocalWindowInfo.containerSize, но на части OEM-прошивок (Huawei/EMUI) он
    // «отставал» на один поворот: рельса/верхний бар показывали прошлую ориентацию.
    val windowSizeInPx = currentWindowSizePx()
    val isLandscape = windowSizeInPx.width > windowSizeInPx.height
    val density = LocalDensity.current
    val model by component.model.subscribeAsState()
    val initialFilePath = remember(model.title) { model.title }

    // Resolves the sync wire id for a local file path. Order of preference:
    //   1. remote-cached files → the host's id from the registry (must match the
    //      host exactly; tablet never recomputes for these);
    //   2. the content-addressed id warmed by [documentIdentityProvider] (the new
    //      `<basename>#<sha256-prefix>` form — what peers also compute);
    //   3. the legacy path-FNV-1a id, used only in the brief cold window before
    //      warming completes (decision 3: a re-open re-syncs via last-writer-wins).
    // Synchronous on purpose — tab/document factories run outside a coroutine; the
    // warming LaunchedEffect below populates the provider cache so (2) hits.
    val syncDocumentIdFor: (String) -> String =
        remember(localDocumentIdRegistry, receivedPdfDir, documentIdentityProvider) {
            { path ->
                val fromRegistry =
                    if (receivedPdfDir != null && path.startsWith(receivedPdfDir)) {
                        localDocumentIdRegistry?.lookup(path)
                    } else {
                        null
                    }
                fromRegistry
                    ?: documentIdentityProvider?.cachedWireIdForPath(path)
                    ?: documentIdFromFilePath(path)
            }
        }

    // Declared here (ahead of the layout-restore effect) so that effect can consume
    // a pending restore stashed by the library's "Сессии" menu before applying the
    // in-process split. The autosave/SessionsMenu wiring below reuse the same instance.
    val sessionRepository = remember { createSessionRepository() }

    var savedLayout by rememberSaveable { mutableStateOf("") }
    val tabSession =
        rememberTabSession(
            initialFilePath = initialFilePath,
            syncDocumentIdFor = syncDocumentIdFor,
        )
    val layout = tabSession.layout

    // Restore the full workspace split (all panels + tabs) saved before the
    // editor was torn down — e.g. while the user picked a file in the library.
    // Then keep the snapshot in sync with every layout change.
    // Gates the pending-tab open below: restore() rebuilds the registry from
    // scratch, so a tab added before it lands would be wiped. The suspend call
    // to consumePendingRestore makes that ordering race real, hence the flag.
    var workspaceRestored by remember(tabSession) { mutableStateOf(false) }
    LaunchedEffect(tabSession) {
        // A pending restore is set only by the library's "Сессии" menu (explicit
        // user action). When present it wins over the in-process split and brings
        // the whole session back, including per-tab view positions; a normal open
        // has nothing pending and behaves exactly as before.
        val pending = sessionRepository.consumePendingRestore()
        if (pending != null) {
            tabSession.restoreSession(pending)
            savedLayout = WorkspaceSnapshot.encode(tabSession.layout.toSnapshot())
        } else {
            WorkspaceSnapshot.decode(savedLayout)?.let { tabSession.restore(it) }
        }
        workspaceRestored = true
        snapshotFlow { tabSession.layout }
            .collect { l -> savedLayout = WorkspaceSnapshot.encode(l.toSnapshot()) }
    }

    // Library "+" opens a file into the focused panel as a new tab — only after
    // the workspace split above has been restored, so it layers on top instead
    // of being clobbered by the restore.
    val pendingTabUri by component.pendingTabUri.subscribeAsState()
    LaunchedEffect(pendingTabUri, workspaceRestored) {
        if (!workspaceRestored) return@LaunchedEffect
        if (pendingTabUri.isBlank()) return@LaunchedEffect
        tabSession.openTab(
            panelId = tabSession.layout.focusedPanelId,
            filePath = pendingTabUri,
            displayName = resolveDocumentDisplayName(pendingTabUri),
        )
        component.onPendingTabHandled()
    }

    // Warm the content-addressed wire id for every open document so the
    // synchronous [syncDocumentIdFor] resolves to the canonical id instead of
    // the legacy fallback. Remote-cached files are skipped — their id comes from
    // the host via [localDocumentIdRegistry] and must NOT be recomputed locally.
    if (documentIdentityProvider != null) {
        LaunchedEffect(tabSession, documentIdentityProvider, receivedPdfDir) {
            snapshotFlow {
                tabSession.layout.panels
                    .flatMap { panel -> panel.tabs.tabs }
                    .map { it.filePath }
                    .toSet()
            }.collect { paths ->
                paths
                    .filterNot { receivedPdfDir != null && it.startsWith(receivedPdfDir) }
                    .forEach { path ->
                        runCatching { documentIdentityProvider.identityForPath(path) }
                            .onFailure { logger.warn { "Identity warm-up failed for $path: ${it::class.simpleName}" } }
                    }
            }
        }
    }

    // Публикуем открытые во вкладках документы, чтобы хост раздал их пирам
    // (раздел «Открыто на устройстве» в каталоге пира). Remote-кешированные файлы
    // пропускаем — их documentId принадлежит чужому хосту. Wire-id берём тем же
    // [syncDocumentIdFor], что и для синхронизации, чтобы пир попал в тот же документ.
    if (openDocumentsSink != null) {
        LaunchedEffect(tabSession, openDocumentsSink, receivedPdfDir, syncDocumentIdFor) {
            snapshotFlow {
                tabSession.layout.panels
                    .flatMap { panel -> panel.tabs.tabs }
                    .filterNot { tab -> receivedPdfDir != null && tab.filePath.startsWith(receivedPdfDir) }
                    .map { tab ->
                        ru.kyamshanov.notepen.sync.domain.model.OpenDocumentInfo(
                            documentId = syncDocumentIdFor(tab.filePath),
                            displayName = tab.displayName,
                            absolutePath = tab.filePath,
                        )
                    }.filter { it.documentId.isNotBlank() }
                    .distinctBy { it.documentId }
            }.collect { openDocumentsSink(it) }
        }
    }

    DisposableEffect(tabSession) {
        onDispose {
            tabSession.disposeAll()
            // Редактор закрыт — больше нечего «отдавать как открытое».
            openDocumentsSink?.invoke(emptyList())
        }
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

    // Set true right before a programmatic tool restore (the per-document focus
    // swap below) so the preset effect doesn't overwrite the document's saved
    // settings with a preset on the synthetic toolMode change.
    var suppressNextPresetApply by remember { mutableStateOf(false) }

    // Настройки ридера — глобальные (на все документы и панели); видимость самого
    // airbar — per-tab. Персист между запусками — через ReaderSettingsRepository (ниже).
    var readerStored by remember { mutableStateOf(StoredReaderSettings()) }
    // `true` после первой загрузки настроек с диска (см. LaunchedEffect ниже). До этого
    // фактическое значение `readerStored` неотличимо от дефолтных настроек, поэтому
    // окрашивать хром/фон под «тему ридера» рано — пользователь увидит дефолт-ридер,
    // а затем (через 50–200 мс I/O) скачок в сохранённую тему. Все ридер-зависимые
    // цвета гасим до true и переключаемся ОДНИМ скачком в сохранённую тему.
    var readerStoredLoaded by remember { mutableStateOf(false) }

    // Per-document tool state: save on lose-focus, restore on gain-focus. Keyed by
    // file path, so switching tabs (not just panels) swaps the active tool and two
    // tabs of the same file share it. A restore suppresses the preset effect below
    // so the document's saved settings aren't overwritten by a preset.
    val documentToolStates = remember { mutableStateMapOf<String, ToolStateSnapshot>() }
    LaunchedEffect(tabSession) {
        var previousFilePath = tabSession.focusedActiveState?.filePath
        snapshotFlow { tabSession.focusedActiveState?.filePath }
            .distinctUntilChanged()
            .drop(1)
            .collect { newFilePath ->
                previousFilePath?.let { prev ->
                    documentToolStates[prev] =
                        ToolStateSnapshot(
                            toolMode = toolMode,
                            penSettings = penSettings,
                            markerSettings = markerSettings,
                            eraserSettings = eraserSettings,
                            markerWidthPinned = markerWidthPinned,
                        )
                }
                previousFilePath = newFilePath
                val restored = newFilePath?.let { documentToolStates[it] }
                if (restored != null) {
                    if (restored.toolMode != toolMode) suppressNextPresetApply = true
                    toolMode = restored.toolMode
                    penSettings = restored.penSettings
                    markerSettings = restored.markerSettings
                    eraserSettings = restored.eraserSettings
                    markerWidthPinned = restored.markerWidthPinned
                }
            }
    }

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

    // The workspace as it was when this editor session began — including an
    // autosave that survived a crash. Loaded once, before the autosave below
    // starts overwriting it, so "restore last" recovers the pre-session state
    // rather than the workspace just reopened.
    val lastSession by produceState<SessionData?>(initialValue = null, sessionRepository) {
        value = sessionRepository.loadAutosave()
    }

    // Crash-survival autosave: mirror the live workspace (layout + per-tab view
    // positions) to disk, debounced so a scroll storm collapses to a single write.
    // Never auto-loaded on launch — recovered only on explicit user action.
    LaunchedEffect(tabSession) {
        snapshotFlow { tabSession.captureSession() }
            .distinctUntilChanged()
            .debounce(SESSION_AUTOSAVE_DEBOUNCE_MS)
            .collect { sessionRepository.saveAutosave(it) }
    }
    val pdfExporter = remember { createPdfExporter() }
    val reflowExtractor = remember { createPdfReflowExtractor() }

    // Global tool presets, persisted across documents.
    val toolPresetsRepository = remember { createToolPresetsRepository() }
    var toolPresets by remember { mutableStateOf(StoredToolPresets()) }
    LaunchedEffect(Unit) { toolPresets = toolPresetsRepository.load() }
    val onToolPresetsChange: (StoredToolPresets) -> Unit = { updated ->
        toolPresets = updated
        coroutineScope.launch { toolPresetsRepository.save(updated) }
    }

    // Reader settings + user presets, persisted across documents (mirrors tool presets).
    val readerSettingsRepository = remember { createReaderSettingsRepository() }
    LaunchedEffect(Unit) {
        readerStored = readerSettingsRepository.load()
        readerStoredLoaded = true
    }
    val onReaderStoredChange: (StoredReaderSettings) -> Unit = { updated ->
        readerStored = updated
        coroutineScope.launch { readerSettingsRepository.save(updated) }
    }

    // Glass blur is expensive: on a low-end device or low battery, recommend (don't force)
    // turning it off. Shown at most once per session via the editor snackbar.
    val blurAdvice = rememberBlurAdvice()
    var blurRecommendationShown by remember { mutableStateOf(false) }
    LaunchedEffect(blurAdvice.shouldRecommendDisablingBlur, readerStored.blurEnabled) {
        if (readerStored.blurEnabled && blurAdvice.shouldRecommendDisablingBlur && !blurRecommendationShown) {
            blurRecommendationShown = true
            val result =
                snackbarHostState.showSnackbar(
                    message = "Размытие панелей может замедлять работу на этом устройстве",
                    actionLabel = "Отключить",
                    duration = SnackbarDuration.Long,
                )
            if (result == SnackbarResult.ActionPerformed) {
                onReaderStoredChange(readerStored.copy(blurEnabled = false))
            }
        }
    }

    // Last preset explicitly selected per tool within this document session.
    // Null means "not yet chosen" — the first activation falls back to the first builtin.
    var lastPenPresetId by remember { mutableStateOf<String?>(null) }
    var lastMarkerPresetId by remember { mutableStateOf<String?>(null) }
    var lastEraserPresetId by remember { mutableStateOf<String?>(null) }

    val onPresetApplied: (String) -> Unit = { id ->
        when (toolMode) {
            ToolMode.PEN -> lastPenPresetId = id
            ToolMode.MARKER -> lastMarkerPresetId = id
            ToolMode.ERASER -> lastEraserPresetId = id
            ToolMode.NONE -> Unit
        }
    }

    // When a tool becomes active, restore the last chosen preset for it (or the first
    // builtin preset if none has been chosen yet in this session).
    LaunchedEffect(toolMode) {
        // A per-document restore set the tool programmatically — keep the document's
        // saved settings instead of snapping to the tool's last preset.
        if (suppressNextPresetApply) {
            suppressNextPresetApply = false
            return@LaunchedEffect
        }
        when (toolMode) {
            ToolMode.PEN -> {
                val all = BuiltinToolPresets.pen + toolPresets.pen
                val preset = lastPenPresetId?.let { id -> all.firstOrNull { it.id == id } } ?: all.firstOrNull()
                preset?.let { penSettings = it.settings }
            }
            ToolMode.MARKER -> {
                val all = BuiltinToolPresets.marker + toolPresets.marker
                val preset = lastMarkerPresetId?.let { id -> all.firstOrNull { it.id == id } } ?: all.firstOrNull()
                preset?.let { markerSettings = it.settings }
            }
            ToolMode.ERASER -> {
                val all = BuiltinToolPresets.eraser + toolPresets.eraser
                val preset = lastEraserPresetId?.let { id -> all.firstOrNull { it.id == id } } ?: all.firstOrNull()
                preset?.let { eraserSettings = it.settings }
            }
            ToolMode.NONE -> Unit
        }
    }

    // Re-keyed on [isLandscape] so a rotation resets each measured inset to its seed
    // instead of carrying the previous orientation's value: the landscape dims are
    // written only by the landscape branch's onRailWidthChanged/onSizeChanged and the
    // portrait dim only by the portrait branch, so without the key they go stale the
    // moment the orientation flips (the other branch never re-measures them). Stale
    // insets pushed reading-mode text and fit-to-width pages under the wrong-orientation
    // chrome until a background/relaunch reseeded them.
    var landscapeToolbarWidthDp by remember(isLandscape) { mutableStateOf(FLOATING_TOOLBAR_WIDTH) }
    var landscapePageCounterHeightDp by remember(isLandscape) { mutableStateOf(TAB_BAR_HEIGHT) }
    // Measured height of the portrait top bar (status-bar inset + toolbar row). Reused as the
    // reading-mode top inset so the reader text clears the floating bar (Defect C). Seeded with
    // TAB_BAR_HEIGHT so the reserve is sane before the first measure.
    var portraitTopBarHeightDp by remember(isLandscape) { mutableStateOf(TAB_BAR_HEIGHT) }

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
    val showToc = controls?.showToc ?: false
    val hasToc = controls?.hasToc ?: false
    val readingModeEnabled = controls?.readingModeEnabled ?: false
    // Per-document live-sync toggle (M4) — distinct from the QR pairing entry below.
    val liveSyncAvailable = controls?.liveSyncAvailable ?: false
    val liveSyncEnabled = controls?.liveSyncEnabled ?: false
    // F-6: при включении режима чтения возвращаем фокус на корневой Box, чтобы
    // стрелки и Space начинали листать сразу. До этого фокуса требовался первый
    // тап по полотну (см. Initial-pass focusRequester ниже) — а в чистом mouse-only
    // сценарии после переключения в режим чтения первый Right часто не доходил.
    LaunchedEffect(readingModeEnabled) {
        if (readingModeEnabled) focusRequester.requestFocus()
    }
    val readingModeAvailable = controls?.readingModeAvailable ?: true
    // В режиме чтения тап по тексту прячет инструменты и быстрые действия вместе с
    // airbar ридера; повторный тап — возвращает. Берём состояние НАПРЯМУЮ из
    // персистентного per-tab состояния сфокусированной панели, а не из
    // ретранслируемого PanelControls.chromeHidden: при возврате с другой панели в
    // split view focusedActiveState меняется синхронно с фокусом, поэтому хром не
    // «моргает» видимым на кадр перехода и не залипает на чужом (устаревшем)
    // значении, пока сфокусированная панель не переопубликует свой SideEffect.
    val focusedReaderState = tabSession.focusedActiveState
    val chromeHidden =
        focusedReaderState != null &&
            focusedReaderState.readingMode &&
            !focusedReaderState.readerBarVisible
    // Цвета активной темы ридера: не null только в режиме чтения. Хром (рельса,
    // airbar, вкладки) перекрашивается под них; null — сохраняем цвета темы.
    //
    // Берём НАПРЯМУЮ из (1) per-tab readingMode сфокусированной панели и (2) глобального
    // `readerStored` — без посредничества `PanelControls`. Раньше эти два поля гонялись
    // через канал per-panel SideEffect-publish: при включении режима чтения хром (аирбары,
    // рельса, top-bar) обновлялся не одновременно с фоном, а через цепочку
    // recomposeEditorPanel → SideEffect → focusedControls := new → recomposeDetailsContent,
    // что под нагрузкой (например, тяжёлый reflow.extract на старте) растягивалось до
    // появления текста — выглядело как «мерцание» (default → reader). Поскольку и фон, и
    // хром теперь читают один и тот же синхронный источник, переход — одним кадром.
    val readerRenderForChrome =
        if (focusedReaderState?.readingMode == true && readerStoredLoaded) {
            readerStored.current.toRenderSettings()
        } else {
            null
        }
    val readerBackground = readerRenderForChrome?.background
    val readerContentColor = readerRenderForChrome?.textColor

    // ---- Sync availability + status tint ----------------------------------
    // Кнопка синхронизации живёт в колесе настроек (см. systemControlEntries) —
    // здесь держим её доступность, состояние диалога и цвет-индикатор связи,
    // потому что они нужны и колесу (выше по дереву), и панели/острову ниже.
    val syncPaneEnabled = hostQrViewModel != null || clientScanViewModel != null
    var showSyncPanel by remember { mutableStateOf(false) }
    val syncStatusTint =
        if (syncPaneEnabled) {
            val hostPeers =
                peerServer
                    ?.connectedPeers
                    ?.collectAsState(emptySet())
                    ?.value
                    ?: emptySet()
            val hostLifecycle =
                peerServer
                    ?.lifecycle
                    ?.collectAsState(ServerLifecycleState.Idle)
                    ?.value
            val clientHosts =
                peerClient
                    ?.connectedHosts
                    ?.collectAsState(emptySet())
                    ?.value
                    ?: emptySet()
            val clientStates =
                peerClient
                    ?.pairingStates
                    ?.collectAsState(emptyMap())
                    ?.value
                    ?: emptyMap()
            syncIndicatorTint(hostPeers, hostLifecycle, clientHosts, clientStates)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    // ---- Save helpers (across all panels) ---------------------------------
    val saveTab: suspend (PdfDocumentState) -> Unit = { state ->
        val annotations = state.drawingStates.mapValues { (_, s) -> s.currentPaths.toList() }
        val extents = state.drawingStates.mapValues { (_, s) -> s.extent.value }
        annotationRepository
            .save(
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
    // Both the tab "+" and the "Новая вкладка" menu item open the library so
    // the user can pick a document there. The new tab lands in whichever panel
    // requested it (we focus it first); the full split is preserved across the
    // library round-trip via the workspace snapshot below.
    val onAddTabToPanel: (PanelId) -> Unit = { panelId ->
        tabSession.focusPanel(panelId)
        onOpenLibrary()
    }

    EditorBackHandler(enabled = true) { onBackWithSave() }

    ImmersiveEditorMode()

    // Громкость листает ридер только в режиме чтения; перехват снимается, едва он
    // выключится, чтобы клавиши громкости в остальном работали штатно. На десктопе
    // — no-op. Стрелки/PageUp-Down/Space обрабатываются общим onKeyEvent ниже.
    ReaderVolumeKeyHandler(
        enabled = readingModeEnabled,
        onPageDelta = { delta -> controls?.readerPageDelta?.invoke(delta) },
    )

    val onPencilModeChange: (Boolean) -> Unit = { enabled ->
        pencilModeEnabled = enabled
        pencilModeManuallyTouched = true
    }
    val onBackOrCloseThumbnails: () -> Unit = {
        when {
            controls?.showThumbnails == true -> controls.toggleThumbnails()
            controls?.showToc == true -> controls.toggleToc()
            else -> onBackWithSave()
        }
    }

    // Top chrome (each panel's tab strip and the toolbar floating just below it)
    // must clear the status bar AND the display cutout: ImmersiveEditorMode hides
    // the system bars, so WindowInsets.statusBars collapses to 0 — yet the camera
    // notch / punch-hole still occupies the top edge and would otherwise overlap
    // the tab strip. In landscape the cutout moves to a side, so its top is ~0 and
    // this stays equal to the (hidden) status bar inset.
    val topChromeInset =
        WindowInsets.statusBars
            .union(WindowInsets.displayCutout)
            .asPaddingValues()
            .calculateTopPadding()

    // Measured height of the workspace grid in px. Panel ratios are relative to
    // this — using it (rather than deriving from window size minus the status
    // bar) keeps the airbar / sidebar vertical placement exact across platforms.
    var gridHeightPx by remember { mutableStateOf(0f) }

    // Horizontal area occupied by the floating tool rail at the screen's left in
    // landscape (single-panel FULL layout). Double-tap fit-to-width subtracts it
    // so the page lands beside the rail/menu, not under it. The portrait top bar
    // spans the full width and steals no horizontal room, so the inset is 0 there.
    val pdfFitWidthStartInset =
        if (isLandscape && layout.template == LayoutTemplate.FULL) {
            val sidebar =
                when {
                    showThumbnails && tabSession.focusedActiveState?.pages?.isNotEmpty() == true -> SIDEBAR_WIDTH
                    // ToC-сайдбар (шире миниатюр) тоже сдвигает контент при fit-to-width.
                    showToc -> TOC_SIDEBAR_WIDTH
                    else -> 0.dp
                }
            sidebar + landscapeToolbarWidthDp + 32.dp
        } else {
            0.dp
        }

    // Vertical room taken by the page-counter airbar floating at the focused
    // panel's top-centre in landscape. The airbar sits 8.dp below the viewer's
    // top edge; double-tap fit-to-width pushes the page below it (plus a 16.dp
    // gap) so the page top doesn't slide under the counter.
    val pdfFitWidthTopInset =
        if (isLandscape && layout.template == LayoutTemplate.FULL) {
            8.dp + landscapePageCounterHeightDp + 16.dp
        } else {
            0.dp
        }

    // Defect C — reading-mode chrome reserve. The reflow reader, unlike the PDF
    // page path, reserved no room for the floating chrome, so the H1/first lines
    // hid under the top bar / «Страница N/M» chip and (on tablets) the left tool
    // rail clipped the first chars of every line. We reserve a STATIC inset (it
    // must NOT animate with chrome visibility — a changing reader viewport would
    // force re-pagination on every tap). In reading mode the focused panel shows
    // the reader, not the PDF viewer, so reusing the same EditorPanel inset params
    // doesn't regress fit-to-width.
    //   • Landscape uses LandscapeToolRail for ALL landscape (not only FULL), so we
    //     reserve the rail strip (its 16.dp start pad + measured width + 16.dp gap to
    //     clear the first glyphs) regardless of the strict width>height FULL gate.
    //   • Portrait uses the full-width PortraitTopBar — no horizontal room, top inset
    //     = its measured height (status-bar inset + toolbar row).
    val readingStartInset =
        if (isLandscape) {
            16.dp + landscapeToolbarWidthDp + 16.dp
        } else {
            0.dp
        }
    val readingTopInset =
        if (isLandscape) {
            8.dp + landscapePageCounterHeightDp + 16.dp
        } else {
            portraitTopBarHeightDp
        }

    // In reading mode the chrome insets clear the reader; otherwise keep the exact
    // PDF fit-to-width behaviour. Both feed the same EditorPanel params (#256-257),
    // which route them to the reader or PDF viewer depending on the active mode.
    val fitWidthStartInset = if (readingModeEnabled) readingStartInset else pdfFitWidthStartInset
    val fitWidthTopInset = if (readingModeEnabled) readingTopInset else pdfFitWidthTopInset

    // Под скрытым хромом (режим чтения + airbar спрятан тапом) область над панелью —
    // status-bar inset + резерв под TAB_BAR_HEIGHT — заполняется ИМЕННО этим фоном.
    // Берём `readerBackground`, который вычислен выше из того же per-tab+global источника,
    // что и цвета хрома — гарантия, что фон и аирбары переключаются одним кадром, без
    // визуального лага «фон уже сменился, аирбары ещё нет».
    val rootBackground = readerBackground ?: MaterialTheme.colorScheme.background
    GlassBackdropProvider(blurEnabled = readerStored.blurEnabled) {
        Box(
            modifier
                .fillMaxSize()
                .background(rootBackground)
                // Landscape side notch / punch-hole: inset the whole editor from the
                // horizontal display cutout (and any side system bar) so the tab-strip
                // edges — and the content below them — never slide under it. Consumed
                // here, so the tool rail's own systemBars∪displayCutout padding doesn't
                // double-apply. No-op in portrait, on desktop, and on cutout-less screens.
                .windowInsetsPadding(
                    WindowInsets.systemBars
                        .union(WindowInsets.displayCutout)
                        .only(WindowInsetsSides.Horizontal),
                ).focusRequester(focusRequester)
                .focusTarget()
                // В режиме чтения тап по полотну (скрытие панели, перелистывание по
                // тап-зонам) очищает фокус с этого focusTarget — и хардварное
                // перелистывание стрелками перестаёт работать. Перехватываем нажатие
                // в Initial-проходе (родитель → дочерний, до потребления ридером) и
                // возвращаем фокус сюда. Только в reading mode, чтобы не мешать
                // фокусировке текстовых полей в обычном редакторе.
                .then(
                    if (readingModeEnabled) {
                        Modifier.pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                focusRequester.requestFocus()
                            }
                        }
                    } else {
                        Modifier
                    },
                ).onKeyEvent { e ->
                    val isShift = e.key == Key.ShiftLeft || e.key == Key.ShiftRight
                    val isCtrl = e.key == Key.CtrlLeft || e.key == Key.CtrlRight
                    val isAlt = e.key == Key.AltLeft || e.key == Key.AltRight
                    val isMeta = e.key == Key.MetaLeft || e.key == Key.MetaRight
                    val readerPageTurn =
                        if (readingModeEnabled && e.type == KeyEventType.KeyDown) {
                            readerPageTurnDelta(e.key, shiftHeld)
                        } else {
                            null
                        }
                    when {
                        isShift -> {
                            shiftHeld = e.type == KeyEventType.KeyDown
                            false
                        }
                        isCtrl -> {
                            ctrlHeld = e.type == KeyEventType.KeyDown
                            false
                        }
                        isAlt -> {
                            altHeld = e.type == KeyEventType.KeyDown
                            false
                        }
                        isMeta -> {
                            metaHeld = e.type == KeyEventType.KeyDown
                            false
                        }
                        e.type == KeyEventType.KeyDown && e.key == Key.Z && e.isCtrlPressed && !shiftHeld -> {
                            tabSession.focusedActiveState?.undo()
                            true
                        }
                        e.type == KeyEventType.KeyDown && e.key == Key.Z && e.isCtrlPressed && shiftHeld -> {
                            tabSession.focusedActiveState?.redo()
                            true
                        }
                        // Перелистывание ридера хардварными клавишами — только в режиме
                        // чтения (см. readerPageTurn выше), иначе стрелки/Space/PageUp-Down
                        // остаются свободны. Клавиши громкости тут — запасной путь (на
                        // Android их раньше съедает оконный перехват ReaderVolumeKeyHandler;
                        // на десктопе их нет).
                        readerPageTurn != null -> {
                            controls?.readerPageDelta?.invoke(readerPageTurn)
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
            // Workspace grid. Each panel draws its own tab strip at its top; the grid
            // clears the status bar / display cutout so the top-row tab strips sit below them.
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = topChromeInset)
                    .onSizeChanged { gridHeightPx = it.height.toFloat() },
            ) {
                GridContainer(
                    layout = layout,
                    onSetRatio = { index, value -> tabSession.setRatio(index, value) },
                    onFocusPanel = { tabSession.focusPanel(it) },
                ) { panel ->
                    val templates = tabSession.availableTemplatesForAdd()
                    EditorPanel(
                        panel = panel,
                        tabSession = tabSession,
                        sessionsMenu = { expanded, onDismiss ->
                            SessionsMenu(
                                expanded = expanded,
                                onDismiss = onDismiss,
                                sessionRepository = sessionRepository,
                                lastSession = lastSession,
                                onCaptureCurrent = { tabSession.captureSession() },
                                onRestore = { data ->
                                    tabSession.restoreSession(data)
                                    // Keep the in-process layout snapshot consistent with the restored split.
                                    savedLayout = WorkspaceSnapshot.encode(tabSession.layout.toSnapshot())
                                },
                            )
                        },
                        isFocused = panel.id == layout.focusedPanelId,
                        loader = loader,
                        renderer = renderer,
                        outlineProvider = outlineProvider,
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
                        reflowExtractor = reflowExtractor,
                        readerStored = readerStored,
                        readerStoredLoaded = readerStoredLoaded,
                        onReaderStoredChange = onReaderStoredChange,
                        syncEngineFor = syncEngineFor,
                        peerClient = peerClient,
                        peerServer = peerServer,
                        pendingDeltaCounts = pendingDeltaCounts,
                        receivedPdfDir = receivedPdfDir,
                        openDocumentRegistry = openDocumentRegistry,
                        liveSyncController = liveSyncController,
                        hostAnnotationSnapshotFor = hostAnnotationSnapshotFor,
                        showSnackbar = { msg -> coroutineScope.launch { snackbarHostState.showSnackbar(msg) } },
                        onRestoreToolSettings = { pen, marker, eraser ->
                            penSettings = pen
                            markerSettings = marker
                            // A restored width is the user's own choice — don't override it.
                            markerWidthPinned = true
                            eraserSettings = eraser
                            // Seed this document's tool checkpoint with its just-loaded
                            // settings (keyed by file path), so switching away and back
                            // restores them.
                            panel.tabs.activeTab?.filePath?.let { filePath ->
                                documentToolStates[filePath] =
                                    ToolStateSnapshot(
                                        toolMode = toolMode,
                                        penSettings = pen,
                                        markerSettings = marker,
                                        eraserSettings = eraser,
                                        markerWidthPinned = true,
                                    )
                            }
                        },
                        onAddTab = { onAddTabToPanel(panel.id) },
                        onAllTabsClosed = onBackWithSave,
                        onOpenPanelPicker =
                            if (templates.isNotEmpty() && panel.tabs.tabs.size > 1) {
                                { tabId ->
                                    tabSession.focusPanel(panel.id)
                                    pendingPanelMove = panel.id to tabId
                                }
                            } else {
                                null
                            },
                        onClosePanel =
                            if (layout.panels.size > 1) {
                                {
                                    val anyTab = panel.tabs.tabs.firstOrNull()
                                    if (anyTab != null) {
                                        // Close every tab in this panel → panel is removed.
                                        panel.tabs.tabs
                                            .toList()
                                            .forEach { tabSession.closeTab(panel.id, it.id) }
                                    }
                                }
                            } else {
                                null
                            },
                        onControlsChanged = { c -> if (panel.id == layout.focusedPanelId) focusedControls = c },
                        fitWidthStartInset = fitWidthStartInset,
                        fitWidthTopInset = fitWidthTopInset,
                    )
                }
            }

            // ---- Unified toolbar: overlay floating just below the tab strip ------
            Box(
                Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(WindowInsets.statusBars)
                    .padding(top = TAB_BAR_HEIGHT + topChromeInset),
            ) {
                val dividerPx = with(density) { DIVIDER_HIT.toPx() }
                if (isLandscape) {
                    // When a left-edge sidebar is open it would otherwise sit on top of the
                    // back button and tool rail; push them right by the sidebar's width so
                    // they sit beside it. Both the thumbnails and the (wider) ToC sidebar
                    // anchor to the left edge, so each one shifts the chrome by its own width.
                    val railShift by animateDpAsState(
                        targetValue =
                            when {
                                focusedPanelStartXFraction(layout) != 0f -> 0.dp
                                showThumbnails && tabSession.focusedActiveState?.pages?.isNotEmpty() == true ->
                                    SIDEBAR_WIDTH
                                showToc -> TOC_SIDEBAR_WIDTH
                                else -> 0.dp
                            },
                        animationSpec = tween(THUMBNAIL_SIDEBAR_ANIM_MS),
                        label = "railShift",
                    )
                    AnimatedVisibility(
                        visible = !chromeHidden,
                        enter = slideInHorizontally { -it } + fadeIn(),
                        exit = slideOutHorizontally { -it } + fadeOut(),
                        modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .offset(x = railShift)
                                // Те же инсеты, что и у рельсы ниже — иначе при боковом
                                // вырезе/системном баре кнопка назад не совпадает с рельсой
                                // по левому краю.
                                .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
                                .padding(start = 16.dp, top = 8.dp),
                    ) {
                        GlassSurface(
                            shape = CircleShape,
                            tint = readerBackground ?: MaterialTheme.colorScheme.surface,
                            modifier = Modifier.size(width = landscapeToolbarWidthDp, height = landscapePageCounterHeightDp),
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .clickable(onClick = onBackOrCloseThumbnails),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = BACK_CONTENT_DESCRIPTION,
                                    tint = readerContentColor ?: MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = !chromeHidden,
                        enter = slideInHorizontally { -it } + fadeIn(),
                        exit = slideOutHorizontally { -it } + fadeOut(),
                        modifier =
                            Modifier
                                // Резервируем сверху высоту back-кнопки (чтобы не перекрыть её),
                                // а ниже занимаем всю высоту и центрируем рельсу по вертикали —
                                // на десктопе она оказывается посередине, а не прижата к верху.
                                .align(Alignment.TopStart)
                                .offset(x = railShift)
                                // systemBars ∪ displayCutout: в ландшафте вырез/«бровь» уходит
                                // на боковой край, и без cutout вертикальная рельса налезала
                                // на него слева.
                                .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
                                .padding(
                                    start = 16.dp,
                                    top = 8.dp + landscapePageCounterHeightDp + 16.dp,
                                    end = 16.dp,
                                    bottom = 16.dp,
                                ).fillMaxHeight(),
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxHeight()) {
                            LandscapeToolRail(
                                tools =
                                    ToolRailTools(
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
                                        toolPresets = toolPresets,
                                        onToolPresetsChange = onToolPresetsChange,
                                        onPresetApplied = onPresetApplied,
                                    ),
                                system =
                                    ToolRailSystem(
                                        undoEnabled = tabSession.focusedActiveState?.canUndo == true,
                                        onUndo = { tabSession.focusedActiveState?.undo() },
                                        redoEnabled = tabSession.focusedActiveState?.canRedo == true,
                                        onRedo = { tabSession.focusedActiveState?.redo() },
                                        hasAnnotations = hasAnnotations,
                                        isExporting = isExporting,
                                        onExport = { controls?.export?.invoke() },
                                        scale = scale,
                                        onZoomIn = {
                                            tabSession.focusedActiveState?.pdfViewerState?.let { vs ->
                                                vs.zoomBy(
                                                    TOOLBAR_ZOOM_STEP_IN,
                                                    Offset(
                                                        vs.viewportSize.width / 2f,
                                                        vs.viewportSize.height / 2f,
                                                    ),
                                                )
                                            }
                                        },
                                        onZoomOut = {
                                            tabSession.focusedActiveState?.pdfViewerState?.let { vs ->
                                                vs.zoomBy(
                                                    TOOLBAR_ZOOM_STEP_OUT,
                                                    Offset(vs.viewportSize.width / 2f, vs.viewportSize.height / 2f),
                                                )
                                            }
                                        },
                                        showThumbnails = showThumbnails,
                                        onToggleThumbnails = { controls?.toggleThumbnails?.invoke() },
                                        showTocButton = hasToc,
                                        showToc = showToc,
                                        onToggleToc = { controls?.toggleToc?.invoke() },
                                        readingModeEnabled = readingModeEnabled,
                                        readingModeAvailable = readingModeAvailable,
                                        onToggleReadingMode = { controls?.toggleReadingMode?.invoke() },
                                        showPencilModeButton = SupportsPencilMode,
                                        pencilModeEnabled = pencilModeEnabled,
                                        onPencilModeChange = onPencilModeChange,
                                        magnifierEnabled = magnifierEnabled,
                                        onMagnifierToggle = { controls?.toggleMagnifier?.invoke() },
                                        showSyncButton = syncPaneEnabled,
                                        syncTint = syncStatusTint,
                                        onOpenSync = { showSyncPanel = true },
                                        liveSyncAvailable = liveSyncAvailable,
                                        liveSyncEnabled = liveSyncEnabled,
                                        onToggleLiveSync = { controls?.toggleLiveSync?.invoke() },
                                        onOpenShortcutsSettings = { showShortcutsDialog = true },
                                        onRotatePage = { controls?.rotateCurrentPage?.invoke() },
                                        spreadSplitEnabled = controls?.spreadSplitEnabled == true,
                                        onToggleSpreadSplit = { controls?.toggleSpreadSplit?.invoke() },
                                        bookSpreadEnabled = controls?.bookSpreadEnabled == true,
                                        onToggleBookSpread = { controls?.toggleBookSpread?.invoke() },
                                    ),
                                readerTheme =
                                    ToolRailReaderTheme(
                                        background = readerBackground,
                                        contentColor = readerContentColor,
                                    ),
                                onRailWidthChanged = { landscapeToolbarWidthDp = it },
                            )
                        }
                    }

                    // ---- Page-indicator airbar: slides to the centre of the focused panel ----
                    val airbarCenterFraction by animateFloatAsState(
                        targetValue = focusedPanelCenterXFraction(layout),
                        animationSpec =
                            spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        label = "airbarCenter",
                    )
                    val airbarTopPx by animateFloatAsState(
                        targetValue = focusedPanelStartYPx(layout, gridHeightPx, dividerPx),
                        animationSpec =
                            spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        label = "airbarTop",
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier =
                            Modifier
                                .align(Alignment.TopCenter)
                                .graphicsLayer {
                                    translationX =
                                        (airbarCenterFraction - 0.5f) * windowSizeInPx.width.toFloat()
                                    // The grid's top sits exactly one tab-strip below this airbar's
                                    // natural position, so translating by the focused panel's top
                                    // offset (relative to the grid) lands it on that panel.
                                    translationY = airbarTopPx
                                }.windowInsetsPadding(WindowInsets.statusBars)
                                .padding(top = 8.dp),
                    ) {
                        AnimatedVisibility(
                            visible = totalPages > 0 && !chromeHidden,
                            enter = slideInVertically { -it } + fadeIn(),
                            exit = slideOutVertically { -it } + fadeOut(),
                        ) {
                            PageIndicatorAirbar(
                                currentPage = currentPage,
                                totalPages = totalPages,
                                onNavigateToPage = { controls?.navigateToPage?.invoke(it) },
                                containerColor = readerBackground,
                                contentColor = readerContentColor,
                                modifier =
                                    Modifier.onSizeChanged {
                                        landscapePageCounterHeightDp = with(density) { it.height.toDp() }
                                    },
                            )
                        }
                    }
                } else {
                    Column(
                        modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .fillMaxWidth(),
                    ) {
                        AnimatedVisibility(
                            visible = !chromeHidden,
                            enter = slideInVertically { -it } + fadeIn(),
                            exit = slideOutVertically { -it } + fadeOut(),
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
                                toolPresets = toolPresets,
                                onToolPresetsChange = onToolPresetsChange,
                                onPresetApplied = onPresetApplied,
                                undoEnabled = tabSession.focusedActiveState?.canUndo == true,
                                onUndo = { tabSession.focusedActiveState?.undo() },
                                redoEnabled = tabSession.focusedActiveState?.canRedo == true,
                                onRedo = { tabSession.focusedActiveState?.redo() },
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
                                showTocButton = hasToc,
                                showToc = showToc,
                                onToggleToc = { controls?.toggleToc?.invoke() },
                                readingModeEnabled = readingModeEnabled,
                                readingModeAvailable = readingModeAvailable,
                                onToggleReadingMode = { controls?.toggleReadingMode?.invoke() },
                                showPencilModeButton = SupportsPencilMode,
                                pencilModeEnabled = pencilModeEnabled,
                                onPencilModeChange = onPencilModeChange,
                                magnifierEnabled = magnifierEnabled,
                                onMagnifierToggle = { controls?.toggleMagnifier?.invoke() },
                                showSyncButton = syncPaneEnabled,
                                syncTint = syncStatusTint,
                                onOpenSync = { showSyncPanel = true },
                                liveSyncAvailable = liveSyncAvailable,
                                liveSyncEnabled = liveSyncEnabled,
                                onToggleLiveSync = { controls?.toggleLiveSync?.invoke() },
                                onOpenShortcutsSettings = { showShortcutsDialog = true },
                                onRotatePage = { controls?.rotateCurrentPage?.invoke() },
                                spreadSplitEnabled = controls?.spreadSplitEnabled == true,
                                onToggleSpreadSplit = { controls?.toggleSpreadSplit?.invoke() },
                                bookSpreadEnabled = controls?.bookSpreadEnabled == true,
                                onToggleBookSpread = { controls?.toggleBookSpread?.invoke() },
                                onBack = onBackOrCloseThumbnails,
                                readerBackground = readerBackground,
                                readerContentColor = readerContentColor,
                                // Меряем фактическую высоту бара (вместе с status-bar инсетом) —
                                // используется как верхний резерв ридера (Defect C).
                                modifier =
                                    Modifier.onSizeChanged {
                                        portraitTopBarHeightDp = with(density) { it.height.toDp() }
                                    },
                            )
                        }
                    }
                }

                // Меню страниц/оглавления поверх контента. В портрете — модальная боковая
                // шторка слева с затемнением (тап по фону закрывает); в ландшафте рельса
                // отъезжает вправо (railShift выше), затемнения нет.
                val sideMenuState = tabSession.focusedActiveState
                if (sideMenuState != null) {
                    FocusedPanelMenus(
                        focusedState = sideMenuState,
                        renderer = renderer,
                        controls = controls,
                        showThumbnails = showThumbnails,
                        showToc = showToc,
                        scrim = !isLandscape,
                        offsetXPx = focusedPanelStartXPx(layout, windowSizeInPx.width.toFloat(), dividerPx),
                        offsetYPx = focusedPanelStartYPx(layout, gridHeightPx, dividerPx),
                        heightPx =
                            focusedPanelHeightPx(layout, gridHeightPx, dividerPx) -
                                with(density) { TAB_BAR_HEIGHT.toPx() },
                        onClose = onBackOrCloseThumbnails,
                    )
                }
            }

            // Floating airbar (bottom-right): the quick loupe (touch only) and the
            // scroll-mode toggle stacked into one glass island. The sync entry now
            // lives in the settings wheel (see systemControlEntries); its panel and
            // status tint are owned higher up so the wheel can reach them too.
            // В режиме чтения прокрутка только вертикальная (reflow-поток), поэтому
            // переключатель режима скролла бессмысленен — прячем его.
            val showScrollModeButton = controls != null && !readingModeEnabled
            val airbarButtonCount =
                (if (SupportsQuickLoupe) 1 else 0) +
                    (if (showScrollModeButton) 1 else 0)
            if (airbarButtonCount > 0 && !chromeHidden) {
                val inactiveTint = readerContentColor ?: MaterialTheme.colorScheme.onSurface
                val activeTint = readerContentColor ?: MaterialTheme.colorScheme.primary
                GlassSurface(
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(bottom = 88.dp, end = 16.dp),
                    shape =
                        if (airbarButtonCount > 1) {
                            RoundedCornerShape(28.dp)
                        } else {
                            CircleShape
                        },
                    tint = readerBackground ?: MaterialTheme.colorScheme.surface,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (SupportsQuickLoupe) {
                            val armed = controls?.quickLoupeArmed == true
                            IconButton(onClick = { controls?.toggleQuickLoupe?.invoke() }) {
                                Icon(
                                    imageVector = Icons.Default.ZoomIn,
                                    contentDescription = "Быстрая лупа: выделить область",
                                    tint = if (armed) activeTint else inactiveTint,
                                )
                            }
                        }
                        if (showScrollModeButton) {
                            val mode = controls.scrollMode
                            IconButton(onClick = { controls.cycleScrollMode() }) {
                                Icon(
                                    imageVector =
                                        when (mode) {
                                            ScrollMode.BOTH -> Icons.Default.OpenWith
                                            ScrollMode.VERTICAL -> Icons.Default.SwapVert
                                            ScrollMode.NONE -> Icons.Default.Block
                                        },
                                    contentDescription =
                                        when (mode) {
                                            ScrollMode.BOTH -> "Скролл: по обеим осям"
                                            ScrollMode.VERTICAL -> "Скролл: только по вертикали"
                                            ScrollMode.NONE -> "Скролл выключен"
                                        },
                                    tint = if (mode == ScrollMode.BOTH) inactiveTint else activeTint,
                                )
                            }
                        }
                    }
                }
            }

            if (showSyncPanel && syncPaneEnabled) {
                Dialog(onDismissRequest = { showSyncPanel = false }) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        tonalElevation = 6.dp,
                        modifier =
                            Modifier
                                .widthIn(min = 320.dp, max = 480.dp)
                                .heightIn(min = 200.dp, max = 720.dp),
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                        ) {
                            if (hostQrViewModel != null) {
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp, end = 8.dp, top = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "QR-подключение",
                                        style = MaterialTheme.typography.titleLarge,
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(onClick = { showSyncPanel = false }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Закрыть",
                                        )
                                    }
                                }
                                HostQrPairingPanel(
                                    viewModel = hostQrViewModel,
                                    onCloseDialog = { showSyncPanel = false },
                                )
                            }
                            if (clientScanViewModel != null && manualConnectViewModel != null && peerClient != null) {
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp, end = 8.dp, top = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "Подключение",
                                        style = MaterialTheme.typography.titleLarge,
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(onClick = { showSyncPanel = false }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Закрыть",
                                        )
                                    }
                                }
                                ClientPairingPanel(
                                    scanViewModel = clientScanViewModel,
                                    manualViewModel = manualConnectViewModel,
                                    peerClient = peerClient,
                                    onClose = { showSyncPanel = false },
                                    onConnected = { showSyncPanel = false },
                                    discoveryViewModel = hostDiscoveryViewModel,
                                )
                            }
                        }
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars),
            )

            if (showShortcutsDialog) {
                ShortcutsSettingsDialog(
                    settings = shortcutsSettings,
                    onChange = { shortcutsSettingsState.value = it },
                    onDismiss = { showShortcutsDialog = false },
                    penButtons = tabletController.penButtons,
                    blurEnabled = readerStored.blurEnabled,
                    onBlurEnabledChange = { onReaderStoredChange(readerStored.copy(blurEnabled = it)) },
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
}

private val SyncConnectedGreen = Color(0xFF2E7D32)
private val SyncUnstableYellow = Color(0xFFF9A825)

/**
 * Tint for the sync button in the quick-actions airbar, signalling connection
 * status:
 * - **Green** — host has at least one connected peer, or client is paired.
 * - **Yellow** — client is [PairingState.Reconnecting]/[PairingState.Error]
 *   or host lifecycle is [ServerLifecycleState.Error] (and no green).
 * - **Default** (`onSurface`) — idle / pairing / lost / both null.
 */
@Composable
private fun syncIndicatorTint(
    hostPeers: Set<DeviceInfo>,
    hostLifecycle: ServerLifecycleState?,
    clientHosts: Set<DeviceInfo>,
    clientStates: Map<String, PairingState>,
): Color {
    val anyConnected = hostPeers.isNotEmpty() || clientHosts.isNotEmpty()
    val hostUnstable = hostLifecycle is ServerLifecycleState.Error
    val anyClientUnstable =
        clientStates.values.any {
            it is PairingState.Reconnecting || it is PairingState.Error
        }
    return when {
        anyConnected -> SyncConnectedGreen
        hostUnstable || anyClientUnstable -> SyncUnstableYellow
        else -> MaterialTheme.colorScheme.onSurface
    }
}

/**
 * Returns the left-edge position of the focused panel as a fraction of screen width [0..1].
 * Used to offset the thumbnail sidebar overlay so it aligns with the panel's left edge.
 */
private fun focusedPanelStartXFraction(layout: WorkspaceLayout): Float {
    val idx = layout.panels.indexOfFirst { it.id == layout.focusedPanelId }
    val r = layout.ratios
    return when (layout.template) {
        LayoutTemplate.FULL -> 0f
        // Both rows span the full width — sidebar aligns to the left edge either way.
        LayoutTemplate.ROWS_2 -> 0f
        LayoutTemplate.COLUMNS_2 -> if (idx == 0) 0f else r[0]
        LayoutTemplate.COLUMNS_3 ->
            when (idx) {
                0 -> 0f
                1 -> r[0]
                else -> r[1]
            }
        LayoutTemplate.LEFT_PLUS_STACK -> if (idx == 0) 0f else r[0]
        LayoutTemplate.GRID_2X2 -> if (idx == 0 || idx == 2) 0f else r[0]
    }
}

/**
 * Returns the horizontal centre of the focused panel as a fraction of screen width [0..1].
 * Used to smoothly slide the [PageIndicatorAirbar] over the active panel.
 */
private fun focusedPanelCenterXFraction(layout: WorkspaceLayout): Float {
    val idx = layout.panels.indexOfFirst { it.id == layout.focusedPanelId }
    val r = layout.ratios
    return when (layout.template) {
        LayoutTemplate.FULL -> 0.5f
        // Both rows are full width — the airbar stays centred for either row.
        LayoutTemplate.ROWS_2 -> 0.5f
        LayoutTemplate.COLUMNS_2 ->
            if (idx == 0) r[0] / 2f else r[0] + (1f - r[0]) / 2f
        LayoutTemplate.COLUMNS_3 ->
            when (idx) {
                0 -> r[0] / 2f
                1 -> r[0] + (r[1] - r[0]) / 2f
                else -> r[1] + (1f - r[1]) / 2f
            }
        LayoutTemplate.LEFT_PLUS_STACK ->
            if (idx == 0) r[0] / 2f else r[0] + (1f - r[0]) / 2f
        LayoutTemplate.GRID_2X2 ->
            if (idx == 0 || idx == 2) r[0] / 2f else r[0] + (1f - r[0]) / 2f
    }
}

/**
 * Боковые шторки фокусного PDF-документа: миниатюры страниц и оглавление, выезжающие
 * слева поверх контента. В портрете ([scrim] = `true`) подложку затемняем и тап по ней
 * закрывает шторку; в ландшафте затемнения нет — там рельса инструментов отъезжает вправо,
 * освобождая место. Видна максимум одна шторка за раз (тогглы взаимоисключающие).
 *
 * @param offsetXPx левый край шторки (px); в портрете 0, в ландшафте — старт фокусной панели
 * @param offsetYPx верх шторки (px) — верх фокусной панели
 * @param heightPx высота шторки (px) — высота панели за вычетом полосы вкладок
 */
@Composable
private fun BoxScope.FocusedPanelMenus(
    focusedState: PdfDocumentState,
    renderer: PdfPageRenderer,
    controls: PanelControls?,
    showThumbnails: Boolean,
    showToc: Boolean,
    scrim: Boolean,
    offsetXPx: Float,
    offsetYPx: Float,
    heightPx: Float,
    onClose: () -> Unit,
) {
    val density = LocalDensity.current
    val drawingStates = focusedState.drawingStates
    val annotatedPageIndices by remember(drawingStates) {
        derivedStateOf {
            val withStrokes =
                drawingStates.entries
                    .filter { it.value.currentPaths.isNotEmpty() }
                    .map { it.key }
            val withHighlights =
                focusedState.highlights.entries
                    .filter { it.value.isNotEmpty() }
                    .map { it.key }
            (withStrokes + withHighlights).toSet()
        }
    }
    val pagePaths: (Int) -> List<DrawingPath> =
        remember(drawingStates) {
            { idx -> drawingStates[idx]?.currentPaths ?: emptyList() }
        }
    val sideMenuModifier: Modifier =
        Modifier
            .align(Alignment.TopStart)
            .offset { IntOffset(offsetXPx.roundToInt(), offsetYPx.roundToInt()) }
            .height(with(density) { heightPx.toDp() })

    if (scrim) {
        AnimatedVisibility(
            visible = (showThumbnails && focusedState.pages.isNotEmpty()) || showToc,
            enter = fadeIn(animationSpec = tween(THUMBNAIL_SIDEBAR_ANIM_MS)),
            exit = fadeOut(animationSpec = tween(THUMBNAIL_SIDEBAR_ANIM_MS)),
            modifier = Modifier.matchParentSize(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = PORTRAIT_SCRIM_ALPHA))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClose,
                    ),
            )
        }
    }
    AnimatedVisibility(
        visible = showThumbnails && focusedState.pages.isNotEmpty(),
        enter =
            slideInHorizontally(animationSpec = tween(THUMBNAIL_SIDEBAR_ANIM_MS)) { -it } +
                fadeIn(animationSpec = tween(THUMBNAIL_SIDEBAR_ANIM_MS)),
        exit =
            slideOutHorizontally(animationSpec = tween(THUMBNAIL_SIDEBAR_ANIM_MS)) { -it } +
                fadeOut(animationSpec = tween(THUMBNAIL_SIDEBAR_ANIM_MS)),
        modifier = sideMenuModifier,
    ) {
        PageThumbnailsSidebar(
            pages = focusedState.pages,
            pdfDocument = focusedState.pdfDocument,
            renderer = renderer,
            currentPage = focusedState.pdfViewerState.firstVisiblePageIndex,
            onPageClick = { pageIndex ->
                // В режиме чтения переход уходит в reflow-ридер (ветка в
                // PanelControls.navigateToPage); иначе — обычная прокрутка вьювера.
                controls?.navigateToPage?.invoke(pageIndex)
                    ?: focusedState.pdfViewerState.scrollToPage(pageIndex, 0)
            },
            annotatedPageIndices = annotatedPageIndices,
            favoritePageIndices = focusedState.favoritePageIndices.toSet(),
            onToggleFavorite = { pageIndex ->
                if (!focusedState.favoritePageIndices.remove(pageIndex)) {
                    focusedState.favoritePageIndices.add(pageIndex)
                }
            },
            pagePaths = pagePaths,
            pageHighlights = { idx -> focusedState.highlights[idx] ?: emptyList() },
            pageSource = { logical ->
                ru.kyamshanov.notepen.annotation.domain.model.SpreadSplit
                    .sourceFor(logical, focusedState.spreadSplit)
            },
        )
    }
    AnimatedVisibility(
        visible = showToc,
        enter =
            slideInHorizontally(animationSpec = tween(THUMBNAIL_SIDEBAR_ANIM_MS)) { -it } +
                fadeIn(animationSpec = tween(THUMBNAIL_SIDEBAR_ANIM_MS)),
        exit =
            slideOutHorizontally(animationSpec = tween(THUMBNAIL_SIDEBAR_ANIM_MS)) { -it } +
                fadeOut(animationSpec = tween(THUMBNAIL_SIDEBAR_ANIM_MS)),
        modifier = sideMenuModifier,
    ) {
        TocSidebar(
            entries = focusedState.outline,
            onEntryClick = { pageIndex ->
                controls?.navigateToPage?.invoke(pageIndex)
                    ?: focusedState.pdfViewerState.scrollToPage(pageIndex, 0)
            },
        )
    }
}

/**
 * Returns the top-edge position of the focused panel in pixels, measured from
 * the top of the workspace grid ([gridHeightPx] tall). A horizontal divider
 * occupies a fixed [dividerPx] that is taken off the weighted split before the
 * rows share the remainder, so a stacked panel's top is
 * `ratio·(gridHeight − divider) + divider`, not `ratio·gridHeight`. Ignoring
 * the divider made the page-indicator stick to the panel's tab strip the taller
 * the lower panel got. Used to push the airbar / thumbnail sidebar onto a
 * stacked (lower) focused panel.
 */
private fun focusedPanelStartYPx(
    layout: WorkspaceLayout,
    gridHeightPx: Float,
    dividerPx: Float,
): Float {
    val idx = layout.panels.indexOfFirst { it.id == layout.focusedPanelId }
    val r = layout.ratios

    fun belowDivider(topRatio: Float) = topRatio * (gridHeightPx - dividerPx) + dividerPx
    return when (layout.template) {
        // Templates with no horizontal divider — every panel starts at the top.
        LayoutTemplate.FULL, LayoutTemplate.COLUMNS_2, LayoutTemplate.COLUMNS_3 -> 0f
        LayoutTemplate.ROWS_2 -> if (idx == 0) 0f else belowDivider(r[0])
        LayoutTemplate.LEFT_PLUS_STACK -> if (idx == 2) belowDivider(r[1]) else 0f
        LayoutTemplate.GRID_2X2 -> if (idx >= 2) belowDivider(r[1]) else 0f
    }
}

/**
 * Returns the left-edge pixel position of the focused panel inside the window,
 * accounting for the fixed [dividerPx] consumed by each vertical divider between
 * columns. Used to position the thumbnail sidebar so it starts exactly at the
 * panel's content edge rather than inside a divider's hit zone.
 */
private fun focusedPanelStartXPx(
    layout: WorkspaceLayout,
    windowWidthPx: Float,
    dividerPx: Float,
): Float {
    val idx = layout.panels.indexOfFirst { it.id == layout.focusedPanelId }
    val r = layout.ratios
    val split1 = windowWidthPx - dividerPx
    val split2 = windowWidthPx - 2 * dividerPx
    return when (layout.template) {
        LayoutTemplate.FULL, LayoutTemplate.ROWS_2 -> 0f
        LayoutTemplate.COLUMNS_2 -> if (idx == 0) 0f else split1 * r[0] + dividerPx
        LayoutTemplate.COLUMNS_3 ->
            when (idx) {
                0 -> 0f
                1 -> split2 * r[0] + dividerPx
                else -> split2 * r[1] + 2 * dividerPx
            }
        LayoutTemplate.LEFT_PLUS_STACK -> if (idx == 0) 0f else split1 * r[0] + dividerPx
        LayoutTemplate.GRID_2X2 -> if (idx == 0 || idx == 2) 0f else split1 * r[0] + dividerPx
    }
}

/**
 * Returns the height of the focused panel in pixels, accounting for the fixed
 * [dividerPx] taken out of any horizontal split (see [focusedPanelStartYPx]).
 * Used to clamp the thumbnail sidebar to the focused panel so it stops at the
 * row divider instead of covering the divider's drag zone and the other panel.
 */
private fun focusedPanelHeightPx(
    layout: WorkspaceLayout,
    gridHeightPx: Float,
    dividerPx: Float,
): Float {
    val idx = layout.panels.indexOfFirst { it.id == layout.focusedPanelId }
    val r = layout.ratios
    val split = gridHeightPx - dividerPx
    return when (layout.template) {
        LayoutTemplate.FULL, LayoutTemplate.COLUMNS_2, LayoutTemplate.COLUMNS_3 -> gridHeightPx
        LayoutTemplate.ROWS_2 -> if (idx == 0) r[0] * split else (1f - r[0]) * split
        LayoutTemplate.LEFT_PLUS_STACK ->
            when (idx) {
                0 -> gridHeightPx
                1 -> r[1] * split
                else -> (1f - r[1]) * split
            }
        LayoutTemplate.GRID_2X2 -> if (idx >= 2) (1f - r[1]) * split else r[1] * split
    }
}

/**
 * Сопоставляет нажатую клавишу [key] перелистыванию ридера: `+1` — следующая
 * страница, `-1` — предыдущая, `null` — клавиша не про листание.
 *
 * Раскладка под горизонтальное листание: вправо/PageDown/Space → вперёд;
 * влево/PageUp/Shift+Space → назад (согласовано с тап-зонами и свайпом). Клавиши
 * громкости (Android) тоже учитываем — вниз листает вперёд, вверх назад, — на
 * случай если событие дойдёт сюда мимо оконного перехвата.
 */
private fun readerPageTurnDelta(
    key: Key,
    shiftHeld: Boolean,
): Int? =
    when (key) {
        Key.DirectionRight, Key.PageDown, Key.VolumeDown -> 1
        Key.DirectionLeft, Key.PageUp, Key.VolumeUp -> -1
        Key.Spacebar -> if (shiftHeld) -1 else 1
        else -> null
    }
