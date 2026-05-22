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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size as CanvasSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.LineWeight
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
import ru.kyamshanov.notepen.annotation.domain.model.BuiltinToolPresets
import ru.kyamshanov.notepen.annotation.domain.model.EraserMode
import ru.kyamshanov.notepen.annotation.domain.model.EraserPreset
import ru.kyamshanov.notepen.annotation.domain.model.MarkerPreset
import ru.kyamshanov.notepen.annotation.domain.model.MarkerSettings
import ru.kyamshanov.notepen.annotation.domain.model.PenPreset
import ru.kyamshanov.notepen.annotation.domain.model.StoredToolPresets
import ru.kyamshanov.notepen.annotation.domain.model.applyAlpha
import ru.kyamshanov.notepen.annotation.domain.model.applyMode
import ru.kyamshanov.notepen.annotation.domain.model.applyPreset
import ru.kyamshanov.notepen.annotation.domain.model.applyShape
import ru.kyamshanov.notepen.annotation.domain.model.applySize
import ru.kyamshanov.notepen.annotation.domain.model.applyStrokeWidth
import ru.kyamshanov.notepen.annotation.domain.model.isBuiltinPresetId
import ru.kyamshanov.notepen.annotation.domain.model.sliderPositionToStrokeWidth
import ru.kyamshanov.notepen.annotation.domain.model.strokeWidthToSliderPosition
import ru.kyamshanov.notepen.mainscreen.domain.model.generateUuid
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
            tint = slot.tint
                ?: if (isExpanded) MaterialTheme.colorScheme.primary
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
 * Builds the active tool's settings slots as a list of [WheelEntry] — one icon
 * button per slot. The [expandedIndex] slot is highlighted; tapping a slot calls
 * [onToggle] with its index. The caller owns the expanded state so the expansion
 * content can live in a sibling surface (the landscape "budding" side rail, or
 * the portrait below-bar strip).
 *
 * [expandedButtonModifier] is applied only to the currently-expanded slot so the
 * landscape budding panel can anchor itself to that button's position.
 */
