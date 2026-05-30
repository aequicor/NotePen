@file:OptIn(FlowPreview::class)

package ru.kyamshanov.notepen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ru.kyamshanov.notepen.annotation.domain.model.AnnotationViewState
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.model.EraserSettings
import ru.kyamshanov.notepen.annotation.domain.model.MarkerSettings
import ru.kyamshanov.notepen.annotation.domain.model.NormalizedRect
import ru.kyamshanov.notepen.annotation.domain.model.PageExtent
import ru.kyamshanov.notepen.annotation.domain.model.PageNote
import ru.kyamshanov.notepen.annotation.domain.model.PenSettings
import ru.kyamshanov.notepen.annotation.domain.model.StickyHighlight
import ru.kyamshanov.notepen.annotation.domain.model.ToolKind
import ru.kyamshanov.notepen.annotation.domain.model.sanitizedForCurrentScheme
import ru.kyamshanov.notepen.annotation.domain.port.AnnotationRepository
import ru.kyamshanov.notepen.annotation.domain.port.PdfExporter
import ru.kyamshanov.notepen.blur.glassSource
import ru.kyamshanov.notepen.book.DocumentOutlineProvider
import ru.kyamshanov.notepen.magnifier.LoupeSelectionController
import ru.kyamshanov.notepen.magnifier.MagnifierInputPanel
import ru.kyamshanov.notepen.magnifier.asMagnifierGeometry
import ru.kyamshanov.notepen.mainscreen.domain.model.generateUuid
import ru.kyamshanov.notepen.pdf.domain.port.PdfDocumentLoader
import ru.kyamshanov.notepen.pdf.domain.port.PdfPageRenderer
import ru.kyamshanov.notepen.pdf.presentation.toImageBitmap
import ru.kyamshanov.notepen.pdfviewer.PdfPagesViewer
import ru.kyamshanov.notepen.pdfviewer.PdfViewerState
import ru.kyamshanov.notepen.pdfviewer.ScrollMode
import ru.kyamshanov.notepen.pdfviewer.asPageLayoutGeometry
import ru.kyamshanov.notepen.reflow.BuildReflowReadingUseCase
import ru.kyamshanov.notepen.reflow.ReflowPageLocator
import ru.kyamshanov.notepen.reflow.ReflowReading
import ru.kyamshanov.notepen.reflow.StrokeTextMapper
import ru.kyamshanov.notepen.reflow.api.PageBitmapProvider
import ru.kyamshanov.notepen.reflow.api.PageRaster
import ru.kyamshanov.notepen.reflow.api.PdfContentKind
import ru.kyamshanov.notepen.reflow.api.PdfReflowExtractor
import ru.kyamshanov.notepen.reflow.api.ReflowDocument
import ru.kyamshanov.notepen.reflow.api.StoredReaderSettings
import ru.kyamshanov.notepen.reflow.api.TextAnchor
import ru.kyamshanov.notepen.reflow.createReflowLayoutCache
import ru.kyamshanov.notepen.reflow.ui.LocalReflowLayoutCache
import ru.kyamshanov.notepen.reflow.ui.LocalReflowSelection
import ru.kyamshanov.notepen.reflow.ui.ReflowReader
import ru.kyamshanov.notepen.reflow.ui.ReflowSelection
import ru.kyamshanov.notepen.reflow.ui.toRenderSettings
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
import ru.kyamshanov.notepen.tablet.LocalTabletInputController
import ru.kyamshanov.notepen.tablet.PenPointerEventType
import ru.kyamshanov.notepen.tablet.stylusEventSink
import ru.kyamshanov.notepen.tabs.DocumentId
import ru.kyamshanov.notepen.tabs.Panel
import ru.kyamshanov.notepen.tabs.PdfDocumentState
import ru.kyamshanov.notepen.tabs.TAB_BAR_HEIGHT
import ru.kyamshanov.notepen.tabs.TabBar
import ru.kyamshanov.notepen.tabs.TabCloseResult
import ru.kyamshanov.notepen.tabs.TabSession
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

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

/** Вертикальный зазор между спиннером и подписью в плейсхолдере «Открываем книгу…». */
private val PREPARING_INDICATOR_SPACING = 12.dp

