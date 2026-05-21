package ru.kyamshanov.notepen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ru.kyamshanov.notepen.annotation.domain.model.EraserSettings
import ru.kyamshanov.notepen.annotation.domain.model.MarkerSettings
import ru.kyamshanov.notepen.annotation.domain.model.PenSettings
import ru.kyamshanov.notepen.ui.glass.GlassSurface

/**
 * Full-width top chrome for portrait mode.
 *
 * A single bar split by a thin divider into two segments:
 *  - left segment — back button, page counter and the [ToolSelector];
 *  - right segment — [SystemControls] when no tool is active, or the active
 *    tool's [ToolSettingsIconStrip] when a tool is selected (full content swap).
 *
 * Tapping a settings slot opens its slider / color picker in a panel that
 * animates DOWN below the bar.
 */
@Composable
fun PortraitTopBar(
    currentPage: Int,
    totalPages: Int,
    toolMode: ToolMode,
    onToolModeChange: (ToolMode) -> Unit,
    penSettings: PenSettings,
    onPenSettingsChange: (PenSettings) -> Unit,
    markerSettings: MarkerSettings,
    onMarkerSettingsChange: (MarkerSettings) -> Unit,
    eraserSettings: EraserSettings,
    onEraserSettingsChange: (EraserSettings) -> Unit,
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
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expandedIndex by remember(toolMode) { mutableStateOf<Int?>(null) }
    // Switching directly between two slots first collapses the open panel, then
    // opens the requested one — [pendingIndex] holds the slot awaiting that
    // delayed open.
    var pendingIndex by remember(toolMode) { mutableStateOf<Int?>(null) }
    var lastExpanded by remember { mutableStateOf(0) }
    LaunchedEffect(expandedIndex) { expandedIndex?.let { lastExpanded = it } }
    LaunchedEffect(expandedIndex, pendingIndex) {
        if (expandedIndex == null && pendingIndex != null) {
            delay(PORTRAIT_PANEL_ANIM_MS.toLong())
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
                pendingIndex = i
                expandedIndex = null
            }
        }
    }

    val toolActive = toolMode != ToolMode.NONE

    Column(modifier = modifier) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars),
            shape = RectangleShape,
            tint = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PORTRAIT_BAR_PADDING_H),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "$currentPage / $totalPages",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = PORTRAIT_BAR_PAGE_PADDING),
                )
                Spacer(Modifier.weight(1f))
                ToolSelector(
                    toolMode = toolMode,
                    onToolModeChange = onToolModeChange,
                    orientation = RailOrientation.HORIZONTAL,
                )
                VerticalDivider()
                AnimatedContent(targetState = toolMode, label = "portrait-segment-b") { mode ->
                    if (mode != ToolMode.NONE) {
                        ToolSettingsIconStrip(
                            toolMode = mode,
                            penSettings = penSettings,
                            onPenSettingsChange = onPenSettingsChange,
                            markerSettings = markerSettings,
                            onMarkerSettingsChange = onMarkerSettingsChange,
                            eraserSettings = eraserSettings,
                            onEraserSettingsChange = onEraserSettingsChange,
                            orientation = RailOrientation.HORIZONTAL,
                            expandedIndex = expandedIndex,
                            onToggle = onSlotToggle,
                        )
                    } else {
                        SystemControls(
                            orientation = RailOrientation.HORIZONTAL,
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
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = toolActive && expandedIndex != null,
            enter = expandVertically(animationSpec = tween(PORTRAIT_PANEL_ANIM_MS), expandFrom = Alignment.Top) +
                fadeIn(animationSpec = tween(PORTRAIT_PANEL_ANIM_MS)),
            exit = shrinkVertically(animationSpec = tween(PORTRAIT_PANEL_ANIM_MS), shrinkTowards = Alignment.Top) +
                fadeOut(animationSpec = tween(PORTRAIT_PANEL_ANIM_MS)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = PORTRAIT_EXPANSION_TOP_PADDING),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = PORTRAIT_BAR_PADDING_H),
                horizontalArrangement = Arrangement.End,
            ) {
                GlassSurface(tint = MaterialTheme.colorScheme.secondaryContainer) {
                    ToolSettingsExpansionContent(
                        toolMode = toolMode,
                        penSettings = penSettings,
                        onPenSettingsChange = onPenSettingsChange,
                        markerSettings = markerSettings,
                        onMarkerSettingsChange = onMarkerSettingsChange,
                        eraserSettings = eraserSettings,
                        onEraserSettingsChange = onEraserSettingsChange,
                        orientation = RailOrientation.HORIZONTAL,
                        index = lastExpanded,
                    )
                }
            }
        }
    }
}

/** Thin vertical line separating the tool selector from the swappable segment. */
@Composable
private fun VerticalDivider() {
    Spacer(
        Modifier
            .padding(horizontal = PORTRAIT_DIVIDER_SPACING)
            .height(PORTRAIT_DIVIDER_HEIGHT)
            .width(PORTRAIT_DIVIDER_WIDTH)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = PORTRAIT_DIVIDER_ALPHA)),
    )
}

/** Open/close duration of the expansion panel; the slot-switch delay matches it. */
private const val PORTRAIT_PANEL_ANIM_MS = 180

private val PORTRAIT_BAR_PADDING_H = 4.dp
private val PORTRAIT_BAR_PAGE_PADDING = 8.dp
private val PORTRAIT_DIVIDER_SPACING = 8.dp
private val PORTRAIT_DIVIDER_HEIGHT = 24.dp
private val PORTRAIT_DIVIDER_WIDTH = 1.dp
private const val PORTRAIT_DIVIDER_ALPHA = 0.5f
private val PORTRAIT_EXPANSION_TOP_PADDING = 8.dp