internal fun toolSettingsSlotEntries(
    toolMode: ToolMode,
    penSettings: PenSettings,
    onPenSettingsChange: (PenSettings) -> Unit,
    markerSettings: MarkerSettings,
    onMarkerSettingsChange: (MarkerSettings) -> Unit,
    eraserSettings: EraserSettings,
    onEraserSettingsChange: (EraserSettings) -> Unit,
    expandedIndex: Int?,
    onToggle: (Int) -> Unit,
    expandedButtonModifier: Modifier = Modifier,
): List<WheelEntry> {
    val slots = slotsFor(
        toolMode,
        penSettings, onPenSettingsChange,
        markerSettings, onMarkerSettingsChange,
        eraserSettings, onEraserSettingsChange,
    )
    return slots.mapIndexed { i, slot ->
        WheelEntry("slot_$i") {
            SlotIconButton(
                slot = slot,
                isExpanded = expandedIndex == i,
                onToggle = { onToggle(i) },
                modifier = if (expandedIndex == i) expandedButtonModifier else Modifier,
            )
        }
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
        icon = NotePenIcons.ColorSwatch,
        contentDescription = "Цвет",
        tint = Color(settings.colorArgb.toInt()),
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
        icon = NotePenIcons.Opacity,
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

/* ---------------- presets zone ---------------- */

/**
 * Builds the active tool's preset chips (built-in + user) plus a trailing add
 * button as a list of [WheelEntry]. Empty for [ToolMode.NONE]. Tapping a chip
 * applies a full settings snapshot; the caller owns [presets] and persists
 * changes via [onPresetsChange].
 */
internal fun toolPresetEntries(
    toolMode: ToolMode,
    penSettings: PenSettings,
    onPenSettingsChange: (PenSettings) -> Unit,
    markerSettings: MarkerSettings,
    onMarkerSettingsChange: (MarkerSettings) -> Unit,
    eraserSettings: EraserSettings,
    onEraserSettingsChange: (EraserSettings) -> Unit,
    presets: StoredToolPresets,
    onPresetsChange: (StoredToolPresets) -> Unit,
    onPresetApplied: ((id: String) -> Unit)? = null,
): List<WheelEntry> = when (toolMode) {
    ToolMode.PEN -> {
        val all = BuiltinToolPresets.pen + presets.pen
        toolPresetWheelEntries(
            items = all.map {
                ToolPresetItem(
                    id = it.id,
                    deletable = !isBuiltinPresetId(it.id),
                    selected = it.settings == penSettings,
                    preview = { PenPresetPreview(it.settings) },
                )
            },
            addIcon = Icons.Default.Add,
            onApply = { id ->
                all.firstOrNull { it.id == id }?.let {
                    onPenSettingsChange(it.settings)
                    onPresetApplied?.invoke(id)
                }
            },
            onAdd = { onPresetsChange(presets.copy(pen = presets.pen + PenPreset(generateUuid(), penSettings))) },
            onDelete = { id -> onPresetsChange(presets.copy(pen = presets.pen.filterNot { it.id == id })) },
            showAdd = all.none { it.settings == penSettings },
        )
    }
    ToolMode.MARKER -> {
        val all = BuiltinToolPresets.marker + presets.marker
        toolPresetWheelEntries(
            items = all.map {
                ToolPresetItem(
                    id = it.id,
                    deletable = !isBuiltinPresetId(it.id),
                    selected = it.settings == markerSettings,
                    preview = { MarkerPresetPreview(it.settings) },
                )
            },
            addIcon = Icons.Default.Add,
            onApply = { id ->
                all.firstOrNull { it.id == id }?.let {
                    onMarkerSettingsChange(it.settings)
                    onPresetApplied?.invoke(id)
                }
            },
            onAdd = {
                onPresetsChange(presets.copy(marker = presets.marker + MarkerPreset(generateUuid(), markerSettings)))
            },
            onDelete = { id -> onPresetsChange(presets.copy(marker = presets.marker.filterNot { it.id == id })) },
            showAdd = all.none { it.settings == markerSettings },
        )
    }
    ToolMode.ERASER -> {
        val all = BuiltinToolPresets.eraser + presets.eraser
        toolPresetWheelEntries(
            items = all.map {
                ToolPresetItem(
                    id = it.id,
                    deletable = !isBuiltinPresetId(it.id),
                    selected = it.settings == eraserSettings,
                    preview = { EraserPresetPreview(it.settings) },
                )
            },
            addIcon = Icons.Default.Add,
            onApply = { id ->
                all.firstOrNull { it.id == id }?.let {
                    onEraserSettingsChange(it.settings)
                    onPresetApplied?.invoke(id)
                }
            },
            onAdd = {
                onPresetsChange(presets.copy(eraser = presets.eraser + EraserPreset(generateUuid(), eraserSettings)))
            },
            onDelete = { id -> onPresetsChange(presets.copy(eraser = presets.eraser.filterNot { it.id == id })) },
            showAdd = all.none { it.settings == eraserSettings },
        )
    }
    ToolMode.NONE -> emptyList()
}

/** Maps a slider position `[0..1]` to a preview diameter in [PREVIEW_MIN]..[PREVIEW_MAX]. */
private fun previewSize(position: Float): Dp =
    PREVIEW_MIN + (PREVIEW_MAX - PREVIEW_MIN) * position.coerceIn(0f, 1f)

/** Pen preview: a colour dot whose diameter encodes the stroke width. */
@Composable
private fun PenPresetPreview(settings: PenSettings) {
    val pos = strokeWidthToSliderPosition(
        settings.strokeWidth,
        PenSettings.MIN_STROKE_WIDTH,
        PenSettings.MAX_STROKE_WIDTH,
    )
    Box(
        Modifier.size(previewSize(pos)).clip(CircleShape)
            .background(Color(settings.colorArgb.toInt())),
    )
}

/** Marker preview: a translucent capsule whose thickness encodes the stroke width. */
@Composable
private fun MarkerPresetPreview(settings: MarkerSettings) {
    val pos = strokeWidthToSliderPosition(
        settings.strokeWidth,
        MarkerSettings.MIN_STROKE_WIDTH,
        MarkerSettings.MAX_STROKE_WIDTH,
    )
    Box(
        Modifier.width(PREVIEW_MAX).height(previewSize(pos)).clip(RoundedCornerShape(percent = 50))
            .background(Color(settings.colorArgb.toInt())),
    )
}

/** Eraser preview: the actual shape, sized by the eraser size; filled for OBJECT mode. */
@Composable
private fun EraserPresetPreview(settings: EraserSettings) {
    val pos = (settings.sizeNormalized - EraserSettings.MIN_SIZE_NORMALIZED) /
        (EraserSettings.MAX_SIZE_NORMALIZED - EraserSettings.MIN_SIZE_NORMALIZED)
    val shape = if (settings.shape == EraserShape.CIRCLE) CircleShape else RoundedCornerShape(2.dp)
    val outline = MaterialTheme.colorScheme.onSecondaryContainer
    Box(
        Modifier.size(previewSize(pos)).clip(shape)
            .background(if (settings.mode == EraserMode.OBJECT) outline.copy(alpha = ERASER_FILL_ALPHA) else Color.Transparent)
            .border(PRESET_PREVIEW_BORDER, outline, shape),
    )
}

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
private val RAIL_ICON_BUTTON_SIZE = 40.dp
private val CHIP_GAP = 6.dp
private val PREVIEW_MIN = 8.dp
private val PREVIEW_MAX = 24.dp
private val PRESET_PREVIEW_BORDER = 1.5.dp
private const val ERASER_FILL_ALPHA = 0.3f
