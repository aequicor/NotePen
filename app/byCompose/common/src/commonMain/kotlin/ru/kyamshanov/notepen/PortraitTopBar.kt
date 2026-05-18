package ru.kyamshanov.notepen

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import ru.kyamshanov.notepen.ui.glass.GlassSurface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Full-width top bar for portrait mode.
 *
 * Contains, left to right: back button, page counter, spacer, tool toggle
 * buttons (Pen / Marker / Eraser / PencilMode / Magnifier / Thumbnails),
 * Save, Export, and Zoom controls. Replaces the separate floating toolbar and
 * page-indicator airbar in portrait orientation.
 *
 * Tool-settings are NOT shown here; when a tool is selected, the caller renders
 * a settings strip directly below this bar.
 */
@Composable
fun PortraitTopBar(
    currentPage: Int,
    totalPages: Int,
    toolMode: ToolMode,
    onToolModeChange: (ToolMode) -> Unit,
    hasAnnotations: Boolean,
    isSaving: Boolean,
    isExporting: Boolean,
    onSave: () -> Unit,
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
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassSurface(
        modifier = modifier
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

            ToolToggleButton(
                icon = Icons.Default.Edit,
                contentDescription = "Перо",
                selected = toolMode == ToolMode.PEN,
                onClick = { onToolModeChange(nextToolModeOnToggle(toolMode, ToolMode.PEN)) },
            )
            ToolToggleButton(
                icon = Icons.Default.Brush,
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

            IconButton(
                onClick = onSave,
                enabled = hasAnnotations && !isSaving,
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(PORTRAIT_BAR_PROGRESS_SIZE),
                        strokeWidth = PORTRAIT_BAR_PROGRESS_STROKE,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = "Сохранить аннотации",
                        tint = if (hasAnnotations) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = PORTRAIT_BAR_DISABLED_ALPHA)
                        },
                    )
                }
            }

            IconButton(
                onClick = onExport,
                enabled = hasAnnotations && !isExporting,
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(PORTRAIT_BAR_PROGRESS_SIZE),
                        strokeWidth = PORTRAIT_BAR_PROGRESS_STROKE,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = "Экспортировать в PDF",
                        tint = if (hasAnnotations) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = PORTRAIT_BAR_DISABLED_ALPHA)
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
    }
}

private val PORTRAIT_BAR_PADDING_H = 4.dp
private val PORTRAIT_BAR_PAGE_PADDING = 8.dp
private val PORTRAIT_BAR_PROGRESS_SIZE = 24.dp
private val PORTRAIT_BAR_PROGRESS_STROKE = 2.dp
private const val PORTRAIT_BAR_DISABLED_ALPHA = 0.38f
