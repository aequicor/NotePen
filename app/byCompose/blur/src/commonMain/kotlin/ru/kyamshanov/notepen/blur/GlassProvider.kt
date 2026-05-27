package ru.kyamshanov.notepen.blur

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntSize

/** The [GlassBackdrop] shared between [Modifier.glassSource] and the glass panels. */
val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

/** Runtime switch for the glass effect. When `false`, panels keep only a flat tint + outline. */
val LocalBlurEnabled = staticCompositionLocalOf { true }

/**
 * Hosts a [GlassBackdrop] for an editor screen and exposes it to descendant glass
 * panels. Wrap the screen that contains both the backdrop ([Modifier.glassSource])
 * and the floating [GlassSurface] panels.
 */
@Composable
fun GlassBackdropProvider(
    blurEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val layer = if (blurEnabled) rememberGraphicsLayer() else null
    val backdrop = remember(layer) { GlassBackdrop(layer) }
    CompositionLocalProvider(
        LocalGlassBackdrop provides backdrop,
        LocalBlurEnabled provides blurEnabled,
        content = content,
    )
}

/**
 * Marks the receiver as the backdrop sampled by glass panels. Records the content
 * into the shared [GraphicsLayer] every frame (so the refraction stays live as the
 * page scrolls) and tracks the backdrop's window origin for position-correct
 * sampling. Apply to the content that should appear behind the panels (the PDF /
 * reflow canvas).
 */
@Composable
fun Modifier.glassSource(): Modifier {
    val backdrop = LocalGlassBackdrop.current
    val layer = backdrop?.layer
    if (!LocalBlurEnabled.current || backdrop == null || layer == null) return this
    return this
        .onGloballyPositioned { backdrop.sourceOriginInWindow = it.positionInWindow() }
        .drawWithContent {
            val w = size.width.toInt().coerceAtLeast(1)
            val h = size.height.toInt().coerceAtLeast(1)
            layer.record(size = IntSize(w, h)) {
                this@drawWithContent.drawContent()
            }
            // Draw the real content on screen (not the layer) so per-panel render
            // effects set on the shared layer never distort the backdrop itself.
            drawContent()
        }
}
