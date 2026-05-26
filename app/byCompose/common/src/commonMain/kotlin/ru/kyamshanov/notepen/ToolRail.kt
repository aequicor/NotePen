package ru.kyamshanov.notepen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ru.kyamshanov.notepen.annotation.domain.model.EraserSettings
import ru.kyamshanov.notepen.annotation.domain.model.MarkerSettings
import ru.kyamshanov.notepen.annotation.domain.model.PenSettings
import ru.kyamshanov.notepen.annotation.domain.model.StoredToolPresets
import ru.kyamshanov.notepen.ui.glass.GlassSurface
import kotlin.math.roundToInt

/**
 * Tool toggles (Pen / Marker / Eraser) as a list of [WheelEntry] for the unified
 * tool wheel. Re-tapping the active tool deactivates it — see [nextToolModeOnToggle].
 *
 * @param includePen when `false`, the pen/brush toggle is omitted (the marker and
 *   eraser remain). Reading mode passes `false`: free-hand drawing is meaningless
 *   on reflowed text, but highlighting (marker) and erasing it stay available.
 */
internal fun toolSelectorEntries(
    toolMode: ToolMode,
    onToolModeChange: (ToolMode) -> Unit,
    includePen: Boolean = true,
): List<WheelEntry> =
    buildList {
        if (includePen) {
            add(
                WheelEntry(TOOL_PEN_KEY) {
                    ToolToggleButton(
                        icon = NotePenIcons.Brush,
                        contentDescription = "Перо",
                        selected = toolMode == ToolMode.PEN,
                        onClick = { onToolModeChange(nextToolModeOnToggle(toolMode, ToolMode.PEN)) },
                        showSelectionBackground = false,
                    )
                },
            )
        }
        add(
            WheelEntry(TOOL_MARKER_KEY) {
                ToolToggleButton(
                    icon = NotePenIcons.Highlighter,
                    contentDescription = "Маркер",
                    selected = toolMode == ToolMode.MARKER,
                    onClick = { onToolModeChange(nextToolModeOnToggle(toolMode, ToolMode.MARKER)) },
                    showSelectionBackground = false,
                )
            },
        )
        add(
            WheelEntry(TOOL_ERASER_KEY) {
                ToolToggleButton(
                    icon = NotePenIcons.Eraser,
                    contentDescription = "Ластик",
                    selected = toolMode == ToolMode.ERASER,
                    onClick = { onToolModeChange(nextToolModeOnToggle(toolMode, ToolMode.ERASER)) },
                    showSelectionBackground = false,
                )
            },
        )
    }

/** Wheel-entry key of the currently selected drawing tool, or `null` for [ToolMode.NONE]. */
internal fun selectedToolWheelKey(toolMode: ToolMode): Any? =
    when (toolMode) {
        ToolMode.PEN -> TOOL_PEN_KEY
        ToolMode.MARKER -> TOOL_MARKER_KEY
        ToolMode.ERASER -> TOOL_ERASER_KEY
        ToolMode.NONE -> null
    }

/**
 * В режиме чтения перекрашивает «слоты выбора» колеса инструментов под тему
 * читалки: [ColorScheme.primaryContainer] — заливка индикатора активной кнопки
 * (и общей «таблетки» пера/маркера/ластика в [WheelStrip]),
 * [ColorScheme.onPrimaryContainer] — значок активной кнопки. Так выбранный
 * инструмент совпадает с выбранной темой ридера, а не с акцентом приложения.
 *
 * Цвета повторяют визуальный язык выбора панели ридера (выбранный чип): заливка
 * [readerContentColor] с прозрачностью [READER_SELECTION_FILL_ALPHA] и значок
 * полной насыщенности. Вне режима чтения ([readerContentColor] `null`) возвращает
 * [base] без изменений.
 */
internal fun railSelectionColorScheme(
    base: ColorScheme,
    readerContentColor: Color?,
    readerBackground: Color? = null,
): ColorScheme =
    if (readerContentColor == null) {
        base
    } else {
        base.copy(
            primaryContainer = readerContentColor.copy(alpha = READER_SELECTION_FILL_ALPHA),
            onPrimaryContainer = readerContentColor,
            // В режиме чтения колесо инструментов держим в палитре ридера: стрелки прокрутки
            // ([WheelScrollButtons]) и разделители красятся onSurfaceVariant/surfaceVariant —
            // без подмены они остаются цветами темы приложения и выбиваются из «бумажного» фона.
            onSurfaceVariant = readerContentColor,
            surfaceVariant = readerBackground ?: base.surfaceVariant,
        )
    }

