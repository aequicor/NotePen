package ru.kyamshanov.notepen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
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
 * Tool selection buttons (Pen / Marker / Eraser) laid out along [orientation].
 * Re-tap on the active tool deactivates it — see [nextToolModeOnToggle].
 */
@Composable
internal fun ToolSelector(
    toolMode: ToolMode,
    onToolModeChange: (ToolMode) -> Unit,
    orientation: RailOrientation,
    modifier: Modifier = Modifier,
) {
    val buttons: @Composable () -> Unit = {
        ToolToggleButton(
            icon = Icons.Default.Edit,
            contentDescription = "Перо",
            selected = toolMode == ToolMode.PEN,
            onClick = { onToolModeChange(nextToolModeOnToggle(toolMode, ToolMode.PEN)) },
        )
        ToolToggleButton(
            icon = Icons.Default.BorderColor,
            contentDescription = "Маркер",
            selected = toolMode == ToolMode.MARKER,
            onClick = { onToolModeChange(nextToolModeOnToggle(toolMode, ToolMode.MARKER)) },
        )
        ToolToggleButton(
            icon = Icons.Default.CleaningServices,
            contentDescription = "Ластик",
            selected = toolMode == ToolMode.ERASER,
            onClick = { onToolModeChange(nextToolModeOnToggle(toolMode, ToolMode.ERASER)) },
        )
    }
    when (orientation) {
        RailOrientation.VERTICAL -> Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { buttons() }
        RailOrientation.HORIZONTAL -> Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
        ) { buttons() }
    }
}

/**
 * System (non-drawing) controls: pencil mode, magnifier, page thumbnails,
 * shortcut settings, PDF export and zoom. Laid out along [orientation].
 */
@Composable
internal fun SystemControls(
    orientation: RailOrientation,
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
) {
    val buttons: @Composable () -> Unit = {
        if (showPencilModeButton) {
            ToolToggleButton(
                icon = Icons.Default.Gesture,
                contentDescription = "Режим стилуса",
                selected = pencilModeEnabled,
                onClick = { onPencilModeChange(!pencilModeEnabled) },
            )
        }
        ToolToggleButton(
            icon = Icons.Default.Search,
            contentDescription = "Лупа для письма",
            selected = magnifierEnabled,
            onClick = onMagnifierToggle,
        )
        ToolToggleButton(
            icon = Icons.Default.GridView,
            contentDescription = "Миниатюры страниц",
            selected = showThumbnails,
            onClick = onToggleThumbnails,
        )
        IconButton(onClick = onOpenShortcutsSettings) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Шорткаты",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = onExport,
            enabled = hasAnnotations && !isExporting,
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
        IconButton(onClick = onZoomIn, enabled = scale < MAX_SCALE) {
            Icon(
                imageVector = Icons.Default.ZoomIn,
                contentDescription = "Увеличить",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "$scale%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onClick = onZoomOut, enabled = scale > MIN_SCALE) {
            Icon(
                imageVector = Icons.Default.ZoomOut,
                contentDescription = "Уменьшить",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    when (orientation) {
        RailOrientation.VERTICAL -> Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { buttons() }
        RailOrientation.HORIZONTAL -> Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
        ) { buttons() }
    }
}

/**
 * Landscape (vertical) tool rail split into two glass "islands":
 *  - top island — [ToolSelector] (always visible);
 *  - bottom island — [SystemControls] when no tool is active, or the active
 *    tool's [ToolSettingsIconStrip] when a tool is selected (full content swap).
 *
 * Tapping a settings slot sprouts a separate vertical "budding" panel to the
 * RIGHT of the bottom island carrying the slider / color picker — the slot
 * icons stay put inside the island.
 *
 * [onRailWidthChanged] reports the islands' width (excluding the budding panel)
 * so the caller can align the back button to the rail.
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
        Column(
            verticalArrangement = Arrangement.spacedBy(ISLAND_GAP),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.onSizeChanged {
                onRailWidthChanged(with(density) { it.width.toDp() })
            },
        ) {
            GlassSurface {
                ToolSelector(
                    toolMode = toolMode,
                    onToolModeChange = onToolModeChange,
                    orientation = RailOrientation.VERTICAL,
                    modifier = Modifier.padding(ISLAND_PADDING),
                )
            }
            GlassSurface {
                AnimatedContent(targetState = toolMode, label = "rail-segment-b") { mode ->
                    if (mode != ToolMode.NONE) {
                        ToolSettingsIconStrip(
                            toolMode = mode,
                            penSettings = penSettings,
                            onPenSettingsChange = onPenSettingsChange,
                            markerSettings = markerSettings,
                            onMarkerSettingsChange = onMarkerSettingsChange,
                            eraserSettings = eraserSettings,
                            onEraserSettingsChange = onEraserSettingsChange,
                            orientation = RailOrientation.VERTICAL,
                            expandedIndex = expandedIndex,
                            onToggle = onSlotToggle,
                            modifier = Modifier.padding(ISLAND_PADDING),
                            expandedButtonModifier = Modifier.onGloballyPositioned { btn ->
                                railCoords?.let { row ->
                                    buttonCenterY = row.localPositionOf(
                                        btn,
                                        Offset(0f, btn.size.height / 2f),
                                    ).y
                                }
                            },
                        )
                    } else {
                        SystemControls(
                            orientation = RailOrientation.VERTICAL,
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
                            modifier = Modifier.padding(ISLAND_PADDING),
                        )
                    }
                }
            }
            if (toolActive) {
                GlassSurface {
                    Box(Modifier.padding(ISLAND_PADDING)) {
                        ToolPresetsZone(
                            toolMode = toolMode,
                            penSettings = penSettings,
                            onPenSettingsChange = onPenSettingsChange,
                            markerSettings = markerSettings,
                            onMarkerSettingsChange = onMarkerSettingsChange,
                            eraserSettings = eraserSettings,
                            onEraserSettingsChange = onEraserSettingsChange,
                            presets = toolPresets,
                            onPresetsChange = onToolPresetsChange,
                            orientation = RailOrientation.VERTICAL,
                            onPresetApplied = onPresetApplied,
                        )
                    }
                }
            }
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

private val ISLAND_GAP = 8.dp
private val ISLAND_PADDING = 4.dp
private val EXPORT_PROGRESS_SIZE = 24.dp
private val EXPORT_PROGRESS_STROKE = 2.dp
private const val SYSTEM_DISABLED_ALPHA = 0.38f
