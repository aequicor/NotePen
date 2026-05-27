package ru.kyamshanov.notepen.blur

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Floating "frosted glass" surface used for tool rail, tool-settings panel and
 * the portrait top airbar. Centralises the look so all three surfaces stay in
 * sync: lowered fill alpha over a chosen tint, a subtle outline border and a
 * backdrop blur sampled from [LocalHazeState].
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(GlassCornerRadius),
    tint: Color = MaterialTheme.colorScheme.surface,
    fillAlpha: Float = GLASS_FILL_ALPHA_OPAQUE,
    content: @Composable () -> Unit,
) {
    val blurOn = LocalBlurEnabled.current
    Surface(
        modifier = modifier.glassBackdrop(shape = shape, tint = tint),
        shape = shape,
        // Blur on: the haze material already supplies the frosted tint, so layer only a
        // faint extra tint to keep panel identity. Off: fall back to a denser opaque fill.
        color = tint.copy(alpha = if (blurOn) GLASS_FILL_ALPHA else fillAlpha),
        tonalElevation = 0.dp,
        border =
            BorderStroke(
                width = GlassBorderWidth,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = GLASS_BORDER_ALPHA),
            ),
        content = content,
    )
}