private const val TOOL_PEN_KEY = "tool_pen"
private const val TOOL_MARKER_KEY = "tool_marker"
private const val TOOL_ERASER_KEY = "tool_eraser"

/**
 * Assembles the unified tool wheel: tool toggles, then (for an active tool) its
 * settings slots and presets, then the system controls — each group separated by
 * a thin [RailDivider]. The whole list feeds one [WheelStrip], so the bar needs
 * no `⋮` overflow.
 *
 * [expandedButtonModifier] is forwarded to the currently-expanded settings slot
 * (used by the landscape budding panel to anchor itself).
 */
internal fun unifiedToolWheelEntries(
    orientation: RailOrientation,
    toolMode: ToolMode,
    onToolModeChange: (ToolMode) -> Unit,
    penSettings: PenSettings,
    onPenSettingsChange: (PenSettings) -> Unit,
    markerSettings: MarkerSettings,
    onMarkerSettingsChange: (MarkerSettings) -> Unit,
    eraserSettings: EraserSettings,
    onEraserSettingsChange: (EraserSettings) -> Unit,
    toolPresets: StoredToolPresets,
    onToolPresetsChange: (StoredToolPresets) -> Unit,
    onPresetApplied: ((id: String) -> Unit)?,
    expandedIndex: Int?,
    onSlotToggle: (Int) -> Unit,
    hasAnnotations: Boolean,
    isExporting: Boolean,
    onExport: () -> Unit,
    scale: Int,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    showThumbnails: Boolean,
    onToggleThumbnails: () -> Unit,
    showTocButton: Boolean,
    showToc: Boolean,
    onToggleToc: () -> Unit,
    readingModeEnabled: Boolean,
    readingModeAvailable: Boolean,
    onToggleReadingMode: () -> Unit,
    showPencilModeButton: Boolean,
    pencilModeEnabled: Boolean,
    onPencilModeChange: (Boolean) -> Unit,
    magnifierEnabled: Boolean,
    onMagnifierToggle: () -> Unit,
    showSyncButton: Boolean,
    syncTint: Color,
    onOpenSync: () -> Unit,
    onOpenShortcutsSettings: () -> Unit,
    expandedButtonModifier: Modifier = Modifier,
): List<WheelEntry> {
    // В режиме чтения свободное рисование пером бессмысленно, но выделять текст
    // маркером и стирать выделение нужно. Поэтому в чтении прячем только перо
    // (вместе с его настройками/пресетами), а маркер/ластик и их настройки
    // оставляем. Для остальных режимов поведение прежнее.
    val includePen = !readingModeEnabled
    val penHidden = readingModeEnabled && toolMode == ToolMode.PEN
    val settings =
        if (penHidden) {
            emptyList()
        } else {
            toolSettingsSlotEntries(
                toolMode = toolMode,
                penSettings = penSettings,
                onPenSettingsChange = onPenSettingsChange,
                markerSettings = markerSettings,
                onMarkerSettingsChange = onMarkerSettingsChange,
                eraserSettings = eraserSettings,
                onEraserSettingsChange = onEraserSettingsChange,
                expandedIndex = expandedIndex,
                onToggle = onSlotToggle,
                expandedButtonModifier = expandedButtonModifier,
            )
        }
    val presets =
        if (penHidden) {
            emptyList()
        } else {
            toolPresetEntries(
                toolMode = toolMode,
                penSettings = penSettings,
                onPenSettingsChange = onPenSettingsChange,
                markerSettings = markerSettings,
                onMarkerSettingsChange = onMarkerSettingsChange,
                eraserSettings = eraserSettings,
                onEraserSettingsChange = onEraserSettingsChange,
                presets = toolPresets,
                onPresetsChange = onToolPresetsChange,
                onPresetApplied = onPresetApplied,
            )
        }
    val system =
        systemControlEntries(
            hasAnnotations = hasAnnotations,
            isExporting = isExporting,
            onExport = onExport,
            scale = scale,
            onZoomIn = onZoomIn,
            onZoomOut = onZoomOut,
            showThumbnails = showThumbnails,
            onToggleThumbnails = onToggleThumbnails,
            showTocButton = showTocButton,
            showToc = showToc,
            onToggleToc = onToggleToc,
            readingModeEnabled = readingModeEnabled,
            readingModeAvailable = readingModeAvailable,
            onToggleReadingMode = onToggleReadingMode,
            showPencilModeButton = showPencilModeButton,
            pencilModeEnabled = pencilModeEnabled,
            onPencilModeChange = onPencilModeChange,
            magnifierEnabled = magnifierEnabled,
            onMagnifierToggle = onMagnifierToggle,
            showSyncButton = showSyncButton,
            syncTint = syncTint,
            onOpenSync = onOpenSync,
            onOpenShortcutsSettings = onOpenShortcutsSettings,
        )
    return buildList {
        // В чтении перо скрыто, но маркер/ластик остаются (см. includePen).
        addAll(toolSelectorEntries(toolMode, onToolModeChange, includePen = includePen))
        if (settings.isNotEmpty()) {
            add(WheelEntry("div_settings", WHEEL_DIVIDER_ENTRY_SIZE) { RailDivider(orientation) })
            addAll(settings)
        }
        if (presets.isNotEmpty()) {
            add(WheelEntry("div_presets", WHEEL_DIVIDER_ENTRY_SIZE) { RailDivider(orientation) })
            addAll(presets)
        }
        // Системные кнопки заполняют колесо, когда нет настроек/пресетов активного
        // инструмента: либо инструмент не выбран, либо это скрытое в чтении перо.
        // При активном маркере/ластике их место занимают настройки и пресеты.
        if (settings.isEmpty() && presets.isEmpty() && system.isNotEmpty()) {
            add(WheelEntry("div_system", WHEEL_DIVIDER_ENTRY_SIZE) { RailDivider(orientation) })
            addAll(system)
        }
    }
}

