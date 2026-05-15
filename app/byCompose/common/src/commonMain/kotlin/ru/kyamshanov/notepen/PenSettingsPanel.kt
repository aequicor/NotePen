package ru.kyamshanov.notepen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ru.kyamshanov.notepen.annotation.domain.model.applyAlpha
import ru.kyamshanov.notepen.annotation.domain.model.applyPreset
import ru.kyamshanov.notepen.annotation.domain.model.applyStrokeWidth

/**
 * Panel with thickness slider, alpha slider and a horizontal lane of color presets
 * for the pen tool. State-mapping is delegated to pure helpers
 * (`PenSettings.applyPreset / applyAlpha / applyStrokeWidth`) so the visible
 * composable stays a thin shell over them.
 *
 * Verifies AC-6, AC-7, AC-8 (semantics); AC-18 (Material 3 tokens, dp constants).
 */
@Composable
fun PenSettingsPanel(
    settings: PenSettings,
    onChange: (PenSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(PEN_PANEL_PADDING),
    ) {
        Text(
            text = "Толщина",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Slider(
            value = settings.strokeWidth,
            onValueChange = { onChange(settings.applyStrokeWidth(it)) },
            valueRange = PenSettings.MIN_STROKE_WIDTH..PenSettings.MAX_STROKE_WIDTH,
        )

        Spacer(Modifier.height(PEN_PANEL_INNER_SPACING))

        Text(
            text = "Прозрачность",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Slider(
            value = settings.alpha,
            onValueChange = { onChange(settings.applyAlpha(it)) },
            valueRange = 0f..1f,
        )

        Spacer(Modifier.height(PEN_PANEL_INNER_SPACING))

        Text(
            text = "Цвет",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(PEN_PRESET_GAP),
            modifier = Modifier.padding(top = PEN_PRESET_TOP_PADDING),
        ) {
            items(PenSettings.PRESET_COLORS) { presetArgb ->
                val presetColor = Color(presetArgb.toInt())
                val isSelected = (presetArgb and 0x00FFFFFFL) == (settings.colorArgb and 0x00FFFFFFL)
                val borderColor = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                }
                Spacer(
                    modifier = Modifier
                        .size(PEN_PRESET_SIZE)
                        .clip(CircleShape)
                        .background(presetColor)
                        .border(
                            width = if (isSelected) PEN_PRESET_BORDER_SELECTED else PEN_PRESET_BORDER_DEFAULT,
                            color = borderColor,
                            shape = CircleShape,
                        )
                        .clickable { onChange(settings.applyPreset(presetArgb)) },
                )
            }
        }
    }
}

private val PEN_PANEL_PADDING = 8.dp
private val PEN_PANEL_INNER_SPACING = 8.dp
private val PEN_PRESET_GAP = 8.dp
private val PEN_PRESET_TOP_PADDING = 4.dp
private val PEN_PRESET_SIZE = 28.dp
private val PEN_PRESET_BORDER_DEFAULT = 1.dp
private val PEN_PRESET_BORDER_SELECTED = 2.dp
