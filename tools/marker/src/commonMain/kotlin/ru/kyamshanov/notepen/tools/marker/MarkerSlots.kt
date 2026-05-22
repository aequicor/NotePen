package ru.kyamshanov.notepen.tools.marker

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LineWeight
import androidx.compose.ui.graphics.Color
import ru.kyamshanov.notepen.ColorPresets
import ru.kyamshanov.notepen.NotePenIcons
import ru.kyamshanov.notepen.SlotItem
import ru.kyamshanov.notepen.StrokeWidthSlider
import ru.kyamshanov.notepen.annotation.domain.model.MarkerSettings
import ru.kyamshanov.notepen.annotation.domain.model.applyPreset
import ru.kyamshanov.notepen.annotation.domain.model.applyStrokeWidth

/** Settings slots for the marker tool: color presets and stroke width. */
public fun markerSlots(
    settings: MarkerSettings,
    onChange: (MarkerSettings) -> Unit,
): List<SlotItem> = listOf(
    SlotItem(
        icon = NotePenIcons.ColorSwatch,
        contentDescription = "Цвет",
        tint = Color(settings.colorArgb.toInt()),
        content = { orientation ->
            ColorPresets(
                presets = MarkerSettings.PRESET_COLORS,
                isSelected = { it == settings.colorArgb },
                onPick = { onChange(settings.applyPreset(it)) },
                orientation = orientation,
            )
        },
    ),
    SlotItem(
        icon = Icons.Default.LineWeight,
        contentDescription = "Толщина",
        content = { orientation ->
            StrokeWidthSlider(
                orientation = orientation,
                strokeWidth = settings.strokeWidth,
                min = MarkerSettings.MIN_STROKE_WIDTH,
                max = MarkerSettings.MAX_STROKE_WIDTH,
                onWidthChange = { onChange(settings.applyStrokeWidth(it)) },
            )
        },
    ),
)