/** Thin line separating groups inside the tool wheel, oriented across the strip. */
@Composable
internal fun RailDivider(orientation: RailOrientation) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = WHEEL_DIVIDER_ALPHA)
    when (orientation) {
        RailOrientation.HORIZONTAL ->
            Spacer(
                Modifier
                    .padding(horizontal = WHEEL_DIVIDER_SPACING)
                    .height(WHEEL_DIVIDER_LENGTH)
                    .width(WHEEL_DIVIDER_THICKNESS)
                    .background(color),
            )
        RailOrientation.VERTICAL ->
            Spacer(
                Modifier
                    .padding(vertical = WHEEL_DIVIDER_SPACING)
                    .width(WHEEL_DIVIDER_LENGTH)
                    .height(WHEEL_DIVIDER_THICKNESS)
                    .background(color),
            )
    }
}

/**
 * System (non-drawing) controls as wheel entries: pencil mode (optional),
 * magnifier, page thumbnails, table of contents, reading mode, shortcuts, sync,
 * PDF export and a zoom cluster. Appended to the unified wheel so the phone bar
 * needs no `⋮` overflow menu.
 *
 * In reading mode the drawing-adjacent controls (pencil mode, magnifier, export)
 * are dropped — only navigation / reading controls remain. The ToC button is
 * shown only when the document has a table of contents ([showTocButton]); the
 * reading-mode button is disabled when reflow is unavailable
 * ([readingModeAvailable]); the sync button appears only when sync is wired
 * ([showSyncButton]).
 */
