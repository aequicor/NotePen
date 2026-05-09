package ru.kyamshanov.notepen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Floating glass pill displaying "Страница X / N" at the top of the PDF viewer.
 *
 * Glass style matches [ToolSettingsFloatingPanel]: semi-transparent surface (0.85α),
 * outlineVariant border, tonalElevation = 6.dp.
 *
 * Positioning is the caller's responsibility (Alignment.TopCenter in DetailsContent).
 * The composable must not be shown when [totalPages] == 0 — caller guards this (AC-4, EC-2).
 */
@Composable
fun PageIndicatorAirbar(
    currentPage: Int,
    totalPages: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(AIRBAR_CORNER_RADIUS),
        color = MaterialTheme.colorScheme.surface.copy(alpha = AIRBAR_GLASS_ALPHA),
        tonalElevation = AIRBAR_TONAL_ELEVATION,
        shadowElevation = AIRBAR_SHADOW_ELEVATION,
        border = BorderStroke(
            width = AIRBAR_BORDER_WIDTH,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = AIRBAR_BORDER_ALPHA),
        ),
    ) {
        Text(
            text = "Страница $currentPage / $totalPages",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(
                horizontal = AIRBAR_PADDING_H,
                vertical = AIRBAR_PADDING_V,
            ),
        )
    }
}

private val AIRBAR_CORNER_RADIUS = 12.dp
private val AIRBAR_TONAL_ELEVATION = 6.dp
private val AIRBAR_SHADOW_ELEVATION = 4.dp
private val AIRBAR_BORDER_WIDTH = 1.dp
private val AIRBAR_PADDING_H = 16.dp
private val AIRBAR_PADDING_V = 8.dp
private const val AIRBAR_GLASS_ALPHA = 0.85f
private const val AIRBAR_BORDER_ALPHA = 0.5f
