package ru.kyamshanov.notepen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import ru.kyamshanov.notepen.blur.GlassSurface

/** Default radius for `LiquidGlassCard`. */
val LiquidGlassCardShape: Shape = RoundedCornerShape(20.dp)

/**
 * Frosted-glass card. Functionally a `Card`-shaped clickable container that
 * also refracts the surrounding `GlassBackdropProvider`'s backdrop (a hero
 * gradient, the PDF canvas, etc.). The shape is clipped on the outer modifier
 * so the ripple stays inside the corners.
 *
 * Pass `null` to [onClick] for a non-interactive container.
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = LiquidGlassCardShape,
    tint: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable () -> Unit,
) {
    val clickModifier =
        if (onClick != null) {
            Modifier.clip(shape).clickable(onClick = onClick)
        } else {
            Modifier.clip(shape)
        }
    GlassSurface(
        modifier = modifier.then(clickModifier),
        shape = shape,
        tint = tint,
    ) {
        content()
    }
}
