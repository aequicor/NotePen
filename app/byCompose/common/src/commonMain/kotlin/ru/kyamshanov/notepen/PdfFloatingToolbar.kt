package ru.kyamshanov.notepen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Floating PDF toolbar with the Pen / Eraser / Save / Zoom controls and an
 * expandable settings section that shows either [PenSettingsPanel] or
 * [EraserSettingsPanel] depending on [toolMode].
 *
 * Toggle semantics (AC-2 / AC-3) live in [nextToolModeOnToggle] — pure helper
 * unit-tested in `PdfFloatingToolbarLogicTest`. Re-tap on the active tool
 * deactivates it (returns to [ToolMode.NONE]); tap on the inactive tool
 * switches to it (mutual exclusion).
 *
 * Verifies AC-1, AC-2, AC-3, AC-4, AC-5, AC-18.
 */
@Composable
fun PdfFloatingToolbar(
    toolMode: ToolMode,
    onToolModeChange: (ToolMode) -> Unit,
    penSettings: PenSettings,
    onPenSettingsChange: (PenSettings) -> Unit,
    eraserSettings: EraserSettings,
    onEraserSettingsChange: (EraserSettings) -> Unit,
    hasAnnotations: Boolean,
    isSaving: Boolean,
    onSave: () -> Unit,
    scale: Int,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(TOOLBAR_CORNER_RADIUS),
        tonalElevation = TOOLBAR_TONAL_ELEVATION,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(TOOLBAR_PADDING),
            verticalAlignment = Alignment.Top,
        ) {
            // Left column — tool buttons + save + zoom.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ToolToggleButton(
                    icon = Icons.Default.Edit,
                    contentDescription = "Перо",
                    selected = toolMode == ToolMode.PEN,
                    onClick = {
                        onToolModeChange(nextToolModeOnToggle(toolMode, ToolMode.PEN))
                    },
                )

                ToolToggleButton(
                    icon = Icons.Default.Delete,
                    contentDescription = "Ластик",
                    selected = toolMode == ToolMode.ERASER,
                    onClick = {
                        onToolModeChange(nextToolModeOnToggle(toolMode, ToolMode.ERASER))
                    },
                )

                IconButton(
                    onClick = onSave,
                    enabled = hasAnnotations && !isSaving,
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(SAVE_PROGRESS_SIZE),
                            strokeWidth = SAVE_PROGRESS_STROKE,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Сохранить аннотации",
                            tint = if (hasAnnotations) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = SAVE_DISABLED_ALPHA)
                            },
                        )
                    }
                }

                IconButton(
                    onClick = onZoomIn,
                    enabled = scale < MAX_SCALE,
                ) {
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

                IconButton(
                    onClick = onZoomOut,
                    enabled = scale > MIN_SCALE,
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomOut,
                        contentDescription = "Уменьшить",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Right column — expandable settings panel; only present when a tool is active.
            // Recomposition-safe: when toolMode = NONE the entire branch is skipped.
            when (toolMode) {
                ToolMode.PEN -> {
                    Spacer(Modifier.width(TOOLBAR_PANEL_GAP))
                    PenSettingsPanel(
                        settings = penSettings,
                        onChange = onPenSettingsChange,
                        modifier = Modifier.width(SETTINGS_PANEL_WIDTH),
                    )
                }

                ToolMode.ERASER -> {
                    Spacer(Modifier.width(TOOLBAR_PANEL_GAP))
                    EraserSettingsPanel(
                        settings = eraserSettings,
                        onChange = onEraserSettingsChange,
                        modifier = Modifier.width(SETTINGS_PANEL_WIDTH),
                    )
                }

                ToolMode.NONE -> {
                    // No settings section — verifies AC-1 / TC-22.
                }
            }
        }
    }
}

@Composable
private fun ToolToggleButton(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/**
 * Pure state-mapping helper for [PdfFloatingToolbar] tool buttons:
 *  - tap on the active tool → return to [ToolMode.NONE] (toggle off);
 *  - tap on the inactive tool → switch to it (mutual exclusion);
 *  - explicit [ToolMode.NONE] request → always [ToolMode.NONE].
 *
 * Extracted so toggle semantics stay testable without compose-ui-test infra.
 * Verifies AC-2 / AC-3 toggle / mutual-exclusion semantics.
 */
fun nextToolModeOnToggle(current: ToolMode, requested: ToolMode): ToolMode = when {
    requested == ToolMode.NONE -> ToolMode.NONE
    current == requested -> ToolMode.NONE
    else -> requested
}

internal const val MIN_SCALE = 10
internal const val MAX_SCALE = 200

private val TOOLBAR_CORNER_RADIUS = 16.dp
private val TOOLBAR_TONAL_ELEVATION = 3.dp
private val TOOLBAR_PADDING = 4.dp
private val TOOLBAR_PANEL_GAP = 8.dp
private val SETTINGS_PANEL_WIDTH = 220.dp
private val SAVE_PROGRESS_SIZE = 24.dp
private val SAVE_PROGRESS_STROKE = 2.dp
private const val SAVE_DISABLED_ALPHA = 0.38f