internal fun systemControlEntries(
    hasAnnotations: Boolean,
    isExporting: Boolean,
    onExport: () -> Unit,
    scale: Int,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    showThumbnails: Boolean,
    onToggleThumbnails: () -> Unit,
    showTocButton: Boolean,
    showToc: Boolean,
    onToggleToc: () -> Unit,
    readingModeEnabled: Boolean,
    readingModeAvailable: Boolean,
    onToggleReadingMode: () -> Unit,
    showPencilModeButton: Boolean,
    pencilModeEnabled: Boolean,
    onPencilModeChange: (Boolean) -> Unit,
    magnifierEnabled: Boolean,
    onMagnifierToggle: () -> Unit,
    showSyncButton: Boolean,
    syncTint: Color,
    onOpenSync: () -> Unit,
    onOpenShortcutsSettings: () -> Unit,
): List<WheelEntry> {
    val pencilModeButton: @Composable () -> Unit = {
        if (showPencilModeButton) {
            ToolToggleButton(
                icon = Icons.Default.Gesture,
                contentDescription = "Режим стилуса",
                selected = pencilModeEnabled,
                onClick = { onPencilModeChange(!pencilModeEnabled) },
            )
        }
    }
    val thumbnailsButton: @Composable () -> Unit = {
        ToolToggleButton(
            icon = Icons.Default.GridView,
            contentDescription = "Миниатюры страниц",
            selected = showThumbnails,
            onClick = onToggleThumbnails,
        )
    }
    val tocButton: @Composable () -> Unit = {
        ToolToggleButton(
            icon = Icons.AutoMirrored.Filled.List,
            contentDescription = "Содержание",
            selected = showToc,
            onClick = onToggleToc,
        )
    }
    val readingModeButton: @Composable () -> Unit = {
        ToolToggleButton(
            icon = Icons.AutoMirrored.Filled.MenuBook,
            contentDescription = "Режим чтения",
            selected = readingModeEnabled,
            onClick = onToggleReadingMode,
        )
    }
    val syncButton: @Composable () -> Unit = {
        IconButton(onClick = onOpenSync, modifier = Modifier.size(RAIL_BUTTON_SIZE)) {
            Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = "Синхронизация",
                tint = syncTint,
            )
        }
    }
    val shortcutsButton: @Composable () -> Unit = {
        IconButton(onClick = onOpenShortcutsSettings, modifier = Modifier.size(RAIL_BUTTON_SIZE)) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Шорткаты",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    val exportButton: @Composable () -> Unit = {
        IconButton(
            onClick = onExport,
            enabled = hasAnnotations && !isExporting,
            modifier = Modifier.size(RAIL_BUTTON_SIZE),
        ) {
            if (isExporting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(EXPORT_PROGRESS_SIZE),
                    strokeWidth = EXPORT_PROGRESS_STROKE,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.PictureAsPdf,
                    contentDescription = "Экспортировать в PDF",
                    tint =
                        if (hasAnnotations) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = SYSTEM_DISABLED_ALPHA)
                        },
                )
            }
        }
    }
    val magnifierButton: @Composable () -> Unit = {
        ToolToggleButton(
            icon = NotePenIcons.WritingLoupe,
            contentDescription = "Лупа для письма",
            selected = magnifierEnabled,
            onClick = onMagnifierToggle,
        )
    }
    // Зум разбит на ОТДЕЛЬНЫЕ элементы колеса (а не один Row), иначе в
    // вертикальной рельсе широкий горизонтальный кластер обрезался по ширине
    // полосы — «−» пропадал, а проценты съезжали.
    val zoomInButton: @Composable () -> Unit = {
        IconButton(onClick = onZoomIn, enabled = scale < MAX_SCALE, modifier = Modifier.size(RAIL_BUTTON_SIZE)) {
            Icon(
                imageVector = Icons.Default.ZoomIn,
                contentDescription = "Увеличить",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    val zoomLabel: @Composable () -> Unit = {
        Text(
            text = "$scale%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            // Фиксированная ширина под максимум "800%": иначе смена зума меняет
            // ширину метки и дёргает соседние элементы.
            modifier = Modifier.width(SCALE_LABEL_WIDTH),
        )
    }
    val zoomOutButton: @Composable () -> Unit = {
        IconButton(onClick = onZoomOut, enabled = scale > MIN_SCALE, modifier = Modifier.size(RAIL_BUTTON_SIZE)) {
            Icon(
                imageVector = Icons.Default.ZoomOut,
                contentDescription = "Уменьшить",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    return buildList {
        // Инструменты рисования и их спутники (стилус, лупа, экспорт) в режиме
        // чтения не нужны — оставляем только навигацию и контролы чтения.
        if (showPencilModeButton && !readingModeEnabled) add(WheelEntry("sys_pencil") { pencilModeButton() })
        if (!readingModeEnabled) add(WheelEntry("sys_magnifier") { magnifierButton() })
        add(WheelEntry("sys_thumbnails") { thumbnailsButton() })
        if (showTocButton) add(WheelEntry("sys_toc") { tocButton() })
        // Скрываем кнопку (а не дизейблим) для документов без извлекаемого текста;
        // оставляем видимой, пока режим чтения активен, чтобы из него можно было выйти.
        if (readingModeAvailable || readingModeEnabled) add(WheelEntry("sys_reading") { readingModeButton() })
        if (showSyncButton) add(WheelEntry("sys_sync") { syncButton() })
        add(WheelEntry("sys_shortcuts") { shortcutsButton() })
        if (!readingModeEnabled) add(WheelEntry("sys_export") { exportButton() })
        add(WheelEntry("sys_zoom_in") { zoomInButton() })
        add(WheelEntry("sys_zoom_label", WHEEL_LABEL_ENTRY_SIZE) { zoomLabel() })
        add(WheelEntry("sys_zoom_out") { zoomOutButton() })
    }
}

/**
 * Drawing-tool state for the tool rail: the active [toolMode] plus the per-tool
 * settings (pen / marker / eraser) and the saved [toolPresets], each paired with
 * its change callback. Grouped so the rail composables take a single cohesive
 * argument instead of a dozen loose tool parameters.
 *
 * @property toolMode the currently active drawing tool.
 * @property onToolModeChange invoked with the next tool when a toggle is tapped.
 * @property penSettings current pen settings; [onPenSettingsChange] reports edits.
 * @property markerSettings current marker settings; [onMarkerSettingsChange] reports edits.
 * @property eraserSettings current eraser settings; [onEraserSettingsChange] reports edits.
 * @property toolPresets saved per-tool presets; [onToolPresetsChange] reports edits.
 * @property onPresetApplied invoked with a preset id when the user applies one;
 *   `null` disables preset-applied reporting.
 */
data class ToolRailTools(
    val toolMode: ToolMode,
    val onToolModeChange: (ToolMode) -> Unit,
    val penSettings: PenSettings,
    val onPenSettingsChange: (PenSettings) -> Unit,
    val markerSettings: MarkerSettings,
    val onMarkerSettingsChange: (MarkerSettings) -> Unit,
    val eraserSettings: EraserSettings,
    val onEraserSettingsChange: (EraserSettings) -> Unit,
    val toolPresets: StoredToolPresets,
    val onToolPresetsChange: (StoredToolPresets) -> Unit,
    val onPresetApplied: ((id: String) -> Unit)? = null,
)

/**
 * Non-drawing ("system") controls for the tool rail — export, zoom, page
 * thumbnails, table of contents, reading mode, pencil mode, magnifier, sync and
 * the shortcuts settings. Mirrors the parameters of [systemControlEntries]; the
 * rail unpacks it when building that wheel section.
 *
 * @property hasAnnotations enables the PDF export button.
 * @property isExporting shows the export progress spinner.
 * @property onExport invoked when the export button is tapped.
 * @property scale current zoom percentage shown between the zoom buttons.
 * @property onZoomIn / @property onZoomOut zoom cluster callbacks.
 * @property showThumbnails / @property onToggleThumbnails page-thumbnails toggle state + callback.
 * @property showTocButton whether the table-of-contents button is shown at all.
 * @property showToc / @property onToggleToc table-of-contents toggle state + callback.
 * @property readingModeEnabled / @property readingModeAvailable / @property onToggleReadingMode
 *   reading-mode toggle state, availability (reflow possible) and callback.
 * @property showPencilModeButton / @property pencilModeEnabled / @property onPencilModeChange
 *   stylus-mode button visibility, state and callback.
 * @property magnifierEnabled / @property onMagnifierToggle writing-loupe toggle state + callback.
 * @property showSyncButton / @property syncTint / @property onOpenSync sync button visibility,
 *   tint and callback.
 * @property onOpenShortcutsSettings opens the keyboard-shortcuts settings.
 */
data class ToolRailSystem(
    val hasAnnotations: Boolean,
    val isExporting: Boolean,
    val onExport: () -> Unit,
    val scale: Int,
    val onZoomIn: () -> Unit,
    val onZoomOut: () -> Unit,
    val showThumbnails: Boolean,
    val onToggleThumbnails: () -> Unit,
    val showTocButton: Boolean,
    val showToc: Boolean,
    val onToggleToc: () -> Unit,
    val readingModeEnabled: Boolean,
    val readingModeAvailable: Boolean,
    val onToggleReadingMode: () -> Unit,
    val showPencilModeButton: Boolean,
    val pencilModeEnabled: Boolean,
    val onPencilModeChange: (Boolean) -> Unit,
    val magnifierEnabled: Boolean,
    val onMagnifierToggle: () -> Unit,
    val showSyncButton: Boolean,
    val syncTint: Color,
    val onOpenSync: () -> Unit,
    val onOpenShortcutsSettings: () -> Unit,
)

/**
 * Reader-theme colours applied to the tool rail in reading mode.
 *
 * @property background glass tint of the rail; `null` keeps the [MaterialTheme]
 *   surface colour.
 * @property contentColor recolours the selected-tool indicator under the reader
 *   theme (see [railSelectionColorScheme]); `null` keeps the [MaterialTheme]
 *   accent.
 */
data class ToolRailReaderTheme(
    val background: Color? = null,
    val contentColor: Color? = null,
)

/**
 * Landscape (vertical) tool rail: one glass island holding a single vertical
 * [WheelStrip] — tool toggles, the active tool's settings slots and presets, and
 * the system controls, separated by [RailDivider]s. Entries fade towards the
 * rail's edges and scroll when they overflow.
 *
 * Tapping a settings slot sprouts a separate vertical "budding" panel to the
 * RIGHT of the rail carrying the slider / color picker — the slot icons stay put
 * inside the wheel.
 *
 * @param tools drawing-tool state (active tool, per-tool settings, presets).
 * @param system non-drawing controls (export, zoom, thumbnails, toc, …).
 * @param readerTheme reader-theme colours applied in reading mode.
 * @param onRailWidthChanged reports the rail's width (excluding the budding panel)
 *   so the caller can align the back button to the rail.
 */
@Composable
fun LandscapeToolRail(
    tools: ToolRailTools,
    system: ToolRailSystem,
    modifier: Modifier = Modifier,
    readerTheme: ToolRailReaderTheme = ToolRailReaderTheme(),
    onRailWidthChanged: (Dp) -> Unit = {},
) {
    val density = LocalDensity.current
    val expansion = rememberToolRailExpansion(tools.toolMode)

    // Vertical anchoring: the budding panel aligns its centre to the tapped
    // slot button so it visually sprouts from that button.
    var railCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var buttonCenterY by remember { mutableStateOf(0f) }
    var panelHeightPx by remember { mutableStateOf(0) }

    val toolActive = tools.toolMode != ToolMode.NONE

    Row(
        modifier = modifier.onGloballyPositioned { railCoords = it },
        verticalAlignment = Alignment.Bottom,
    ) {
        // Инструменты, настройки, пресеты и системные кнопки — в ОДНОМ
        // вертикальном колесе (затухание к краям). Колесо подгоняется по высоте
        // под содержимое и прокручивается, если оно не помещается.
        val entries =
            landscapeWheelEntries(
                tools = tools,
                system = system,
                expandedIndex = expansion.expandedIndex,
                onSlotToggle = expansion::toggle,
                expandedButtonModifier =
                    Modifier.onGloballyPositioned { btn ->
                        railCoords?.let { row ->
                            buttonCenterY =
                                row
                                    .localPositionOf(
                                        btn,
                                        Offset(0f, btn.size.height / 2f),
                                    ).y
                        }
                    },
            )
        GlassSurface(
            tint = readerTheme.background ?: MaterialTheme.colorScheme.surface,
            modifier = Modifier.onSizeChanged { onRailWidthChanged(with(density) { it.width.toDp() }) },
        ) {
            MaterialTheme(
                colorScheme =
                    railSelectionColorScheme(
                        MaterialTheme.colorScheme,
                        readerTheme.contentColor,
                        readerTheme.background,
                    ),
            ) {
                WheelStrip(
                    entries = entries,
                    orientation = RailOrientation.VERTICAL,
                    modifier = Modifier.padding(ISLAND_PADDING),
                    selectedKey = selectedToolWheelKey(tools.toolMode),
                )
            }
        }
        ToolRailBuddingPanel(
            visible = toolActive && expansion.expandedIndex != null,
            anchor = ToolRailAnchor(buttonCenterY, panelHeightPx, onPanelHeightChange = { panelHeightPx = it }),
            tools = tools,
            slotIndex = expansion.lastExpanded,
        )
    }
}

/**
 * Builds the landscape wheel entries by unpacking [tools] and [system] into the
 * shared [unifiedToolWheelEntries] builder (vertical orientation).
 * [expandedButtonModifier] anchors the budding panel to the open settings slot.
 */
private fun landscapeWheelEntries(
    tools: ToolRailTools,
    system: ToolRailSystem,
    expandedIndex: Int?,
    onSlotToggle: (Int) -> Unit,
    expandedButtonModifier: Modifier,
): List<WheelEntry> =
    unifiedToolWheelEntries(
        orientation = RailOrientation.VERTICAL,
        toolMode = tools.toolMode,
        onToolModeChange = tools.onToolModeChange,
        penSettings = tools.penSettings,
        onPenSettingsChange = tools.onPenSettingsChange,
        markerSettings = tools.markerSettings,
        onMarkerSettingsChange = tools.onMarkerSettingsChange,
        eraserSettings = tools.eraserSettings,
        onEraserSettingsChange = tools.onEraserSettingsChange,
        toolPresets = tools.toolPresets,
        onToolPresetsChange = tools.onToolPresetsChange,
        onPresetApplied = tools.onPresetApplied,
        expandedIndex = expandedIndex,
        onSlotToggle = onSlotToggle,
        hasAnnotations = system.hasAnnotations,
        isExporting = system.isExporting,
        onExport = system.onExport,
        scale = system.scale,
        onZoomIn = system.onZoomIn,
        onZoomOut = system.onZoomOut,
        showThumbnails = system.showThumbnails,
        onToggleThumbnails = system.onToggleThumbnails,
        showTocButton = system.showTocButton,
        showToc = system.showToc,
        onToggleToc = system.onToggleToc,
        readingModeEnabled = system.readingModeEnabled,
        readingModeAvailable = system.readingModeAvailable,
        onToggleReadingMode = system.onToggleReadingMode,
        showPencilModeButton = system.showPencilModeButton,
        pencilModeEnabled = system.pencilModeEnabled,
        onPencilModeChange = system.onPencilModeChange,
        magnifierEnabled = system.magnifierEnabled,
        onMagnifierToggle = system.onMagnifierToggle,
        showSyncButton = system.showSyncButton,
        syncTint = system.syncTint,
        onOpenSync = system.onOpenSync,
        onOpenShortcutsSettings = system.onOpenShortcutsSettings,
        expandedButtonModifier = expandedButtonModifier,
    )

/**
 * Vertical anchoring for the budding panel: where it sits and how it measures.
 *
 * @property buttonCenterY y of the tapped slot's centre, in the rail's coordinates;
 *   the panel offsets its own centre to match so it sprouts from that slot.
 * @property panelHeightPx the panel's last measured height, used to centre it.
 * @property onPanelHeightChange reports the panel's measured height back.
 */
@Stable
private class ToolRailAnchor(
    val buttonCenterY: Float,
    val panelHeightPx: Int,
    val onPanelHeightChange: (Int) -> Unit,
)

/**
 * The vertical "budding" panel that sprouts to the RIGHT of the rail carrying the
 * active settings slot's slider / color picker. It vertically anchors its centre
 * to the tapped slot via [anchor].
 */
@Composable
private fun RowScope.ToolRailBuddingPanel(
    visible: Boolean,
    anchor: ToolRailAnchor,
    tools: ToolRailTools,
    slotIndex: Int,
) {
    AnimatedVisibility(
        visible = visible,
        enter =
            expandHorizontally(animationSpec = tween(PANEL_ANIM_MS), expandFrom = Alignment.Start) +
                fadeIn(animationSpec = tween(PANEL_ANIM_MS)) +
                scaleIn(animationSpec = tween(PANEL_ANIM_MS), transformOrigin = TransformOrigin(0f, 0.5f)),
        exit =
            shrinkHorizontally(animationSpec = tween(PANEL_ANIM_MS), shrinkTowards = Alignment.Start) +
                fadeOut(animationSpec = tween(PANEL_ANIM_MS)) +
                scaleOut(animationSpec = tween(PANEL_ANIM_MS), transformOrigin = TransformOrigin(0f, 0.5f)),
        modifier =
            Modifier
                .align(Alignment.Top)
                .offset {
                    IntOffset(0, (anchor.buttonCenterY - anchor.panelHeightPx / 2f).roundToInt().coerceAtLeast(0))
                },
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Spacer(Modifier.width(ISLAND_GAP))
            GlassSurface(modifier = Modifier.onSizeChanged { anchor.onPanelHeightChange(it.height) }) {
                ToolSettingsExpansionContent(
                    toolMode = tools.toolMode,
                    penSettings = tools.penSettings,
                    onPenSettingsChange = tools.onPenSettingsChange,
                    markerSettings = tools.markerSettings,
                    onMarkerSettingsChange = tools.onMarkerSettingsChange,
                    eraserSettings = tools.eraserSettings,
                    onEraserSettingsChange = tools.onEraserSettingsChange,
                    orientation = RailOrientation.VERTICAL,
                    index = slotIndex,
                )
            }
        }
    }
}

/**
 * Expansion state of the rail's settings slots: which slot's budding panel is
 * open and the pending slot during a slot-to-slot switch.
 *
 * @property expandedIndex the currently open slot, or `null` when none is open.
 * @property lastExpanded the last opened slot — kept so the budding panel renders
 *   its content through the collapse exit animation even after [expandedIndex]
 *   clears.
 */
@Stable
internal class ToolRailExpansion(
    private val expanded: MutableState<Int?>,
    private val pending: MutableState<Int?>,
    private val last: MutableState<Int>,
) {
    val expandedIndex: Int? get() = expanded.value
    val lastExpanded: Int get() = last.value

    /** Toggles slot [index]; switching from another open slot collapses it first. */
    fun toggle(index: Int) {
        when {
            expanded.value == index -> {
                expanded.value = null
                pending.value = null
            }
            expanded.value == null -> expanded.value = index
            else -> {
                // Collapse the current panel; the LaunchedEffect opens [index] next.
                pending.value = index
                expanded.value = null
            }
        }
    }
}

/**
 * Wires the rail's expansion state and the delayed slot-switch effect.
 * [expandedIndex]/pending reset whenever the active [toolMode] changes; the last
 * opened slot persists so a collapsing panel keeps its content.
 */
@Composable
internal fun rememberToolRailExpansion(toolMode: ToolMode): ToolRailExpansion {
    val expandedIndex = remember(toolMode) { mutableStateOf<Int?>(null) }
    // Switching directly between two slots first collapses the open panel, then
    // opens the requested one — [pendingIndex] holds the slot awaiting that
    // delayed open.
    val pendingIndex = remember(toolMode) { mutableStateOf<Int?>(null) }
    // Retain the last expanded slot so the budding panel keeps rendering its
    // content through the collapse exit animation.
    val lastExpanded = remember { mutableStateOf(0) }
    LaunchedEffect(expandedIndex.value) { expandedIndex.value?.let { lastExpanded.value = it } }
    LaunchedEffect(expandedIndex.value, pendingIndex.value) {
        if (expandedIndex.value == null && pendingIndex.value != null) {
            delay(PANEL_ANIM_MS.toLong())
            expandedIndex.value = pendingIndex.value
            pendingIndex.value = null
        }
    }
    return remember(expandedIndex, pendingIndex, lastExpanded) {
        ToolRailExpansion(expandedIndex, pendingIndex, lastExpanded)
    }
}

/** Open/close duration of the budding panel; the slot-switch delay matches it. */
private const val PANEL_ANIM_MS = 180

/**
 * Прозрачность заливки индикатора выбора в теме ридера — как у выбранного чипа
 * панели ридера (`CHIP_SELECTED_FILL_ALPHA` в `ReaderAirbar`), чтобы рельса и
 * панель читалки говорили на одном визуальном языке выбора.
 */
private const val READER_SELECTION_FILL_ALPHA = 0.22f

private val WHEEL_DIVIDER_SPACING = 6.dp
private val WHEEL_DIVIDER_LENGTH = 28.dp
private val WHEEL_DIVIDER_THICKNESS = 1.5.dp
private const val WHEEL_DIVIDER_ALPHA = 0.7f

/** Main-axis size estimates for non-button wheel entries (for tight strip sizing). */
private val WHEEL_DIVIDER_ENTRY_SIZE = 16.dp
private val WHEEL_LABEL_ENTRY_SIZE = 24.dp

private val SCALE_LABEL_WIDTH = 40.dp
private val ISLAND_GAP = 8.dp
private val ISLAND_PADDING = 4.dp
private val EXPORT_PROGRESS_SIZE = 24.dp
private val EXPORT_PROGRESS_STROKE = 2.dp
private const val SYSTEM_DISABLED_ALPHA = 0.38f
