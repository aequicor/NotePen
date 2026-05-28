package ru.kyamshanov.notepen

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush

/**
 * Soft tinted gradient used as the screen-level backdrop behind every glass
 * surface (`GlassSurface`, glass tiles). Without a vibrant background the
 * frosted refraction has nothing to bend, so panels look like flat tinted
 * rectangles. This applies a diagonal three-stop gradient through the theme's
 * containers, which gives every glass surface above it a visible color
 * gradient + soft luminance variation to refract.
 *
 * Place on the same `Box` that has `Modifier.glassSource()` so the gradient
 * becomes the sampled backdrop for all panels on top.
 */
@Composable
fun Modifier.liquidGlassHero(): Modifier {
    val cs = MaterialTheme.colorScheme
    val brush =
        remember(cs.primaryContainer, cs.tertiaryContainer, cs.surface) {
            Brush.linearGradient(
                colorStops =
                    arrayOf(
                        0.0f to cs.primaryContainer,
                        0.55f to cs.surface,
                        1.0f to cs.tertiaryContainer,
                    ),
                start = Offset.Zero,
                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
            )
        }
    return this.background(brush)
}