/** Размеры поповера редактирования тела заметки ([NoteBodyPopover]). */
private val NOTE_POPOVER_WIDTH = 320.dp
private val NOTE_POPOVER_PADDING = 16.dp
private val NOTE_POPOVER_ELEVATION = 6.dp

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
    val showToc: Boolean,
    /** `true`, когда у документа есть оглавление — иначе кнопку ToC скрываем. */
    val hasToc: Boolean,
    val readingModeEnabled: Boolean,
    /**
     * `true`, когда документ содержит извлекаемый текст (reflow применим) —
     * иначе кнопку режима чтения дизейблим. По умолчанию `true`, пока проба не
     * завершилась или упала, чтобы текстовый PDF не блокировался ошибочно.
     */
    val readingModeAvailable: Boolean,
    /** `true` в режиме чтения, когда airbar скрыт тапом — родитель прячет весь хром. */
    val chromeHidden: Boolean,
    val quickLoupeArmed: Boolean,
    val scrollMode: ScrollMode,
    val zoomIn: () -> Unit,
    val zoomOut: () -> Unit,
    val toggleMagnifier: () -> Unit,
    val export: () -> Unit,
    val toggleThumbnails: () -> Unit,
    val toggleToc: () -> Unit,
    val toggleReadingMode: () -> Unit,
    val navigateToPage: (Int) -> Unit,
    /**
     * Листает ридер на ±N страниц в режиме чтения (хардварные клавиши:
     * стрелки/PageUp-Down/Space, на Android — громкость). No-op вне режима чтения
     * или пока ридер не готов. Реализацию поставляет [ReflowReader] (paged —
     * пейджером, scroll — прокруткой на дельту экранов).
     */
    val readerPageDelta: (Int) -> Unit,
    val toggleQuickLoupe: () -> Unit,
    val cycleScrollMode: () -> Unit,
    /** Поворачивает текущую (первую видимую) страницу на +90° CW кумулятивно. */
    val rotateCurrentPage: () -> Unit,
    /** Включено ли разделение разворотов (FEATURE #4). */
    val spreadSplitEnabled: Boolean,
    /** Переключает разделение разворотов на левую/правую логические страницы. */
    val toggleSpreadSplit: () -> Unit,
    /**
     * Активен ли книжный разворот «Две страницы» (FEATURE #5) — эффективное
     * состояние (авто-по-ширине ИЛИ явный выбор пользователя). Управляет
     * selected-состоянием кнопки.
     */
    val bookSpreadEnabled: Boolean,
    /**
     * Переключает книжный разворот вкл/выкл как ЯВНЫЙ выбор пользователя
     * (перекрывает авто-по-ширине). No-op в режиме чтения.
     */
    val toggleBookSpread: () -> Unit,
    /**
     * Доступна ли «синхронизация документа» (M4) — `true`, когда поднят sync-стек
     * (есть [LiveDocumentSyncController]). Иначе тумблер скрываем.
     */
    val liveSyncAvailable: Boolean,
    /** Включена ли живая синхронизация этого документа сейчас. */
    val liveSyncEnabled: Boolean,
    /** Переключает живую синхронизацию документа (PC ↔ планшет правки в реальном времени). */
    val toggleLiveSync: () -> Unit,
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
    outlineProvider: DocumentOutlineProvider,
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
    readerStored: StoredReaderSettings,
    /**
     * `true` после первой загрузки [readerStored] с диска. Пока `false` — фактическое
     * значение `readerStored` неотличимо от дефолтных настроек, поэтому хром/фон/плейсхолдер
     * НЕ красятся «темой ридера» (иначе пользователь увидит дефолт-ридер, а затем скачок
     * в сохранённую тему через 50–200 мс I/O). После true — одним кадром переходим в
     * сохранённую тему.
     */
    readerStoredLoaded: Boolean,
    onReaderStoredChange: (StoredReaderSettings) -> Unit,
    syncEngineFor: ((documentId: String) -> SyncEngine)?,
    peerClient: SyncClient?,
    /**
     * Host-side peer server (desktop). Used together with [peerClient] only to
     * detect whether any peer is currently connected, so opening a document can
     * auto-enable live sync (the «share just works» path). `null` on the client.
     */
    peerServer: ru.kyamshanov.notepen.sync.domain.port.PeerServer?,
    pendingDeltaCounts: kotlinx.coroutines.flow.Flow<Map<String, Int>>?,
    receivedPdfDir: String?,
    openDocumentRegistry: ru.kyamshanov.notepen.sync.domain.port.OpenDocumentRegistry?,
    /**
     * Тонкий контроллер «синхронизации документа» (M4). Когда задан, тумблер в
     * тулбаре включает/выключает живую синхронизацию для этого документа; `null` —
     * sync-стек не поднят, тумблер не показываем.
     */
    liveSyncController: ru.kyamshanov.notepen.sync.domain.LiveDocumentSyncController?,
    hostAnnotationSnapshotFor: (suspend (documentId: String) -> List<StrokeDelta.Added>)?,
    showSnackbar: (String) -> Unit,
    onRestoreToolSettings: (PenSettings, MarkerSettings, EraserSettings) -> Unit,
    onAddTab: () -> Unit,
    onAllTabsClosed: () -> Unit,
    onOpenPanelPicker: ((DocumentId) -> Unit)?,
    onClosePanel: (() -> Unit)?,
    onControlsChanged: (PanelControls?) -> Unit,
    /**
     * Dropdown content for the tab strip's left «Сессии» button (save / restore
     * the workspace). Invoked with the menu's `expanded` flag and an `onDismiss`;
     * see [TabBar]'s `sessionsMenu`.
     */
    sessionsMenu: @Composable (expanded: Boolean, onDismiss: () -> Unit) -> Unit,
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
    val drawingStates = pdfState.drawingStates
    val favoritePageIndices = pdfState.favoritePageIndices
    val pdfViewerState: PdfViewerState = pdfState.pdfViewerState
    val magnifierState = pdfState.magnifierState
    val globalUndoStack = pdfState.undoStack
    val globalRedoStack = pdfState.redoStack
    val pageRotations = pdfState.pageRotations
    // Включено ли разделение разворотов (FEATURE #4). Читаем наблюдаемое поле,
    // поэтому переключение пере-строит pages и резолвер источника ниже.
    val spreadSplit = pdfState.spreadSplit
    // Книжный разворот «Две страницы» (FEATURE #5) — отдельный от #4 и reflow
    // механизм. По умолчанию ВЫКЛЮЧЕН (как и разделение #4): включается только
    // явным выбором пользователя ([spreadViewOverride] == true). В режиме чтения
    // разворот неприменим (reflow — одноколоночный текст), поэтому форсим SINGLE —
    // это и есть выполнение пользовательского требования «разворот ≠ режим чтения».
    val spreadViewOverride = pdfState.spreadViewOverride
    val bookSpreadEnabled =
        !pdfState.readingMode && (spreadViewOverride ?: false)

    // Пользовательский поворот страницы по ЛОГИЧЕСКОМУ индексу (читает наблюдаемую
    // карту, поэтому смена дёргает релэйаут/ре-рендер). Передаётся и в рендерер
    // растра, и в раскладку (через «эффективные» pages ниже).
    val userRotationOf: (Int) -> Int = { idx -> pageRotations[idx] ?: 0 }
    // Резолвер ЛОГИЧЕСКИЙ индекс → (исходный индекс + вырезка). При выключенном
    // разделении тождественный; при включённом каждая логическая половина
    // отображается в исходную страницу + левую/правую вырезку (см. SpreadSplit).
    val pageSourceOf: (Int) -> ru.kyamshanov.notepen.pdfviewer.PageSourceSpec = { logical ->
        val src =
            ru.kyamshanov.notepen.annotation.domain.model.SpreadSplit
                .sourceFor(logical, spreadSplit)
        ru.kyamshanov.notepen.pdfviewer.PageSourceSpec(
            sourceIndex = src.sourceIndex,
            cropLeftN = src.crop.leftN,
            cropTopN = src.crop.topN,
            cropRightN = src.crop.rightN,
            cropBottomN = src.crop.bottomN,
        )
    }
    // «Эффективные» страницы для раскладки: aspectRatio должен учитывать
    // суммарный поворот (собственный PDF + пользовательский). Поле rotation
    // здесь — только для расчёта aspectRatio слота; рендерер берёт собственный
    // поворот из самого документа, а пользовательский — отдельным параметром,
    // поэтому двойного учёта нет.
    val pages by remember(pdfState) {
        derivedStateOf {
            pdfState.pages.map { info ->
                val user = pageRotations[info.pageIndex] ?: 0
                if (user == 0) {
                    info
                } else {
                    info.copy(
                        rotation =
                            ru.kyamshanov.notepen.annotation.domain.model.PageRotation
                                .effectiveDegrees(info.rotation, user),
                    )
                }
            }
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val tabletController = LocalTabletInputController.current
    val syncEngine = remember(syncEngineFor, documentId) { syncEngineFor?.invoke(documentId) }

    // Per-document live-sync toggle (M4). Default OFF: bind pauses this document's
    // engine on open so merely opening it never starts broadcasting. The toolbar
    // reflects [liveSyncEnabled].
    //
    // On close (onDispose) we `disable` the document: that flips the toggle OFF,
    // pauses broadcasting, AND releases the CONTROLLER's enable-time pin (the
    // ref-counted `acquire` the controller takes in `enable` to protect active-sync
    // consistency). This is a DIFFERENT pin from the editor's own open-lifetime pin
    // taken below in the `openDocumentRegistry` DisposableEffect (~787) — that one
    // exists purely to prevent the file being deleted while it's open. Both are
    // ref-counted on the same registry, so they release independently and correctly.
    val liveSyncEnabled by produceState(false, liveSyncController, documentId) {
        val controller = liveSyncController ?: return@produceState
        controller.isLive(documentId).collect { value = it }
    }
    // Есть ли хотя бы один подключённый пир (как хост, так и клиент).
    val anyPeerConnected by produceState(false, peerClient, peerServer) {
        val hosts = peerClient?.connectedHosts ?: flowOf(emptySet())
        val peers = peerServer?.connectedPeers ?: flowOf(emptySet())
        combine(hosts, peers) { h, p -> h.isNotEmpty() || p.isNotEmpty() }
            .collect { value = it }
    }
    DisposableEffect(liveSyncController, documentId) {
        liveSyncController?.bind(documentId)
        onDispose { liveSyncController?.disable(documentId) }
    }
    // Явная ручная пауза пользователя для ЭТОГО документа. Нужна, чтобы авто-
    // включение при ПЕРЕподключении пира (Wi-Fi моргнул, кабель передёрнули, пир
    // свернулся) не перетирало осознанное выключение синка. Сбрасывается при смене
    // документа (remember(documentId)).
    var userPausedSync by remember(documentId) { mutableStateOf(false) }
    // Шаринг «из коробки»: пока есть подключённый пир, открытие документа само
    // включает живую синхронизацию — одна книга на двух спаренных устройствах
    // синхронизирует надписи без ручного тумблера. Если пользователь явно поставил
    // документ на паузу, авто-включение не срабатывает даже при переподключении.
    LaunchedEffect(liveSyncController, documentId, anyPeerConnected, userPausedSync) {
        if (anyPeerConnected && !userPausedSync) liveSyncController?.enable(documentId)
    }

    var panelSizePx by remember { mutableStateOf(IntSize.Zero) }
    var showThumbnails by remember { mutableStateOf(false) }
    var showToc by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    // Доступность режима чтения зависит от наличия извлекаемого текста (см.
    // PanelControls.readingModeAvailable). По умолчанию false: кнопку показываем
    // только когда проба подтвердила текстовый слой. Для картинок/не-PDF (проба
    // падает) и image-only PDF кнопка остаётся скрытой — иначе вход завис бы на
    // «Готовим режим чтения…».
    var readingModeAvailable by remember(pdfState) { mutableStateOf(false) }

    // ---- Reading (reflow) mode -------------------------------------------
    val reflowReadingUseCase = remember(reflowExtractor) { BuildReflowReadingUseCase(reflowExtractor) }
    // Дисковый layout-cache: один на сессию редактора. На cache HIT раскладка
    // мгновенно поднимается из бинарника, экономя ~3-4 секунды bg-обмера.
    val reflowLayoutCache = remember { createReflowLayoutCache() }
    val reflowListState = remember(pdfState) { LazyListState() }
    // Императивный «листнуть на ±N», который публикует ReflowReader, когда контент
    // готов. Через него хардварные клавиши (общий key-sink в DetailsContent →
    // PanelControls.readerPageDelta) и Android-громкость листают страницы.
    val reflowPageDelta = remember(pdfState) { mutableStateOf<((Int) -> Unit)?>(null) }
    val reflowNavigateToBlock: MutableState<Int?> = remember(pdfState) { mutableStateOf(null) }
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
    // Кэш извлечённого reflow-текста для синхронного снаппинга «липкого маркера» в
    // редакторе: extract асинхронный, на каждый штрих его дёргать нельзя. Заполняется
    // фоном при наличии текстового слоя и липком маркере; переиспользуется режимом чтения.
    val reflowDocCacheState = remember(pdfState) { mutableStateOf<ReflowDocument?>(null) }
    var reflowReading by remember(pdfState) { mutableStateOf<ReflowReading?>(null) }
    // Заметка, открытая в поповере для редактирования/удаления тела (тап по бейджу в
    // редакторе или по маркеру заметки в ридере). null — поповер скрыт. Сбрасывается
    // при смене документа.
    var editingNote by remember(pdfState) { mutableStateOf<PageNote?>(null) }
    // Якорь чтения уровня документа: «валюта» позиции в reflow-режиме (см. TextAnchor).
    // Живёт здесь, а не в ReflowReader: переживает закрытие/открытие reader-mode в сессии
    // и пишется в .view-сайдкар (autosave ниже), чтобы при следующем открытии документа
    // вернуть пользователя на ту же страницу даже после смены шрифта/ориентации.
    var currentReadingAnchor by remember(pdfState) { mutableStateOf(TextAnchor.START) }
    // Флаг «первый вход в reader-mode восстанавливает сохранённый якорь». Включается,
    // когда loadViewState восстановил позицию reflow с диска, и гасится при первом
    // срабатывании reading-mode-enter эффекта. Нужен, чтобы НЕ перебить сохранённую
    // позицию маппингом «PDF-страница → блок» (этот маппинг применяем только при
    // mid-session toggle PDF→reading, когда пользователь действительно в PDF-навигации).
    var useRestoredAnchorOnFirstEnter by remember(pdfState) { mutableStateOf(false) }
    LaunchedEffect(pdfState.readingMode, pdfState) {
        if (!pdfState.readingMode) {
            reflowReading = null
            return@LaunchedEffect
        }
        // Запоминаем текущую страницу до извлечения, чтобы при открытии режима
        // чтения сразу встать на соответствующий абзац.
        val targetPage = pdfViewerState.firstVisiblePageIndex
        val strokesByPage = drawingStates.mapValues { (_, state) -> state.currentPaths.toList() }
        val highlightsByPage = pdfState.highlights.toMap()
        // Lattice-провайдер пиксельных снимков страницы: дёргается только из
        // [LatticeTableRefiner] (использует кадры лениво — лишь для страниц с
        // low-conf Stream-table кандидатами). Если PDF ещё не загружен, передаём
        // null — use case откатывается на plain extract без Lattice.
        val pageBitmapsForLattice: PageBitmapProvider? =
            pdfState.pdfDocument?.let { _ ->
                { pageIndex, targetWidthPx ->
                    val doc = pdfState.pdfDocument
                    val info = doc?.info?.pages?.getOrNull(pageIndex)
                    if (doc == null || info == null) {
                        null
                    } else {
                        val ratio = info.aspectRatio.takeIf { it > 0f } ?: 1f
                        val heightPx = (targetWidthPx / ratio).toInt().coerceAtLeast(1)
                        runCatching { renderer.renderPage(doc, pageIndex, targetWidthPx, heightPx) }
                            .getOrNull()
                            ?.let { PageRaster(pixels = it.pixels, widthPx = it.widthPx, heightPx = it.heightPx) }
                    }
                }
            }
        val reading =
            runCatching {
                reflowReadingUseCase(
                    path = filePath,
                    strokesByPage = strokesByPage,
                    highlightsByPage = highlightsByPage,
                    document = reflowDocCacheState.value,
                    pageBitmaps = pageBitmapsForLattice,
                )
            }
                .onFailure { e -> panelLogger.warn { "Reflow reading build failed: ${e::class.simpleName}" } }
                .getOrNull()
        if (reading != null) {
            if (reflowDocCacheState.value == null) reflowDocCacheState.value = reading.document
            // На самом первом входе с восстановленной позицией пропускаем маппинг
            // PDF-страница→блок — иначе перекрыл бы saved reflowAnchor PDF-страницей.
            // Для mid-session toggle (после рисования в PDF) маппинг как раз нужен.
            val consumeRestored = useRestoredAnchorOnFirstEnter
            useRestoredAnchorOnFirstEnter = false
            if (!consumeRestored) {
                // Mid-session toggle PDF→reading: seed the DURABLE anchor (а не только
                // волатильный one-shot navigateToBlock). initialAnchor = currentReadingAnchor
                // читается, когда ридер впервые компонуется, поэтому pager сразу садится на
                // окно нужной страницы — без гонки с фоновой загрузкой и без page-0 feedback,
                // который раньше тянул и PDF-вьюер назад на страницу 0. Делаем ДО
                // `reflowReading = reading`, чтобы анкер был выставлен до композиции ридера.
                ReflowPageLocator
                    .blockIndexForPage(reading.document, targetPage)
                    ?.let { block ->
                        currentReadingAnchor = TextAnchor.ofBlock(block)
                        // One-shot оставляем для надёжности (если анкер по какой-то причине
                        // не отработает на первом кадре) — он идемпотентен с анкер-сидом.
                        reflowNavigateToBlock.value = block
                    }
            }
        }
        // Триггерим композицию ридера ПОСЛЕ сидирования durable-анкера выше, чтобы
        // initialAnchor прочитал уже корректную позицию (см. Defect F).
        reflowReading = reading
    }

    // Фоновое извлечение reflow-текста для снаппинга «липкого маркера» в редакторе:
    // запускается, когда у документа есть текстовый слой (readingModeAvailable) и маркер
    // липкий. Один раз на документ; результат переиспользуется режимом чтения.
    LaunchedEffect(pdfState, readingModeAvailable, markerSettings.sticky, filePath) {
        if (reflowDocCacheState.value != null) return@LaunchedEffect
        if (!readingModeAvailable || !markerSettings.sticky) return@LaunchedEffect
        reflowDocCacheState.value =
            runCatching { reflowExtractor.extract(filePath) }
                .onFailure { e -> panelLogger.warn { "Sticky-marker text extract failed: ${e::class.simpleName}" } }
                .getOrNull()
    }

    val hasAnnotations by remember {
        derivedStateOf { drawingStates.values.any { it.currentPaths.isNotEmpty() } }
    }
    val annotatedPageIndices by remember {
        derivedStateOf {
            drawingStates
                .asSequence()
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
        // Книжный разворот (FEATURE #5): пробрасываем эффективный режим в вьювер.
        // Смена пере-строит layout (пейринг логических страниц по X) и дёргает
        // релэйаут — координаты штрихов при этом НЕ меняются (разворот чисто
        // визуальный, в отличие от FEATURE #4 split).
        pdfViewerState.spreadMode =
            if (bookSpreadEnabled) {
                ru.kyamshanov.notepen.pdfviewer.SpreadMode.SPREAD
            } else {
                ru.kyamshanov.notepen.pdfviewer.SpreadMode.SINGLE
            }
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
            val (w, h) =
                if (heightFromWidth > PANEL_HIGH_RES_DIM_PX) {
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

    val syncBridge =
        remember(syncEngine) {
            syncEngine?.let {
                SyncBridge(
                    engine = it,
                    drawingStates = drawingStates,
                    notes = pdfState.notes,
                    scope = coroutineScope,
                )
            }
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
                snapshot.notes.forEach { upserted ->
                    val incoming = upserted.note.toDomain()
                    val page = upserted.pageIndex
                    val existing = pdfState.notes[page].orEmpty()
                    pdfState.notes[page] =
                        if (existing.any { it.noteId == incoming.noteId }) {
                            existing.map { if (it.noteId == incoming.noteId) incoming else it }
                        } else {
                            existing + incoming
                        }
                }
            }
    }

    // ---- Tablet: aggregate pairing state + offline / reconnect snackbars --
    var clientPairingState by remember { mutableStateOf<PairingState?>(null) }
    val isRemoteOpenedDoc =
        remember(filePath, receivedPdfDir) {
            receivedPdfDir != null && filePath.startsWith(receivedPdfDir)
        }
    LaunchedEffect(peerClient) {
        val client = peerClient ?: return@LaunchedEffect
        client.pairingStates.collect { states ->
            clientPairingState =
                when {
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
    val showOfflineBanner =
        peerClient != null &&
            clientPairingState != null &&
            clientPairingState !is PairingState.Idle &&
            clientPairingState !is PairingState.Connected &&
            pendingForDoc > 0

    val saveLocallyAndNotify: suspend (String) -> Unit = { message ->
        val annotations = drawingStates.mapValues { (_, state) -> state.currentPaths.toList() }
        val extents = drawingStates.mapValues { (_, state) -> state.extent.value }
        val result =
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
                highlights = pdfState.highlights.toMap(),
                notes = pdfState.notes.toMap(),
            )
        showSnackbar(if (result.isSuccess) message else "Ошибка локального сохранения")
    }
    var previouslyConnected by remember(filePath) { mutableStateOf(false) }
    var previouslyOffline by remember(filePath) { mutableStateOf(false) }
    LaunchedEffect(clientPairingState, isRemoteOpenedDoc) {
        if (!isRemoteOpenedDoc) return@LaunchedEffect
        val nowConnected = clientPairingState is PairingState.Connected
        val nowOffline =
            clientPairingState is PairingState.Reconnecting ||
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
                    val syncedInTime =
                        if (flow != null) {
                            withTimeoutOrNull(PANEL_REPLAY_DEADLINE) {
                                flow.first { (it[documentId] ?: 0) == 0 }
                                true
                            } ?: false
                        } else {
                            false
                        }
                    showSnackbar(
                        if (syncedInTime) {
                            "Соединение восстановлено. Изменения синхронизированы"
                        } else {
                            "Соединение восстановлено, но не все изменения отправлены"
                        },
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

    // ---- TOC outline load -------------------------------------------------
    // Грузим оглавление один раз на таб. Провайдер сам переключает диспетчер на
    // IO (commonMain не трогает Dispatchers.* напрямую); для обычных PDF вернёт
    // пустой список, и сайдбар покажет заглушку.
    LaunchedEffect(pdfState) {
        if (pdfState.outline.isNotEmpty()) return@LaunchedEffect
        val outline =
            runCatching { outlineProvider.outlineFor(filePath) }
                .onFailure { e -> panelLogger.warn { "Outline load failed: ${e::class.simpleName}" } }
                .getOrDefault(emptyList())
        pdfState.outline = outline
    }

    // ---- Reflow availability probe ---------------------------------------
    // Классифицируем содержимое (есть ли извлекаемый текст), чтобы решить,
    // ПОКАЗЫВАТЬ ли кнопку режима чтения. probe — main-safe (сам уводит блок-IO
    // на инжектируемый диспетчер). На ошибке пробы (например, не-PDF картинка
    // вроде PNG) считаем недоступным — кнопку не показываем, иначе вход завис бы
    // на «Готовим режим чтения…».
    LaunchedEffect(pdfState, filePath) {
        readingModeAvailable =
            runCatching { reflowExtractor.probe(filePath) != PdfContentKind.IMAGE_ONLY }
                .onFailure { e -> panelLogger.warn { "Reflow probe failed: ${e::class.simpleName}" } }
                .getOrDefault(false)
    }

    LaunchedEffect(openDocs.tabs) {
        openDocs.tabs.forEach { tab ->
            val state = tabSession.stateOf(tab)
            if (state.pdfDocument == null && !state.isPdfLoading) {
                val sameFileIsLoading =
                    openDocs.tabs.any { other ->
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

    // Editor's open-lifetime pin: held for as long as the document is OPEN in the
    // editor, regardless of the live-sync toggle. Its sole job is to stop background
    // services (e.g. LocalCachedDocumentCleaner) from deleting the file while it's on
    // screen. This is SEPARATE from the controller's enable-time pin (acquired in
    // LiveDocumentSyncController.enable, released on disable) which guards active-sync
    // consistency only while live-sync is ON. Distinct concerns, distinct lifetimes;
    // the registry ref-counts both so they coexist (e.g. open + live-sync-on = +2).
    DisposableEffect(openDocumentRegistry, documentId) {
        openDocumentRegistry?.acquire(documentId)
        onDispose { openDocumentRegistry?.release(documentId) }
    }

    // ---- Annotation load --------------------------------------------------
    LaunchedEffect(pdfState) {
        // Session restore positions a tab via [PdfDocumentState.pendingViewOverride].
        // Applied first and independently of the shared annotation load, so even a
        // second tab of the same file (whose annotations are already loaded by its
        // sibling) still gets its own restored scroll / zoom.
        val viewOverride = pdfState.pendingViewOverride
        pdfState.pendingViewOverride = null
        if (viewOverride != null) {
            pdfViewerState.applyInitialState(
                scalePercent = viewOverride.scalePercent,
                pageIndex = viewOverride.pageIndex,
                pageOffsetPx = viewOverride.pageOffsetPx,
            )
        }
        if (pdfState.annotationsLoaded) return@LaunchedEffect
        pdfState.annotationsLoaded = true
        val restoredView =
            annotationRepository.loadViewState(filePath).getOrNull()?.also { view ->
                if (viewOverride == null) {
                    pdfViewerState.applyInitialState(
                        scalePercent = view.scale,
                        pageIndex = if (pdfState.skipPageRestore) 0 else view.currentPage,
                        pageOffsetPx = if (pdfState.skipPageRestore) 0 else view.currentPageOffset,
                    )
                }
                // Вторичный таб того же файла открываем в обычном (не reading) режиме —
                // как и позицию, режим чтения для него не восстанавливаем.
                pdfState.readingMode = if (pdfState.skipPageRestore) false else view.readingMode
                // Восстанавливаем reflow-якорь (если был сохранён) — даже если режим
                // чтения сейчас не активен: при последующем переключении в reader-mode
                // initialAnchor вернёт пользователя на ту же страницу. На вторичном табе
                // (skipPageRestore) намеренно стартуем с начала, как и с позицией PDF.
                if (!pdfState.skipPageRestore) {
                    currentReadingAnchor = TextAnchor.ofBlock(view.reflowAnchorBlockIndex)
                    useRestoredAnchorOnFirstEnter = true
                }
                // Разделение разворотов (FEATURE #4) восстанавливаем ДО штрихов:
                // оно задаёт логическое индекс-пространство, в котором штрихи и
                // повороты на диске уже лежат. На вторичном табе того же файла
                // (skipPageRestore) состояние shared — уже выставлено первой
                // вкладкой, поэтому не трогаем.
                if (!pdfState.skipPageRestore) {
                    pdfState.spreadSplit = view.spreadSplit
                    // Книжный разворот (FEATURE #5): восстанавливаем ЯВНЫЙ выбор
                    // пользователя (null = авто-по-ширине). Отдельно от #4 и reflow.
                    pdfState.spreadViewOverride = view.spreadViewOverride
                }
                // Пользовательский поворот страниц персистентен — восстанавливаем
                // (штрихи на диске уже в повёрнутой/разделённой системе координат,
                // так что пере-трансформировать не нужно: поворот применяется к
                // растру и раскладке). Карта shared между табами одного файла,
                // поэтому заполняем её один раз — первой активной вкладкой.
                if (pageRotations.isEmpty()) {
                    view.pageRotations.forEach { (idx, q) ->
                        val n = ru.kyamshanov.notepen.annotation.domain.model.PageRotation.normalizeQuarters(q)
                        if (n != 0) pageRotations[idx] = n
                    }
                }
            }
        val projectionStrokes =
            hostAnnotationSnapshotFor
                ?.let { provider ->
                    runCatching { provider(documentId) }.getOrElse { e ->
                        panelLogger.warn { "Host projection read failed for doc=$documentId: ${e::class.simpleName}" }
                        emptyList()
                    }
                }.orEmpty()

        annotationRepository.load(filePath).getOrNull()?.let { bundle ->
            if (restoredView == null && viewOverride == null) {
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
            // Липкие выделения не синхронизируются дельтами — грузим с диска всегда,
            // независимо от projection-штрихов.
            bundle.highlights.forEach { (pageIndex, hs) -> pdfState.highlights[pageIndex] = hs }
            // Заметки, как и липкие выделения, не приходят начальным снапшотом дельт —
            // грузим с диска всегда.
            bundle.notes.forEach { (pageIndex, ns) -> pdfState.notes[pageIndex] = ns }
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
                highlights = state.highlights.toMap(),
                notes = state.notes.toMap(),
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
                pdfState.highlights.values.sumOf { it.size } +
                pdfState.notes.values.sumOf { it.size } +
                pdfState.notes.values.sumOf { ns -> ns.sumOf { n -> n.updatedAt } }.toInt() +
                penSettings.hashCode() + markerSettings.hashCode() + eraserSettings.hashCode()
        }.drop(1)
            .distinctUntilChanged()
            .debounce(PANEL_AUTOSAVE_DEBOUNCE)
            .collect {
                saveTab(pdfState)
                if (isRemoteOpenedDoc) coroutineScope.launch { requestRemoteSaveIfConnected() }
            }
    }

    // ---- View-state autosave (зум/страница/режим чтения) ------------------
    // Пишем только лёгкий .view-сайдкар, не тяжёлый файл штрихов, поэтому не
    // жалко дёргать на каждый скролл (debounce срезает частоту). Закрывает и
    // «убийство приложения»: навигация успевает сохраниться по ходу сессии,
    // а не только на «назад»/«в библиотеку».
    LaunchedEffect(pdfState) {
        snapshotFlow {
            AnnotationViewState(
                scale = pdfViewerState.scalePercent,
                currentPage = pdfViewerState.firstVisiblePageIndex,
                currentPageOffset = pdfViewerState.firstVisiblePageOffsetPx,
                readingMode = pdfState.readingMode,
                reflowAnchorBlockIndex = currentReadingAnchor.blockIndex,
                reflowAnchorCharStart = currentReadingAnchor.charStart,
                pageRotations = pageRotations.toMap(),
                spreadSplit = pdfState.spreadSplit,
                spreadViewOverride = pdfState.spreadViewOverride,
            )
        }.drop(1)
            .distinctUntilChanged()
            .debounce(PANEL_AUTOSAVE_DEBOUNCE)
            .collect { view ->
                annotationRepository.saveViewState(filePath, view).onFailure { e ->
                    panelLogger.warn { "View-state save failed for $filePath: ${e::class.simpleName}" }
                }
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
    // Снаппинг «липкого маркера»: завершённый маркер-штрих превращаем в выделение по словам
    // под ним и убираем сам штрих. true — штрих стал выделением; false — обычный путь
    // (не маркер / не липкий / нет текстового кэша / под штрихом нет текста).
    val trySnapStroke =
        remember(pdfState, drawingStates) {
            fun(
                pageIndex: Int,
                path: DrawingPath,
            ): Boolean {
                val doc = reflowDocCacheState.value
                val state = drawingStates[pageIndex]
                val markerSticky = path.toolType == ToolKind.MARKER && markerSettingsProvider.value.sticky
                val rects =
                    if (markerSticky && doc != null && state != null) {
                        StrokeTextMapper.snapToWords(doc, pageIndex, path)
                    } else {
                        emptyList()
                    }
                if (rects.isEmpty() || state == null) return false
                val lastIndex = state.currentPaths.lastIndex
                if (lastIndex >= 0) state.currentPaths.removeAt(lastIndex)
                pdfState.highlights[pageIndex] =
                    pdfState.highlights[pageIndex].orEmpty() +
                    StickyHighlight(rects = rects, colorArgb = path.colorArgb)
                state.markHistoryChanged()
                return true
            }
        }
    val drawingController =
        remember(pdfViewerState, drawingStates, magnifierState) {
            MultiPageDrawingController(
                drawingStates = drawingStates,
                geometry = pdfViewerState.asPageLayoutGeometry(),
                toolMode = { toolModeProvider.value },
                penSettings = { penSettingsProvider.value },
                markerSettings = { markerSettingsProvider.value },
                eraserSettings = { eraserSettingsProvider.value },
                eraserOverride = { eraserOverrideProvider.value },
                onGestureStart = { pageIndex, snapshot -> pdfState.pushUndoSnapshot(pageIndex, snapshot) },
                onStrokeFinished = { pageIndex, path ->
                    if (!trySnapStroke(pageIndex, path)) {
                        val state = drawingStates[pageIndex] ?: return@MultiPageDrawingController
                        handlePanelStrokeFinished(state, pageIndex, path, syncEngineProvider.value)
                    }
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
    val magnifierTargetGestureController =
        remember(pdfViewerState, magnifierState) {
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
        pinnedRect.value =
            when (magnifierState.attachment) {
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
    val loupeSelectionController =
        remember(pdfViewerState, magnifierState) {
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
    // Окно-origin gesture-узла viewer'а (его левый-верхний угол в координатах
    // окна Compose). Нативный pen-stream (`WindowsPointerHook`) репортит позиции
    // в координатах окна, а Compose pointerInput viewer'а — локально к этому
    // узлу (смещён вниз на полосу вкладок / reading-инсет). Этим origin'ом
    // нативные pen-координаты приводятся в ту же viewport-систему, что у мыши
    // и `pdfViewerState.pan/zoom`, — иначе перо и рамка лупы расходятся.
    val viewerOriginInWindow = remember { mutableStateOf(Offset.Zero) }
    val openTriggerProvider = rememberUpdatedState(isOpenTriggerActive)
    val magnifierInputControllerHolder =
        remember(pdfState) {
            mutableStateOf<ru.kyamshanov.notepen.magnifier.MagnifierInputController?>(null)
        }

    fun routedOnDown(
        viewportPos: Offset,
        pressure: Float,
        tilt: Float,
    ) {
        if (magnifierState.enabled) {
            // `contentBoundsInViewport` хранится в координатах окна Compose
            // (панель репортит `boundsInWindow()`), а `viewportPos` — локально к
            // gesture-узлу viewer'а, смещённому вниз на полосу вкладок /
            // reading-инсет. Поднимаем позицию в координаты окна на origin узла,
            // иначе hit-тест «внутри панели?» промахивается и рисование/ластик/
            // выделение вне панели переставали работать на desktop.
            val panelLocal =
                ru.kyamshanov.notepen.magnifier
                    .viewportToPanelLocal(magnifierState, viewportPos + viewerOriginInWindow.value)
            val mc = magnifierInputControllerHolder.value
            if (panelLocal != null && mc != null) {
                // `panelLocal` измерена относительно `contentBoundsInViewport`
                // (фактический прямоугольник content-области панели), поэтому
                // контроллеру передаём ровно её размер — тот же reference, что
                // и у touch-ветки (`size` Canvas'а) и у рендера. Если передать
                // логический `panelSize`, делитель и измеренная координата
                // относятся к разным reference'ам, и при ресайзе ввод уезжает
                // относительно содержимого по обеим осям.
                mc.onDown(panelLocal, magnifierState.contentBoundsInViewport.size, pressure, tilt)
                gestureRoute.value = PanelGestureRoute.MAGNIFIER
                return
            }
            if (magnifierTargetGestureController.onDown(viewportPos)) {
                gestureRoute.value = PanelGestureRoute.TARGET_RECT
                return
            }
            // Жест начат на странице вне панели/рамки: при зажатом open-триггере
            // (или armed quick-loupe) это переселекция новой области, иначе —
            // обычное рисование. Без этой ветки повторное выделение при уже
            // включённой лупе было недостижимо (ранний return выше).
            if (openTriggerProvider.value || quickLoupeArmed.value) {
                loupeSelectionController.onDown(viewportPos)
                gestureRoute.value = PanelGestureRoute.LOUPE
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

    fun routedOnMove(
        viewportPos: Offset,
        pressure: Float,
        tilt: Float,
    ) {
        when (gestureRoute.value) {
            PanelGestureRoute.LOUPE -> loupeSelectionController.onMove(viewportPos)
            PanelGestureRoute.DRAWING -> drawingController.onMove(viewportPos, pressure, tilt)
            PanelGestureRoute.MAGNIFIER -> {
                // См. routedOnDown: позиция поднимается в координаты окна на
                // origin узла, чтобы совпасть с window-space contentBounds.
                val panelLocal =
                    ru.kyamshanov.notepen.magnifier
                        .viewportToPanelLocal(magnifierState, viewportPos + viewerOriginInWindow.value)
                val mc = magnifierInputControllerHolder.value
                // Тот же content-bounds reference, что и в `routedOnDown`.
                if (panelLocal != null && mc != null) {
                    mc.onMove(panelLocal, magnifierState.contentBoundsInViewport.size, pressure, tilt)
                }
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
            PanelGestureRoute.MAGNIFIER ->
                magnifierInputControllerHolder.value?.onUp(magnifierState.contentBoundsInViewport.size)
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
                // Нативные pen-координаты приходят в координатах окна; приводим
                // их в локальную viewport-систему gesture-узла (как у мыши).
                PenPointerEventType.DOWN ->
                    routedOnDown(ev.position - viewerOriginInWindow.value, ev.pressure, ev.tilt)
                PenPointerEventType.UPDATE ->
                    routedOnMove(ev.position - viewerOriginInWindow.value, ev.pressure, ev.tilt)
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
            val pageIdx =
                when {
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
            val centerN =
                Offset(
                    x = (viewportCenterDocX / basePageW.coerceAtLeast(1f)).coerceIn(0f, 1f),
                    y = ((viewportCenterDocY - pageTop) / pdfH.coerceAtLeast(1f)).coerceIn(0f, 1f),
                )
            magnifierState.enable(
                onPage = pageIdx,
                viewportSize = Size(viewportW, viewportH),
                targetCenterOnPage = centerN,
                // Открываем панель правее тулрейла (а не поверх него): инсет уже
                // включает ширину рейла/сайдбара + зазор.
                startInsetPx = pdfViewerState.fitWidthInsetStartPx,
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
        pdfViewerState.scrollMode =
            when (pdfViewerState.scrollMode) {
                ScrollMode.BOTH -> ScrollMode.VERTICAL
                ScrollMode.VERTICAL -> ScrollMode.NONE
                ScrollMode.NONE -> ScrollMode.BOTH
            }
    }
    // Поворот текущей (первой видимой) страницы на +90° CW, кумулятивно. В режиме
    // чтения — no-op (reflow это поток текста, поворот к нему неприменим). Штрихи и
    // выделения страницы поворачиваются вместе с растром; персист — через autosave
    // pageRotations. Толщину штриха корректируем по aspect ДО поворота.
    val onRotateCurrentPage: () -> Unit = rotate@{
        if (pdfState.readingMode) return@rotate
        val idx = pdfViewerState.firstVisiblePageIndex
        if (idx !in pdfState.pages.indices) return@rotate
        val aspectBefore = pages.getOrNull(idx)?.aspectRatio?.takeIf { it > 0f } ?: 1f
        pdfState.rotatePageClockwise(pageIndex = idx, pageAspectBeforeRotation = aspectBefore)
    }
    // Переключает разделение разворотов (FEATURE #4). В режиме чтения — no-op
    // (reflow это поток текста; вырезка половины страницы к нему неприменима).
    // Перед переключением запоминаем исходную страницу, чтобы прокрутка осталась
    // примерно на том же содержимом после удвоения/сжатия индекс-пространства.
    val onToggleSpreadSplit: () -> Unit = split@{
        if (pdfState.readingMode) return@split
        val enabling = !pdfState.spreadSplit
        val srcPage =
            if (pdfState.spreadSplit) {
                ru.kyamshanov.notepen.annotation.domain.model.SpreadSplit
                    .sourceIndexOf(pdfViewerState.firstVisiblePageIndex)
            } else {
                pdfViewerState.firstVisiblePageIndex
            }
        pdfState.toggleSpreadSplit()
        val targetLogical =
            if (enabling) {
                ru.kyamshanov.notepen.annotation.domain.model.SpreadSplit.leftLogical(srcPage)
            } else {
                srcPage
            }
        // Перецентровка применится после пере-layout'а (смена [pages]) на уже
        // свежем layout'е — не на старом, как делал scrollToPage, из-за чего
        // страница уезжала вбок и прыгала по вертикали. Каждая половина занимает
        // целую колонку, поэтому при ВКЛючении компенсируем масштаб ×0.5 (иначе
        // половина выглядела бы вдвое крупнее своей части в целой странице), а при
        // ВЫКЛючении — ×2 (обратно). Так разделение не «масштабирует на весь экран».
        pdfViewerState.requestRecenter(targetLogical, if (enabling) 0.5f else 2f)
    }
    // Переключает книжный разворот «Две страницы» (FEATURE #5) как ЯВНЫЙ выбор
    // пользователя — он перекрывает авто-по-ширине. В режиме чтения — no-op
    // (reflow это одноколоночный текст; разворот к нему неприменим — это и
    // обеспечивает отличие от режима чтения). Запоминаем текущую страницу, чтобы
    // пере-садить на левую страницу её пары после смены раскладки.
    val onToggleBookSpread: () -> Unit = bookSpread@{
        if (pdfState.readingMode) return@bookSpread
        val current = pdfViewerState.firstVisiblePageIndex
        // Явный выбор = инверсия ТЕКУЩЕГО эффективного состояния, чтобы один тап
        // всегда видимо переключал (даже если авто уже включило разворот).
        pdfState.spreadViewOverride = !bookSpreadEnabled
        // Перецентровка с СОХРАНЕНИЕМ масштаба применится после пере-layout'а
        // (смена [spreadMode]) на свежем layout'е: ряд (пара страниц) встаёт по
        // центру по горизонтали, верх страницы пары прижимается к верху вьюпорта,
        // а масштаб не меняется — разворот не «масштабирует на весь экран».
        pdfViewerState.requestRecenter(current)
    }

    // В режиме чтения с тапом-скрытым хромом панель с единственной вкладкой прячет и
    // полосу вкладок: одиночная вкладка не несёт навигационной ценности, а при >1
    // вкладке полосу оставляем, чтобы можно было переключаться. Per-panel — каждая
    // панель владеет своей полосой (см. оба сайта ниже: резерв места и сам TabBar).
    val tabStripHidden = pdfState.readingMode && !pdfState.readerBarVisible && openDocs.tabs.size == 1

    if (isFocused) {
        // Read showThumbnails here in the composition (not only inside SideEffect)
        // so the composition subscribes to it: a SideEffect block's state reads do
        // not register a recomposition dependency, so without this read toggling
        // thumbnails would never re-run the effect nor republish PanelControls.
        val thumbnailsVisible = showThumbnails
        val tocVisible = showToc
        val readingModeVisible = pdfState.readingMode
        // Read readerBarVisible in composition so the published read-out tracks it.
        // NB: chrome visibility itself is derived in DetailsContent straight from the
        // focused tab's PdfDocumentState, not from this relayed value (see chromeHidden).
        val readerBarVisible = pdfState.readerBarVisible
        // Read scrollMode in composition (not only inside SideEffect) so the
        // composition subscribes to it — toggling it republishes PanelControls.
        val currentScrollMode = pdfViewerState.scrollMode
        // Read the outline here so a republish fires when it loads asynchronously
        // (the TOC button hides until then).
        val tocAvailable = pdfState.outline.isNotEmpty()
        // Read spreadSplit in composition so toggling it republishes PanelControls
        // (the toggle button's selected state tracks it).
        val spreadSplitVisible = pdfState.spreadSplit
        // Read live-sync state in composition so flipping the toggle republishes
        // PanelControls and the toolbar's selected state tracks it.
        val liveSyncVisible = liveSyncEnabled
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
                    showToc = tocVisible,
                    hasToc = tocAvailable,
                    readingModeEnabled = readingModeVisible,
                    readingModeAvailable = readingModeAvailable,
                    chromeHidden = readingModeVisible && !readerBarVisible,
                    quickLoupeArmed = quickLoupeArmed.value,
                    scrollMode = currentScrollMode,
                    zoomIn = onZoomIn,
                    zoomOut = onZoomOut,
                    toggleMagnifier = onMagnifierToggle,
                    export = onExport,
                    toggleThumbnails = {
                        showThumbnails = !showThumbnails
                        if (showThumbnails) showToc = false
                    },
                    toggleToc = {
                        showToc = !showToc
                        if (showToc) showThumbnails = false
                    },
                    toggleReadingMode = { pdfState.readingMode = !pdfState.readingMode },
                    navigateToPage = { page ->
                        if (pdfState.readingMode) {
                            reflowReading?.let { reading ->
                                ReflowPageLocator.blockIndexForPage(reading.document, page)
                                    ?.let { reflowNavigateToBlock.value = it }
                            }
                        } else {
                            pdfViewerState.scrollToPage(page, 0)
                        }
                    },
                    readerPageDelta = { delta -> reflowPageDelta.value?.invoke(delta) },
                    toggleQuickLoupe = onToggleQuickLoupe,
                    cycleScrollMode = onCycleScrollMode,
                    rotateCurrentPage = onRotateCurrentPage,
                    spreadSplitEnabled = spreadSplitVisible,
                    toggleSpreadSplit = onToggleSpreadSplit,
                    bookSpreadEnabled = bookSpreadEnabled,
                    toggleBookSpread = onToggleBookSpread,
                    liveSyncAvailable = liveSyncController != null,
                    liveSyncEnabled = liveSyncVisible,
                    toggleLiveSync = {
                        // Запоминаем явную ручную паузу: если выключаем включённый
                        // синк — больше не авто-включаем при переподключении пира.
                        userPausedSync = liveSyncEnabled
                        liveSyncController?.toggle(documentId)
                    },
                ),
            )
        }
    }

    // ---- UI ---------------------------------------------------------------
    // Вне режима чтения резервируем место под TabBar (TabBar всегда виден, контент =
    // PDF-страница; если бы PDF заходил под полосу вкладок, верхний край страницы
    // визуально лез бы под чипы — путаница).
    //
    // В режиме чтения reserve = 0: текст занимает экран целиком, TabBar (он сам по
    // себе overlay, см. AnimatedVisibility ниже) рисуется поверх верхней кромки.
    // Так больше строк помещается, тап для скрытия/показа настроек НЕ двигает текст
    // (высота вьюпорта стабильна — в paged-режиме нет дорогой репагинации книги).
    // Чтобы текст не «просвечивал» сквозь стекло TabBar в multi-tab reading mode,
    // TabBar в этом режиме рисуется почти непрозрачно (см. fillAlpha ниже).
    val tabStripReserve = if (pdfState.readingMode) 0.dp else TAB_BAR_HEIGHT
    // Под (скрываемой) полосой вкладок поле красим фоном темы ридера, чтобы в режиме чтения
    // оно не мигало системным фоном на тёмных темах.
    val readingChromeBg =
        if (pdfState.readingMode && readerStoredLoaded) {
            readerStored.current.toRenderSettings().background
        } else {
            Color.Transparent
        }
    Box(modifier.fillMaxSize().onSizeChanged { panelSizePx = it }) {
        Box(Modifier.fillMaxSize().background(readingChromeBg).padding(top = tabStripReserve)) {
            PdfPagesViewer(
                state = pdfViewerState,
                pdfDocument = pdfDocument,
                pages = pages,
                renderer = renderer,
                userRotationQuarters = userRotationOf,
                pageSource = pageSourceOf,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .glassSource()
                        .onGloballyPositioned { viewerOriginInWindow.value = it.positionInWindow() }
                        .stylusEventSink(tabletController)
                        .pointerHoverIcon(if (toolMode == ToolMode.NONE) PointerIcon.Hand else PointerIcon.Default),
                primaryDragPanEnabled = { pos ->
                    // Палец/указатель свободен для панорамирования, когда он не
                    // является инструментом рисования: либо инструмент неактивен,
                    // либо включён режим стилуса (рисует только перо) — как в
                    // ноут-апах, где палец скроллит, а стилус рисует.
                    //
                    // Но если нажатие попало на рамку-цель лупы — pan отклоняем:
                    // drag должен двигать/ресайзить рамку (TARGET_RECT в
                    // `routedOnDown`), а не панорамировать страницу из-под неё. На
                    // десктопе pan-обработчик ловит Press на Initial-проходе раньше
                    // внутреннего drag-роутера, поэтому без этой проверки страница
                    // перетаскивалась вместо рамки.
                    (toolModeProvider.value == ToolMode.NONE || pencilModeProvider.value) &&
                        !quickLoupeArmed.value &&
                        !openTriggerProvider.value &&
                        !(
                            magnifierState.enabled &&
                                magnifierTargetGestureController.hitTest(pos) !=
                                ru.kyamshanov.notepen.magnifier.MagnifierTargetGestureController.Mode.NONE
                        )
                },
                gestureModifier =
                    Modifier.pdfMultiPageDrawingInput(
                        key = drawingController,
                        tablet = tabletController,
                        palmRejectionActive = palmRejectionActive,
                        captureGesture = { pos ->
                            quickLoupeArmed.value ||
                                openTriggerProvider.value ||
                                (
                                    magnifierState.enabled &&
                                        magnifierTargetGestureController.hitTest(pos) !=
                                        ru.kyamshanov.notepen.magnifier.MagnifierTargetGestureController.Mode.NONE
                                ) ||
                                (
                                    !pencilModeProvider.value &&
                                        toolModeProvider.value != ToolMode.NONE &&
                                        drawingController.isInsidePdfPage(pos)
                                )
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
                        val pdfDrawingState =
                            remember(pageIndex) {
                                drawingStates.getOrPut(pageIndex) { PdfDrawingState() }
                            }
                        val isMagnifierPage =
                            magnifierState.enabled &&
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
                            highlights = pdfState.highlights[pageIndex].orEmpty(),
                            notes = pdfState.notes[pageIndex].orEmpty(),
                            onNoteTapped = { note -> editingNote = note },
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
                            modifier =
                                Modifier
                                    .size(width = pdfWidth, height = pdfHeight)
                                    .border(
                                        width =
                                            androidx.compose.ui.unit
                                                .Dp(0.5f),
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
                    val stroke =
                        androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 2f,
                            pathEffect =
                                androidx.compose.ui.graphics.PathEffect
                                    .dashPathEffect(floatArrayOf(12f, 8f)),
                        )
                    drawRect(
                        color =
                            androidx.compose.ui.graphics
                                .Color(30, 136, 229),
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
                    modifier =
                        Modifier
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
                val magPdfDrawingStateProvider: (Int) -> PdfDrawingState =
                    remember(drawingStates) {
                        { pageIdx -> drawingStates.getOrPut(pageIdx) { PdfDrawingState() } }
                    }
                val magOnGestureStart: (Int, List<DrawingPath>) -> Unit =
                    remember(pdfState) {
                        { pageIdx, snapshot -> pdfState.pushUndoSnapshot(pageIdx, snapshot) }
                    }
                val syncEngineRef = rememberUpdatedState(syncEngine)
                val magOnStrokeFinished: (Int, DrawingPath) -> Unit =
                    remember(drawingStates) {
                        { pageIdx, path ->
                            if (!trySnapStroke(pageIdx, path)) {
                                drawingStates[pageIdx]?.let {
                                    handlePanelStrokeFinished(it, pageIdx, path, syncEngineRef.value)
                                }
                            }
                        }
                    }
                val magOnEraseFinished: (Int, List<DrawingPath>, List<DrawingPath>) -> Unit =
                    remember(drawingStates) {
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
                val magnifierInputController =
                    remember(magnifierState) {
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

                // Панель лупы — плавающее окно поверх всего хрома: рендерим в
                // Popup, иначе она оказывается под левым тулрейлом (тулрейл —
                // sibling-Box в `DetailsContent`, отрисованный после grid'а, и
                // перекрывает панель). Popup-слой рисуется над оконным контентом.
                // На десктопе Popup живёт в том же окне/scene, поэтому
                // `boundsInWindow()` панели (→ contentBoundsInViewport) и
                // маршрутизация нативного пера не меняются. Позиционирование —
                // через offset Popup'а (panelTopLeft, viewport-px); не focusable,
                // чтобы Popup не перехватывал клавиатуру/шорткаты.
                Popup(
                    alignment = Alignment.TopStart,
                    offset =
                        IntOffset(
                            magnifierState.panelTopLeft.x.toInt(),
                            magnifierState.panelTopLeft.y.toInt(),
                        ),
                    properties = PopupProperties(focusable = false),
                ) {
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
            }

            if (pdfState.readingMode) {
                val reading = reflowReading
                if (reading != null) {
                    // Подсветки для ридера — реактивно из текущего состояния (штрихи +
                    // липкие выделения), чтобы созданное прямо в чтении показывалось сразу.
                    // Маппинг штрих/подсветка → анкеры — геометрически дорогой хит-тест по
                    // документу, поэтому: (1) штрихи и подсветки считаем РАЗДЕЛЬНО, чтобы
                    // добавление подсветки не пересчитывало анкеры штрихов; (2) анкеры каждой
                    // подсветки кэшируем по её значению (она привязана к странице и неизменна).
                    // Иначе каждое новое выделение пересчитывало бы анкеры всех предыдущих —
                    // после выделения копился бы лаг, а следующее «подвисало». Кэш живёт на документ.
                    val strokeAnchors by remember(reading) {
                        derivedStateOf {
                            val doc = reading.document
                            drawingStates.entries.flatMap { (page, st) ->
                                st.currentPaths.flatMap { StrokeTextMapper.anchorsFor(doc, page, it) }
                            }
                        }
                    }
                    val highlightAnchorCache = remember(reading) { mutableMapOf<StickyHighlight, List<TextAnchor>>() }
                    val highlightAnchors by remember(reading) {
                        derivedStateOf {
                            val doc = reading.document
                            pdfState.highlights.entries.flatMap { (page, hs) ->
                                hs.flatMap { h ->
                                    highlightAnchorCache.getOrPut(h) {
                                        StrokeTextMapper.anchorsForRects(doc, page, h.rects)
                                    }
                                }
                            }
                        }
                    }
                    // Заметки тоже геометрически привязаны к страницам — маппим их анкеры
                    // тем же путём, что подсветки (anchorsForNote → геометрия, с фолбэком на
                    // quote), и кэшируем по значению заметки. Складываем в readerHighlights,
                    // чтобы диапазон заметки красился «бесплатно» через тот же highlight-слой;
                    // ReflowReader дополнительно рисует тап-бейдж на полях.
                    val noteAnchorCache = remember(reading) { mutableMapOf<PageNote, List<TextAnchor>>() }
                    val noteAnchors by remember(reading) {
                        derivedStateOf {
                            val doc = reading.document
                            pdfState.notes.values.flatMap { ns ->
                                ns.flatMap { n ->
                                    noteAnchorCache.getOrPut(n) { StrokeTextMapper.anchorsForNote(doc, n) }
                                }
                            }
                        }
                    }
                    val readerHighlights by remember(reading) {
                        derivedStateOf { strokeAnchors + highlightAnchors + noteAnchors }
                    }
                    // Сквозное выделение отдаёт анкеры по всем покрытым блокам сразу; маппим их
                    // в геометрию по страницам (selectionRectsByPage уже сливает по строкам) и
                    // на каждую затронутую страницу добавляем StickyHighlight цвета маркера. Один
                    // pushUndoSnapshot на страницу (до правки её подсветок) — откат жеста по шагам.
                    // Инструмент «заметка» (доступен только в чтении): текстовое выделение
                    // создаёт [PageNote] вместо липкого выделения. Owner — тулбар выше
                    // ([DetailsContent]); здесь выводим из активного [toolMode].
                    val noteToolActive = toolMode == ToolMode.NOTE
                    val createHighlight: (List<TextAnchor>) -> Unit = { anchors ->
                        if (noteToolActive) {
                            // Заметку рождаем из выделения: проецируем анкеры обратно в page-rects
                            // (источник истины) и сохраняем quote+context как фолбэк ре-анкера.
                            val (quote, context) = StrokeTextMapper.quoteForAnchors(reading.document, anchors)
                            StrokeTextMapper.selectionRectsByPage(reading.document, anchors)
                                .forEach { (page, rects) ->
                                    if (rects.isNotEmpty()) {
                                        pdfState.pushUndoSnapshot(
                                            page,
                                            drawingStates[page]?.currentPaths?.toList() ?: emptyList(),
                                        )
                                        val created =
                                            handlePanelNoteCreated(
                                                notesState = pdfState.notes,
                                                pageIndex = page,
                                                rects = rects,
                                                quote = quote,
                                                context = context,
                                                colorArgb = markerSettings.colorArgb,
                                                nowMillis = 0L,
                                                engine = syncEngineProvider.value,
                                            )
                                        if (created != null) editingNote = created
                                    }
                                }
                        } else {
                            StrokeTextMapper.selectionRectsByPage(reading.document, anchors)
                                .forEach { (page, rects) ->
                                    if (rects.isNotEmpty()) {
                                        pdfState.pushUndoSnapshot(
                                            page,
                                            drawingStates[page]?.currentPaths?.toList() ?: emptyList(),
                                        )
                                        pdfState.highlights[page] =
                                            pdfState.highlights[page].orEmpty() +
                                            StickyHighlight(rects = rects, colorArgb = markerSettings.colorArgb)
                                    }
                                }
                        }
                    }
                    CompositionLocalProvider(
                        LocalReflowSelection provides
                            ReflowSelection(
                                immediate = toolMode == ToolMode.MARKER || noteToolActive,
                                onCreate = createHighlight,
                            ),
                        LocalReflowLayoutCache provides reflowLayoutCache,
                    ) {
                        ReflowReader(
                            document = reading.document,
                            stored = readerStored,
                            onStoredChange = onReaderStoredChange,
                            newPresetIdProvider = { generateUuid() },
                            barVisible = isFocused && pdfState.readerBarVisible,
                            onBarVisibleChange = { visible ->
                                if (isFocused) pdfState.readerBarVisible = visible
                            },
                            modifier = Modifier.fillMaxSize(),
                            highlights = readerHighlights,
                            notes = reading.notes,
                            onNoteTap = { note -> editingNote = note },
                            listState = reflowListState,
                            renderPage = renderFigurePage,
                            onPageDeltaReady = { reflowPageDelta.value = it },
                            navigateToBlock = reflowNavigateToBlock,
                            onFirstBlockChange = { block ->
                                reflowReading?.let { reading ->
                                    ReflowPageLocator.pageForBlock(reading.document, block)
                                        ?.let { page -> pdfViewerState.scrollToPage(page, 0) }
                                }
                            },
                            initialAnchor = currentReadingAnchor,
                            onReadingAnchorChange = { currentReadingAnchor = it },
                            // Статический резерв под плавающий хром редактора (верхний
                            // бар/чип «Страница N/M» и боковой tool-rail), чтобы текст не
                            // уходил под него (Defect C). Те же инсеты, что использует PDF-путь.
                            topInset = fitWidthTopInset,
                            startInset = fitWidthStartInset,
                        )
                    }
                } else {
                    // До завершения первой загрузки настроек ридера не угадываем тему —
                    // показываем нейтральный MaterialTheme, чтобы плейсхолдер не прыгал
                    // с дефолтной темы ридера в сохранённую, когда I/O resolved.
                    val readerRender = if (readerStoredLoaded) readerStored.current.toRenderSettings() else null
                    val placeholderBg = readerRender?.background ?: MaterialTheme.colorScheme.surface
                    val placeholderText = readerRender?.textColor ?: MaterialTheme.colorScheme.onSurface
                    Box(Modifier.fillMaxSize().background(placeholderBg)) {
                        Text(
                            text = "Готовим режим чтения…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = placeholderText,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            } else if (pdfDocument == null && pages.isEmpty() && pdfState.isPdfLoading) {
                // Открытие EPUB сперва конвертирует книгу в PDF и растеризует первую
                // страницу — пока документ грузится, PdfPagesViewer рисует пустой
                // SubcomposeLayout. Без индикатора пользователь видит лишь пустой фон
                // и счётчик «1 / 0» (Defect H). Показываем центрированный спиннер поверх
                // readingChromeBg. В режиме чтения этим занимается ветка-плейсхолдер выше.
                Box(Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(PREPARING_INDICATOR_SPACING),
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "Открываем книгу…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = !tabStripHidden,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
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
                sessionsMenu = sessionsMenu,
                // В режиме чтения красим полосу вкладок под фон активной темы ридера,
                // чтобы хром сливался с ридером (как и остальной хром этой панели), а
                // подписи/иконки — под цвет текста темы, иначе на тёмной теме они
                // остаются тёмными и нечитаемыми.
                tint =
                    if (pdfState.readingMode && readerStoredLoaded) {
                        readerStored.current.toRenderSettings().background
                    } else {
                        null
                    },
                contentColor =
                    if (pdfState.readingMode && readerStoredLoaded) {
                        readerStored.current.toRenderSettings().textColor
                    } else {
                        null
                    },
            )
        }

        // Поповер редактирования/удаления тела заметки — общий для редактора (тап по
        // бейджу) и ридера (тап по маркеру заметки). Центрируется, без привязки к бейджу.
        editingNote?.let { note ->
            NoteBodyPopover(
                note = note,
                onDismiss = { editingNote = null },
                onSave = { body ->
                    pdfState.pushUndoSnapshot(
                        note.pageIndex,
                        pdfState.drawingStates[note.pageIndex]?.currentPaths?.toList() ?: emptyList(),
                    )
                    handlePanelNoteEdited(pdfState.notes, note, body, 0L, syncEngineProvider.value)
                    editingNote = null
                },
                onDelete = {
                    pdfState.pushUndoSnapshot(
                        note.pageIndex,
                        pdfState.drawingStates[note.pageIndex]?.currentPaths?.toList() ?: emptyList(),
                    )
                    handlePanelNoteRemoved(pdfState.notes, note, syncEngineProvider.value)
                    editingNote = null
                },
            )
        }
    }
}

/**
 * Centred popover editing the [PageNote.body] of [note]. Save persists the new body
 * (a fresh NoteUpserted via the editor glue); Delete tombstones the note. Dismiss
 * closes without changes.
 */
@Composable
private fun NoteBodyPopover(
    note: PageNote,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var body by remember(note.noteId) { mutableStateOf(note.body) }
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            tonalElevation = NOTE_POPOVER_ELEVATION,
            shadowElevation = NOTE_POPOVER_ELEVATION,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.width(NOTE_POPOVER_WIDTH),
        ) {
            Column(
                modifier = Modifier.padding(NOTE_POPOVER_PADDING),
                verticalArrangement = Arrangement.spacedBy(NOTE_POPOVER_PADDING),
            ) {
                if (note.quote.isNotEmpty()) {
                    Text(
                        text = note.quote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Заметка") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(NOTE_POPOVER_PADDING),
                ) {
                    TextButton(onClick = onDelete) { Text("Удалить") }
                    Box(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Отмена") }
                    TextButton(onClick = { onSave(body) }) { Text("Сохранить") }
                }
            }
        }
    }
}

/**
 * Mints a [PageNote] from a reader selection (rects = source of truth, quote/context
 * = re-anchor fallback), inserts it into [notesState], and publishes a
 * [StrokeDelta.NoteUpserted] (LWW like Added; the engine stamps the real clock).
 * Returns the created note, or `null` when there is nothing to create / no engine.
 */
private fun handlePanelNoteCreated(
    notesState: SnapshotStateMap<Int, List<PageNote>>,
    pageIndex: Int,
    rects: List<NormalizedRect>,
    quote: String,
    context: String,
    colorArgb: Long,
    nowMillis: Long,
    engine: SyncEngine?,
): PageNote? {
    if (rects.isEmpty() || engine == null) return null
    val id = engine.newStrokeId()
    val note =
        PageNote(
            noteId = id,
            rects = rects,
            pageIndex = pageIndex,
            quote = quote,
            context = context,
            body = "",
            colorArgb = colorArgb,
            createdAt = nowMillis,
            updatedAt = nowMillis,
        )
    notesState[pageIndex] = notesState[pageIndex].orEmpty() + note
    engine.applyLocal(
        StrokeDelta.NoteUpserted(
            strokeId = id,
            pageIndex = pageIndex,
            authorDeviceId = engine.deviceId,
            clock = 0,
            note = note.toDto(),
        ),
    )
    return note
}

/**
 * Edits a note's [PageNote.body]: replaces it in [notesState] and publishes a new
 * [StrokeDelta.NoteUpserted] with a higher clock (whole-note LWW).
 */
private fun handlePanelNoteEdited(
    notesState: SnapshotStateMap<Int, List<PageNote>>,
    note: PageNote,
    newBody: String,
    nowMillis: Long,
    engine: SyncEngine?,
) {
    if (engine == null) return
    val updated = note.copy(body = newBody, updatedAt = nowMillis)
    notesState[note.pageIndex] =
        notesState[note.pageIndex].orEmpty().map { if (it.noteId == note.noteId) updated else it }
    engine.applyLocal(
        StrokeDelta.NoteUpserted(
            strokeId = updated.noteId,
            pageIndex = updated.pageIndex,
            authorDeviceId = engine.deviceId,
            clock = 0,
            note = updated.toDto(),
        ),
    )
}

/** Removes a note locally and publishes a [StrokeDelta.NoteRemoved] tombstone. */
private fun handlePanelNoteRemoved(
    notesState: SnapshotStateMap<Int, List<PageNote>>,
    note: PageNote,
    engine: SyncEngine?,
) {
    if (engine == null) return
    notesState[note.pageIndex] =
        notesState[note.pageIndex].orEmpty().filterNot { it.noteId == note.noteId }
    engine.applyLocal(
        StrokeDelta.NoteRemoved(
            strokeId = note.noteId,
            pageIndex = note.pageIndex,
            authorDeviceId = engine.deviceId,
            clock = 0,
        ),
    )
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
    val batch =
        buildList {
            for (id in removedOrModified) {
                add(
                    StrokeDelta.Removed(strokeId = id, pageIndex = pageIndex, authorDeviceId = engine.deviceId, clock = 0),
                )
            }
            for ((idx, p) in newAdded.withIndex()) {
                add(
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
        }
    engine.applyLocalBatch(batch)
}
