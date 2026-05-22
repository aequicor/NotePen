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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import kotlin.math.roundToInt
import ru.kyamshanov.notepen.annotation.domain.model.EraserSettings
import ru.kyamshanov.notepen.annotation.domain.model.MarkerSettings
import ru.kyamshanov.notepen.annotation.domain.model.PenSettings
import ru.kyamshanov.notepen.annotation.domain.model.StoredToolPresets
import ru.kyamshanov.notepen.ui.glass.GlassSurface

/**
 * Tool toggles (Pen / Marker / Eraser) as a list of [WheelEntry] for the unified
 * tool wheel. Re-tapping the active tool deactivates it — see [nextToolModeOnToggle].
 */
internal fun toolSelectorEntries(
    toolMode: ToolMode,
    onToolModeChange: (ToolMode) -> Unit,
): List<WheelEntry> = listOf(
    WheelEntry(TOOL_PEN_KEY) {
        ToolToggleButton(
            icon = NotePenIcons.Brush,
            contentDescription = "Перо",
            selected = toolMode == ToolMode.PEN,
            onClick = { onToolModeChange(nextToolModeOnToggle(toolMode, ToolMode.PEN)) },
            showSelectionBackground = false,
        )
    },
    WheelEntry(TOOL_MARKER_KEY) {
        ToolToggleButton(
            icon = NotePenIcons.Highlighter,
            contentDescription = "Маркер",
            selected = toolMode == ToolMode.MARKER,
            onClick = { onToolModeChange(nextToolModeOnToggle(toolMode, ToolMode.MARKER)) },
            showSelectionBackground = false,
        )
    },
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

/** Wheel-entry key of the currently selected drawing tool, or `null` for [ToolMode.NONE]. */
internal fun selectedToolWheelKey(toolMode: ToolMode): Any? = when (toolMode) {
    ToolMode.PEN -> TOOL_PEN_KEY
    ToolMode.MARKER -> TOOL_MARKER_KEY
    ToolMode.ERASER -> TOOL_ERASER_KEY
    ToolMode.NONE -> null
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
    showPencilModeButton: Boolean,
    pencilModeEnabled: Boolean,
    onPencilModeChange: (Boolean) -> Unit,
    magnifierEnabled: Boolean,
    onMagnifierToggle: () -> Unit,
    onOpenShortcutsSettings: () -> Unit,
    expandedButtonModifier: Modifier = Modifier,
): List<WheelEntry> {
    val settings = toolSettingsSlotEntries(
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
    val presets = toolPresetEntries(
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
    val system = systemControlEntries(
        hasAnnotations = hasAnnotations,
        isExporting = isExporting,
        onExport = onExport,
        scale = scale,
        onZoomIn = onZoomIn,
        onZoomOut = onZoomOut,
        showThumbnails = showThumbnails,
        onToggleThumbnails = onToggleThumbnails,
        showPencilModeButton = showPencilModeButton,
        pencilModeEnabled = pencilModeEnabled,
        onPencilModeChange = onPencilModeChange,
        magnifierEnabled = magnifierEnabled,
        onMagnifierToggle = onMagnifierToggle,
        onOpenShortcutsSettings = onOpenShortcutsSettings,
    )
    return buildList {
        addAll(toolSelectorEntries(toolMode, onToolModeChange))
        if (settings.isNotEmpty()) {
            add(WheelEntry("div_settings", WHEEL_DIVIDER_ENTRY_SIZE) { RailDivider(orientation) })
            addAll(settings)
        }
        if (presets.isNotEmpty()) {
            add(WheelEntry("div_presets", WHEEL_DIVIDER_ENTRY_SIZE) { RailDivider(orientation) })
            addAll(presets)
        }
        // Системные кнопки показываем ТОЛЬКО когда инструмент не выбран: при
        // активном инструменте их место занимают настройки и пресеты.
        if (toolMode == ToolMode.NONE && system.isNotEmpty()) {
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
        RailOrientation.HORIZONTAL -> Spacer(
            Modifier
                .padding(horizontal = WHEEL_DIVIDER_SPACING)
                .height(WHEEL_DIVIDER_LENGTH)
                .width(WHEEL_DIVIDER_THICKNESS)
                .background(color),
        )
        RailOrientation.VERTICAL -> Spacer(
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
 * magnifier, page thumbnails, shortcuts, PDF export and a zoom cluster. Appended
 * to the unified wheel so the phone bar needs no `⋮` overflow menu.
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
    showPencilModeButton: Boolean,
    pencilModeEnabled: Boolean,
    onPencilModeChange: (Boolean) -> Unit,
    magnifierEnabled: Boolean,
    onMagnifierToggle: () -> Unit,
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
                    tint = if (hasAnnotations) {
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
        if (showPencilModeButton) add(WheelEntry("sys_pencil") { pencilModeButton() })
        add(WheelEntry("sys_magnifier") { magnifierButton() })
        add(WheelEntry("sys_thumbnails") { thumbnailsButton() })
        add(WheelEntry("sys_shortcuts") { shortcutsButton() })
        add(WheelEntry("sys_export") { exportButton() })
        add(WheelEntry("sys_zoom_in") { zoomInButton() })
        add(WheelEntry("sys_zoom_label", WHEEL_LABEL_ENTRY_SIZE) { zoomLabel() })
        add(WheelEntry("sys_zoom_out") { zoomOutButton() })
    }
}

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
 * [onRailWidthChanged] reports the rail's width (excluding the budding panel) so
 * the caller can align the back button to the rail.
 */
@Composable
fun LandscapeToolRail(
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
    onPresetApplied: ((id: String) -> Unit)? = null,
    hasAnnotations: Boolean,
    isExporting: Boolean,
    onExport: () -> Unit,
    scale: Int,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    showThumbnails: Boolean,
    onToggleThumbnails: () -> Unit,
    showPencilModeButton: Boolean,
    pencilModeEnabled: Boolean,
    onPencilModeChange: (Boolean) -> Unit,
    magnifierEnabled: Boolean,
    onMagnifierToggle: () -> Unit,
    onOpenShortcutsSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onRailWidthChanged: (Dp) -> Unit = {},
) {
    val density = LocalDensity.current
    var expandedIndex by remember(toolMode) { mutableStateOf<Int?>(null) }
    // Switching directly between two slots first collapses the open panel, then
    // opens the requested one — [pendingIndex] holds the slot awaiting that
    // delayed open.
    var pendingIndex by remember(toolMode) { mutableStateOf<Int?>(null) }
    // Retain the last expanded slot so the budding panel keeps rendering its
    // content through the collapse exit animation.
    var lastExpanded by remember { mutableStateOf(0) }
    LaunchedEffect(expandedIndex) { expandedIndex?.let { lastExpanded = it } }
    LaunchedEffect(expandedIndex, pendingIndex) {
        if (expandedIndex == null && pendingIndex != null) {
            delay(PANEL_ANIM_MS.toLong())
            expandedIndex = pendingIndex
            pendingIndex = null
        }
    }
    val onSlotToggle: (Int) -> Unit = { i ->
        when {
            expandedIndex == i -> {
                expandedIndex = null
                pendingIndex = null
            }
            expandedIndex == null -> expandedIndex = i
            else -> {
                // Collapse the current panel; the LaunchedEffect opens [i] next.
                pendingIndex = i
                expandedIndex = null
            }
        }
    }

    // Vertical anchoring: the budding panel aligns its centre to the tapped
    // slot button so it visually sprouts from that button.
    var railCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var buttonCenterY by remember { mutableStateOf(0f) }
    var panelHeightPx by remember { mutableStateOf(0) }

    val toolActive = toolMode != ToolMode.NONE

    Row(
        modifier = modifier.onGloballyPositioned { railCoords = it },
        verticalAlignment = Alignment.Bottom,
    ) {
        // Инструменты, настройки, пресеты и системные кнопки — в ОДНОМ
        // вертикальном колесе (затухание к краям). Колесо подгоняется по высоте
        // под содержимое и прокручивается, если оно не помещается.
        val entries = unifiedToolWheelEntries(
            orientation = RailOrientation.VERTICAL,
            toolMode = toolMode,
            onToolModeChange = onToolModeChange,
            penSettings = penSettings,
            onPenSettingsChange = onPenSettingsChange,
            markerSettings = markerSettings,
            onMarkerSettingsChange = onMarkerSettingsChange,
            eraserSettings = eraserSettings,
            onEraserSettingsChange = onEraserSettingsChange,
            toolPresets = toolPresets,
            onToolPresetsChange = onToolPresetsChange,
            onPresetApplied = onPresetApplied,
            expandedIndex = expandedIndex,
            onSlotToggle = onSlotToggle,
            hasAnnotations = hasAnnotations,
            isExporting = isExporting,
            onExport = onExport,
            scale = scale,
            onZoomIn = onZoomIn,
            onZoomOut = onZoomOut,
            showThumbnails = showThumbnails,
            onToggleThumbnails = onToggleThumbnails,
            showPencilModeButton = showPencilModeButton,
            pencilModeEnabled = pencilModeEnabled,
            onPencilModeChange = onPencilModeChange,
            magnifierEnabled = magnifierEnabled,
            onMagnifierToggle = onMagnifierToggle,
            onOpenShortcutsSettings = onOpenShortcutsSettings,
            expandedButtonModifier = Modifier.onGloballyPositioned { btn ->
                railCoords?.let { row ->
                    buttonCenterY = row.localPositionOf(
                        btn,
                        Offset(0f, btn.size.height / 2f),
                    ).y
                }
            },
        )
        GlassSurface(
            modifier = Modifier.onSizeChanged { onRailWidthChanged(with(density) { it.width.toDp() }) },
        ) {
            WheelStrip(
                entries = entries,
                orientation = RailOrientation.VERTICAL,
                modifier = Modifier.padding(ISLAND_PADDING),
                selectedKey = selectedToolWheelKey(toolMode),
            )
        }
        AnimatedVisibility(
            visible = toolActive && expandedIndex != null,
            enter = expandHorizontally(animationSpec = tween(PANEL_ANIM_MS), expandFrom = Alignment.Start) +
                fadeIn(animationSpec = tween(PANEL_ANIM_MS)) +
                scaleIn(animationSpec = tween(PANEL_ANIM_MS), transformOrigin = TransformOrigin(0f, 0.5f)),
            exit = shrinkHorizontally(animationSpec = tween(PANEL_ANIM_MS), shrinkTowards = Alignment.Start) +
                fadeOut(animationSpec = tween(PANEL_ANIM_MS)) +
                scaleOut(animationSpec = tween(PANEL_ANIM_MS), transformOrigin = TransformOrigin(0f, 0.5f)),
            modifier = Modifier
                .align(Alignment.Top)
                .offset {
                    IntOffset(0, (buttonCenterY - panelHeightPx / 2f).roundToInt().coerceAtLeast(0))
                },
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Spacer(Modifier.width(ISLAND_GAP))
                GlassSurface(modifier = Modifier.onSizeChanged { panelHeightPx = it.height }) {
                    ToolSettingsExpansionContent(
                        toolMode = toolMode,
                        penSettings = penSettings,
                        onPenSettingsChange = onPenSettingsChange,
                        markerSettings = markerSettings,
                        onMarkerSettingsChange = onMarkerSettingsChange,
                        eraserSettings = eraserSettings,
                        onEraserSettingsChange = onEraserSettingsChange,
                        orientation = RailOrientation.VERTICAL,
                        index = lastExpanded,
                    )
                }
            }
        }
    }
}

/** Open/close duration of the budding panel; the slot-switch delay matches it. */
private const val PANEL_ANIM_MS = 180

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
