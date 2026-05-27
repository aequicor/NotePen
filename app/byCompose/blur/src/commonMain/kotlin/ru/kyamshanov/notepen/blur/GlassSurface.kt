package ru.kyamshanov.notepen.blur

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape

/**
 * Floating "liquid glass" surface used for the tool rail, tool-settings panel,
 * portrait top airbar, page pill and sidebars. Centralises the look so every surface
 * stays in sync: a refracting glass backdrop (see [glassBackdropLayer]) plus a crisp
 * specular rim, drawn *behind* the content so icons/labels on top stay sharp, all
 * lifted by a soft ambient shadow. When blur is disabled it degrades to a denser
 * opaque fill + hairline.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(GlassCornerRadius),
    tint: Color = MaterialTheme.colorScheme.surface,
    fillAlpha: Float = GLASS_FILL_ALPHA_OPAQUE,
    content: @Composable () -> Unit,
) {
    val glassActive = LocalBlurEnabled.current && LocalGlassBackdrop.current?.layer != null
    Box(modifier = modifier) {
        if (glassActive) {
            // Blurred glass backdrop (effected layer), then a crisp hairline edge on top
            // (separate layer so the blur doesn't soften it). Skip the edge on full-width
            // rectangular bars, where a 4-side border reads as stray top/bottom stripes.
            Box(Modifier.matchParentSize().glassBackdropLayer(shape = shape, tint = tint))
            if (shape !== RectangleShape) {
                Box(
                    Modifier
                        .matchParentSize()
                        .border(
                            width = GlassBorderWidth,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = GLASS_BORDER_ALPHA),
                            shape = shape,
                        ),
                )
            }
        } else {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(tint.copy(alpha = fillAlpha))
                    .border(
                        width = GlassBorderWidth,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = GLASS_BORDER_ALPHA),
                        shape = shape,
                    ),
            )
        }
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            content()
        }
    }
}
