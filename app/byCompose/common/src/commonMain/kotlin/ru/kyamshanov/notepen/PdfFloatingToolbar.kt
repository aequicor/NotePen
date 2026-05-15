package ru.kyamshanov.notepen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
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
 * Vertical floating PDF toolbar with the Pen / Eraser / Save / Zoom controls.
 *
 * Tool-settings (color / thickness / alpha for pen, shape / size for eraser)
 * are now rendered as a separate [ToolSettingsFloatingPanel] docked at
 * BottomCenter — see Step 6 rework. This composable owns only the icon column.
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
        Column(
            modifier = Modifier.padding(TOOLBAR_PADDING),
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
                icon = Icons.Default.CleaningServices,
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

internal const val MIN_SCALE = 25
internal const val MAX_SCALE = 800

private val TOOLBAR_CORNER_RADIUS = 16.dp
private val TOOLBAR_TONAL_ELEVATION = 3.dp
private val TOOLBAR_PADDING = 4.dp
private val SAVE_PROGRESS_SIZE = 24.dp
private val SAVE_PROGRESS_STROKE = 2.dp
private const val SAVE_DISABLED_ALPHA = 0.38f
