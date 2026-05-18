package ru.kyamshanov.notepen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.kyamshanov.notepen.annotation.domain.model.EraserSettings
import ru.kyamshanov.notepen.annotation.domain.model.MarkerSettings
import ru.kyamshanov.notepen.annotation.domain.model.PenSettings

/**
 * Combined top airbar for portrait mode that merges the page counter and the
 * tool settings controls into a single horizontal strip.
 *
 * In landscape the two elements are rendered separately ([PageIndicatorAirbar]
 * + [ToolSettingsFloatingPanel]). In portrait, merging them into one row saves
 * vertical space and avoids a second UI strip below the status bar.
 *
 * The settings section is shown only when a tool is active ([ToolMode.PEN],
 * [ToolMode.MARKER] or [ToolMode.ERASER]); it animates in/out horizontally so
 * the page counter stays visible even when no tool is selected.
 */
@Composable
fun PortraitTopAirbar(
    currentPage: Int,
    totalPages: Int,
    toolMode: ToolMode,
    penSettings: PenSettings,
    onPenSettingsChange: (PenSettings) -> Unit,
    markerSettings: MarkerSettings,
    onMarkerSettingsChange: (MarkerSettings) -> Unit,
    eraserSettings: EraserSettings,
    onEraserSettingsChange: (EraserSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasSettings = toolMode == ToolMode.PEN ||
        toolMode == ToolMode.MARKER ||
        toolMode == ToolMode.ERASER

    Surface(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = PORTRAIT_AIRBAR_TOP_PADDING),
        shape = RoundedCornerShape(PORTRAIT_AIRBAR_CORNER_RADIUS),
        color = MaterialTheme.colorScheme.surface.copy(alpha = PORTRAIT_AIRBAR_GLASS_ALPHA),
        tonalElevation = PORTRAIT_AIRBAR_TONAL_ELEVATION,
        shadowElevation = PORTRAIT_AIRBAR_SHADOW_ELEVATION,
        border = BorderStroke(
            width = PORTRAIT_AIRBAR_BORDER_WIDTH,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = PORTRAIT_AIRBAR_BORDER_ALPHA),
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                horizontal = PORTRAIT_AIRBAR_PADDING_H,
                vertical = PORTRAIT_AIRBAR_PADDING_V,
            ),
        ) {
            Text(
                text = "Страница $currentPage / $totalPages",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            AnimatedVisibility(
                visible = hasSettings,
                enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
                exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(PORTRAIT_AIRBAR_DIVIDER_SPACING))
                    Spacer(
                        Modifier
                            .height(PORTRAIT_AIRBAR_DIVIDER_HEIGHT)
                            .width(PORTRAIT_AIRBAR_DIVIDER_WIDTH)
                            .background(
                                MaterialTheme.colorScheme.outlineVariant.copy(
                                    alpha = PORTRAIT_AIRBAR_BORDER_ALPHA,
                                ),
                            ),
                    )
                    Spacer(Modifier.width(PORTRAIT_AIRBAR_DIVIDER_SPACING))
                    ToolSettingsFloatingPanel(
                        toolMode = toolMode,
                        penSettings = penSettings,
                        onPenSettingsChange = onPenSettingsChange,
                        markerSettings = markerSettings,
                        onMarkerSettingsChange = onMarkerSettingsChange,
                        eraserSettings = eraserSettings,
                        onEraserSettingsChange = onEraserSettingsChange,
                        vertical = false,
                        atTop = true,
                        applyInsets = false,
                    )
                }
            }
        }
    }
}

private val PORTRAIT_AIRBAR_CORNER_RADIUS = 12.dp
private val PORTRAIT_AIRBAR_TONAL_ELEVATION = 6.dp
private val PORTRAIT_AIRBAR_SHADOW_ELEVATION = 4.dp
private val PORTRAIT_AIRBAR_BORDER_WIDTH = 1.dp
private val PORTRAIT_AIRBAR_PADDING_H = 16.dp
private val PORTRAIT_AIRBAR_PADDING_V = 8.dp
private val PORTRAIT_AIRBAR_TOP_PADDING = 8.dp
private val PORTRAIT_AIRBAR_DIVIDER_SPACING = 12.dp
private val PORTRAIT_AIRBAR_DIVIDER_HEIGHT = 20.dp
private val PORTRAIT_AIRBAR_DIVIDER_WIDTH = 1.dp
private const val PORTRAIT_AIRBAR_GLASS_ALPHA = 0.85f
private const val PORTRAIT_AIRBAR_BORDER_ALPHA = 0.5f
