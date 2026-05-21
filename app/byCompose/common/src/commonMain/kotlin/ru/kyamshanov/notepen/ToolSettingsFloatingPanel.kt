package ru.kyamshanov.notepen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size as CanvasSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.LineWeight
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.kyamshanov.notepen.annotation.domain.model.EraserMode
import ru.kyamshanov.notepen.annotation.domain.model.MarkerSettings
import ru.kyamshanov.notepen.annotation.domain.model.applyAlpha
import ru.kyamshanov.notepen.annotation.domain.model.applyMode
import ru.kyamshanov.notepen.annotation.domain.model.applyPreset
import ru.kyamshanov.notepen.annotation.domain.model.applyShape
import ru.kyamshanov.notepen.annotation.domain.model.applySize
import ru.kyamshanov.notepen.annotation.domain.model.applyStrokeWidth
import ru.kyamshanov.notepen.annotation.domain.model.sliderPositionToStrokeWidth
import ru.kyamshanov.notepen.tools.marker.markerSlots

/**
 * Greedy left-to-right layout decision for adaptive settings rows.
 *
 * For each slot, determines whether it fits inline (true) or must collapse to
 * an icon-button (false). Budget starts at `maxWidth - paddingPx` and is
 * consumed slot by slot; a gap is counted before each slot except the first.
 * A collapsing slot consumes only `iconButtonWidthPx` instead of its natural width.
 */
internal fun greedyFit(
    naturalWidths: List<Int>,
    maxWidth: Int,
    gapPx: Int,
    paddingPx: Int,
    iconButtonWidthPx: Int,
): BooleanArray {
    val fits = BooleanArray(naturalWidths.size)
    var budget = maxWidth - paddingPx
    naturalWidths.forEachIndexed { i, w ->
        val gap = if (i > 0) gapPx else 0
        if (budget >= w + gap) {
            fits[i] = true
            budget -= w + gap
        } else {
            fits[i] = false
            budget -= iconButtonWidthPx + gap
        }
    }
    return fits
}

@Composable
private fun SlotIconButton(
    slot: SlotItem,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onToggle,
        modifier = modifier.size(RAIL_ICON_BUTTON_SIZE),
    ) {
        Icon(
            imageVector = slot.icon,
            contentDescription = slot.contentDescription,
            tint = if (isExpanded) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/** Builds the settings slots for [toolMode] (empty list for [ToolMode.NONE]). */
private fun slotsFor(
    toolMode: ToolMode,
    penSettings: PenSettings,
    onPenSettingsChange: (PenSettings) -> Unit,
    markerSettings: MarkerSettings,
    onMarkerSettingsChange: (MarkerSettings) -> Unit,
    eraserSettings: EraserSettings,
    onEraserSettingsChange: (EraserSettings) -> Unit,
): List<SlotItem> = when (toolMode) {
    ToolMode.PEN -> penSlots(penSettings, onPenSettingsChange)
    ToolMode.MARKER -> markerSlots(markerSettings, onMarkerSettingsChange)
    ToolMode.ERASER -> eraserSlots(eraserSettings, onEraserSettingsChange)
    ToolMode.NONE -> emptyList()
}

/**
 * Compact strip of slot icon-buttons for the active tool's settings.
 *
 * Icons are laid out along the rail axis ([orientation]); the [expandedIndex]
 * slot is highlighted. Tapping a slot calls [onToggle] with its index — the
 * caller owns the expanded state so the expansion content can live in a sibling
 * surface (the landscape "budding" side rail, or the portrait below-bar strip).
 */
@Composable
internal fun ToolSettingsIconStrip(
    toolMode: ToolMode,
    penSettings: PenSettings,
    onPenSettingsChange: (PenSettings) -> Unit,
    markerSettings: MarkerSettings,
    onMarkerSettingsChange: (MarkerSettings) -> Unit,
    eraserSettings: EraserSettings,
    onEraserSettingsChange: (EraserSettings) -> Unit,
    orientation: RailOrientation,
    expandedIndex: Int?,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier,
    expandedButtonModifier: Modifier = Modifier,
) {
    val slots = slotsFor(
        toolMode,
        penSettings, onPenSettingsChange,
        markerSettings, onMarkerSettingsChange,
        eraserSettings, onEraserSettingsChange,
    )
    val buttons: @Composable () -> Unit = {
        slots.forEachIndexed { i, slot ->
            SlotIconButton(
                slot = slot,
                isExpanded = expandedIndex == i,
                onToggle = { onToggle(i) },
                modifier = if (expandedIndex == i) expandedButtonModifier else Modifier,
            )
        }
    }
    when (orientation) {
        RailOrientation.VERTICAL -> Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(RAIL_ITEM_GAP),
        ) { buttons() }
        RailOrientation.HORIZONTAL -> Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RAIL_ITEM_GAP),
        ) { buttons() }
    }
}

