package ru.kyamshanov.notepen.ui.glass

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
 * sync: lowered fill alpha over a chosen tint, a subtle outline border and an
 * optional backdrop blur on platforms that support it.
 */
@Composable
internal fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(GlassCornerRadius),
    tint: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.glassBackdrop(),
        shape = shape,
        color = tint.copy(alpha = GlassFillAlpha),
        tonalElevation = 0.dp,
        border = BorderStroke(
            width = GlassBorderWidth,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = GlassBorderAlpha),
        ),
        content = content,
    )
}
