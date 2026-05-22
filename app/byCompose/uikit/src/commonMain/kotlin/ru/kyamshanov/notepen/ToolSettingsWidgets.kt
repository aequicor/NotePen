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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size as CanvasSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.kyamshanov.notepen.annotation.domain.model.sliderPositionToStrokeWidth
import ru.kyamshanov.notepen.annotation.domain.model.strokeWidthToSliderPosition

/** Axis along which a tool rail / settings strip lays out its items. */
public enum class RailOrientation { VERTICAL, HORIZONTAL }

/**
 * One settings control of a tool: an [icon] shown in the collapsed strip and the
 * [content] shown when the slot is expanded, laid out along the given [RailOrientation].
 */
public class SlotItem(
    public val icon: ImageVector,
    public val contentDescription: String,
    /** Renders the expanded content for the requested [RailOrientation]. */
    public val content: @Composable (RailOrientation) -> Unit,
    /**
     * Forces the collapsed [icon]'s tint instead of the theme default. Used by the
     * colour slot to render its glyph in the currently selected colour.
     */
    public val tint: Color? = null,
)

/** Row/column of color-preset dots; [isSelected] marks the active one. */
@Composable
public fun ColorPresets(
    presets: List<Long>,
    isSelected: (Long) -> Boolean,
    onPick: (Long) -> Unit,
    orientation: RailOrientation,
) {
    when (orientation) {
        RailOrientation.HORIZONTAL -> Row(horizontalArrangement = Arrangement.spacedBy(PRESET_GAP)) {
            presets.forEach { ColorPresetDot(it, isSelected(it)) { onPick(it) } }
        }
        RailOrientation.VERTICAL -> Column(
            verticalArrangement = Arrangement.spacedBy(PRESET_GAP),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            presets.forEach { ColorPresetDot(it, isSelected(it)) { onPick(it) } }
        }
    }
}

@Composable
private fun ColorPresetDot(
    presetArgb: Long,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.outlineVariant
    Spacer(
        modifier = Modifier
            .size(PRESET_SIZE)
            .clip(CircleShape)
            .background(Color(presetArgb.toInt()))
            .border(
                width = if (selected) PRESET_BORDER_SELECTED else PRESET_BORDER_DEFAULT,
                color = borderColor,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
    )
}

/**
 * Slider for stroke width that:
 * - operates on a perceptually-uniform log scale (equal-feeling steps at every position);
 * - reports/accepts width as a fraction of page width.
 */
@Composable
public fun StrokeWidthSlider(
    orientation: RailOrientation,
    strokeWidth: Float,
    min: Float,
    max: Float,
    onWidthChange: (Float) -> Unit,
) {
    OrientedSlider(
        orientation = orientation,
        value = strokeWidthToSliderPosition(strokeWidth, min, max),
        onValueChange = { t -> onWidthChange(sliderPositionToStrokeWidth(t, min, max)) },
        valueRange = 0f..1f,
    )
}

/** Material 3 slider laid out along [orientation]. */
@Composable
public fun OrientedSlider(
    orientation: RailOrientation,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
) {
    when (orientation) {
        RailOrientation.HORIZONTAL -> HorizontalAdjustableSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            width = SLIDER_LENGTH,
        )
        RailOrientation.VERTICAL -> VerticalAdjustableSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            length = SLIDER_LENGTH,
        )
    }
}

/**
 * Standard horizontal Material 3 slider with the inactive track always visible
 * end-to-end.
 *
 * We use a custom track instead of [SliderDefaults.Track] because Material 3 renders
 * the active and inactive segments separately: at min/max the zero-length segment
 * simply disappears (a round-cap on a zero-length stroke draws nothing). Drawing a
 * full-width background first and then the active portion on top keeps both ends
 * visible at all positions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HorizontalAdjustableSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    width: Dp,
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f)
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        modifier = Modifier.width(width),
        colors = SliderDefaults.colors(
            thumbColor = activeColor,
            activeTrackColor = activeColor,
            inactiveTrackColor = inactiveColor,
        ),
        track = { state ->
            val fraction = ((state.value - state.valueRange.start) /
                (state.valueRange.endInclusive - state.valueRange.start)).coerceIn(0f, 1f)
            Canvas(Modifier.fillMaxWidth().height(SLIDER_TRACK_HEIGHT)) {
                val r = CornerRadius(size.height / 2f)
                drawRoundRect(color = inactiveColor, cornerRadius = r)
                if (fraction > 0f) {
                    drawRoundRect(
                        color = activeColor,
                        size = CanvasSize(size.width * fraction, size.height),
                        cornerRadius = r,
                    )
                }
            }
        },
    )
}

/**
 * Vertical slider built by rotating Material 3 [Slider] -90° around its
 * top-left corner and swapping width/height in a custom [layout] modifier so
 * the parent sees a [VERT_SLIDER_TRACK_BREADTH]-wide × [length]-tall node.
 * Bottom = min, top = max — natural "more = up" mental model. Material 3 has
 * no first-class vertical slider; this rotation pattern is the standard
 * workaround until one ships.
 *
 * Uses the same full-background custom track as [HorizontalAdjustableSlider]
 * so the inactive portion stays visible when the slider is at min or max.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VerticalAdjustableSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    length: Dp,
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f)
    Box(
        modifier = Modifier.size(width = VERT_SLIDER_TRACK_BREADTH, height = length),
        contentAlignment = Alignment.Center,
    ) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = activeColor,
                activeTrackColor = activeColor,
                inactiveTrackColor = inactiveColor,
            ),
            track = { state ->
                val fraction = ((state.value - state.valueRange.start) /
                    (state.valueRange.endInclusive - state.valueRange.start)).coerceIn(0f, 1f)
                Canvas(Modifier.fillMaxWidth().height(SLIDER_TRACK_HEIGHT)) {
                    val r = CornerRadius(size.height / 2f)
                    drawRoundRect(color = inactiveColor, cornerRadius = r)
                    if (fraction > 0f) {
                        drawRoundRect(
                            color = activeColor,
                            size = CanvasSize(size.width * fraction, size.height),
                            cornerRadius = r,
                        )
                    }
                }
            },
            modifier = Modifier
                .graphicsLayer {
                    rotationZ = 270f
                    transformOrigin = TransformOrigin(0f, 0f)
                }
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(
                        Constraints(
                            minWidth = constraints.minHeight,
                            maxWidth = constraints.maxHeight,
                            minHeight = constraints.minWidth,
                            maxHeight = constraints.maxWidth,
                        ),
                    )
                    layout(placeable.height, placeable.width) {
                        placeable.place(-placeable.width, 0)
                    }
                },
        )
    }
}

private val SLIDER_LENGTH = 140.dp
private val SLIDER_TRACK_HEIGHT = 12.dp
private val VERT_SLIDER_TRACK_BREADTH = 40.dp
private val PRESET_GAP = 8.dp
private val PRESET_SIZE = 28.dp
private val PRESET_BORDER_DEFAULT = 1.dp
private val PRESET_BORDER_SELECTED = 2.dp
