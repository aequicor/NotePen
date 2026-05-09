package ru.kyamshanov.notepen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Panel with shape toggle (CIRCLE / SQUARE) and a size slider for the eraser
 * tool. State-mapping is delegated to pure helpers
 * (`EraserSettings.applyShape / applySize`) so the visible composable stays
 * a thin shell over them.
 *
 * Verifies AC-10, AC-11 (semantics); AC-18 (Material 3 tokens, dp constants);
 * EC-10 / EC-11 (clamping).
 */
@Composable
fun EraserSettingsPanel(
    settings: EraserSettings,
    onChange: (EraserSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(ERASER_PANEL_PADDING),
    ) {
        Text(
            text = "Форма",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(ERASER_SHAPE_GAP),
            modifier = Modifier.padding(top = ERASER_SHAPE_TOP_PADDING),
        ) {
            FilterChip(
                selected = settings.shape == EraserShape.CIRCLE,
                onClick = { onChange(settings.applyShape(EraserShape.CIRCLE)) },
                label = { Text("Круг") },
            )
            FilterChip(
                selected = settings.shape == EraserShape.SQUARE,
                onClick = { onChange(settings.applyShape(EraserShape.SQUARE)) },
                label = { Text("Квадрат") },
            )
        }

        Spacer(Modifier.height(ERASER_PANEL_INNER_SPACING))

        Text(
            text = "Размер",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Slider(
            value = settings.sizeNormalized,
            onValueChange = { onChange(settings.applySize(it)) },
            valueRange = EraserSettings.MIN_SIZE_NORMALIZED..EraserSettings.MAX_SIZE_NORMALIZED,
        )
    }
}

private val ERASER_PANEL_PADDING = 8.dp
private val ERASER_PANEL_INNER_SPACING = 8.dp
private val ERASER_SHAPE_GAP = 8.dp
private val ERASER_SHAPE_TOP_PADDING = 4.dp