/**
 * Renders the expanded content (color picker / slider / chips) for the slot at
 * [index] of the active tool. No-op when [index] is out of range.
 */
@Composable
internal fun ToolSettingsExpansionContent(
    toolMode: ToolMode,
    penSettings: PenSettings,
    onPenSettingsChange: (PenSettings) -> Unit,
    markerSettings: MarkerSettings,
    onMarkerSettingsChange: (MarkerSettings) -> Unit,
    eraserSettings: EraserSettings,
    onEraserSettingsChange: (EraserSettings) -> Unit,
    orientation: RailOrientation,
    index: Int,
    modifier: Modifier = Modifier,
) {
    val slots = slotsFor(
        toolMode,
        penSettings, onPenSettingsChange,
        markerSettings, onMarkerSettingsChange,
        eraserSettings, onEraserSettingsChange,
    )
    if (index in slots.indices) {
        Box(
            modifier = modifier.padding(
                horizontal = PANEL_EXPANSION_PADDING_H,
                vertical = PANEL_EXPANSION_PADDING_V,
            ),
        ) {
            slots[index].content(orientation)
        }
    }
}

/* ---------------- slot factories ---------------- */

private fun penSlots(
    settings: PenSettings,
    onChange: (PenSettings) -> Unit,
): List<SlotItem> = listOf(
    SlotItem(
        icon = Icons.Default.Palette,
        contentDescription = "Цвет",
        content = { orientation ->
            ColorPresets(
                presets = PenSettings.PRESET_COLORS,
                isSelected = { (it and 0x00FFFFFFL) == (settings.colorArgb and 0x00FFFFFFL) },
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
                min = PenSettings.MIN_STROKE_WIDTH,
                max = PenSettings.MAX_STROKE_WIDTH,
                onWidthChange = { onChange(settings.applyStrokeWidth(it)) },
            )
        },
    ),
    SlotItem(
        icon = Icons.Default.Opacity,
        contentDescription = "Прозрачность",
        content = { orientation ->
            OrientedSlider(
                orientation = orientation,
                value = settings.alpha,
                onValueChange = { onChange(settings.applyAlpha(it)) },
                valueRange = 0f..1f,
            )
        },
    ),
)

private fun eraserSlots(
    settings: EraserSettings,
    onChange: (EraserSettings) -> Unit,
): List<SlotItem> = listOf(
    SlotItem(
        icon = Icons.Default.Category,
        contentDescription = "Режим",
        content = { orientation ->
            OrientedChipGroup(orientation = orientation) {
                EraserChip(
                    selected = settings.mode == EraserMode.POINT,
                    label = "Точки",
                    onClick = { onChange(settings.applyMode(EraserMode.POINT)) },
                )
                EraserChip(
                    selected = settings.mode == EraserMode.OBJECT,
                    label = "Штрих",
                    onClick = { onChange(settings.applyMode(EraserMode.OBJECT)) },
                )
            }
        },
    ),
    SlotItem(
        icon = Icons.Default.RadioButtonUnchecked,
        contentDescription = "Форма",
        content = { orientation ->
            OrientedChipGroup(orientation = orientation) {
                EraserChip(
                    selected = settings.shape == EraserShape.CIRCLE,
                    label = "Круг",
                    onClick = { onChange(settings.applyShape(EraserShape.CIRCLE)) },
                )
                EraserChip(
                    selected = settings.shape == EraserShape.SQUARE,
                    label = "Квадрат",
                    onClick = { onChange(settings.applyShape(EraserShape.SQUARE)) },
                )
            }
        },
    ),
    SlotItem(
        icon = Icons.Default.PhotoSizeSelectLarge,
        contentDescription = "Размер",
        content = { orientation ->
            OrientedSlider(
                orientation = orientation,
                value = settings.sizeNormalized,
                onValueChange = { onChange(settings.applySize(it)) },
                valueRange = EraserSettings.MIN_SIZE_NORMALIZED..EraserSettings.MAX_SIZE_NORMALIZED,
            )
        },
    ),
)

/* ---------------- oriented expanded-content widgets ---------------- */

@Composable
private fun OrientedChipGroup(
    orientation: RailOrientation,
    content: @Composable () -> Unit,
) {
    when (orientation) {
        RailOrientation.HORIZONTAL -> Row(
            horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) { content() }
        RailOrientation.VERTICAL -> Column(
            verticalArrangement = Arrangement.spacedBy(CHIP_GAP),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    }
}

@Composable
private fun EraserChip(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedContainerColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.15f),
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    )
}

private val PANEL_EXPANSION_PADDING_H = 12.dp
private val PANEL_EXPANSION_PADDING_V = 8.dp
private val RAIL_ITEM_GAP = 4.dp
private val RAIL_ICON_BUTTON_SIZE = 40.dp
private val CHIP_GAP = 6.dp
