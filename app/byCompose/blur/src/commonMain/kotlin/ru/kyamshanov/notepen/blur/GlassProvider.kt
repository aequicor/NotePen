package ru.kyamshanov.notepen.blur

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntSize

/** The [GlassBackdrop] shared between [Modifier.glassSource] and the glass panels. */
val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

/** Runtime switch for the glass effect. When `false`, panels keep only a flat tint + outline. */
val LocalBlurEnabled = staticCompositionLocalOf { true }

/**
 * `true` iff Compose's [androidx.compose.ui.graphics.BlurEffect] is honored by the
 * platform's `graphicsLayer.renderEffect`. On Android, `RenderEffect` requires API 31
 * (Android 12); below that the effect is silently dropped and the layer renders the
 * captured backdrop unblurred. Desktop (Skia) always supports it.
 */
expect fun isBlurEffectSupported(): Boolean

/**
 * Hosts a [GlassBackdrop] for an editor screen and exposes it to descendant glass
 * panels. Wrap the screen that contains both the backdrop ([Modifier.glassSource])
 * and the floating [GlassSurface] panels.
 *
 * When the platform doesn't support [BlurEffect] (Android < 12), the backdrop layer
 * is skipped entirely — there's no point recording each frame into a layer whose
 * blur will be ignored. Panels fall back to the opaque-tint path automatically.
 */
@Composable
fun GlassBackdropProvider(
    blurEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val effectiveBlur = blurEnabled && isBlurEffectSupported()
    val layer = if (effectiveBlur) rememberGraphicsLayer() else null
    val backdrop = remember(layer) { GlassBackdrop(layer) }
    CompositionLocalProvider(
        LocalGlassBackdrop provides backdrop,
        LocalBlurEnabled provides effectiveBlur,
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
    // Vibrancy: compress the sampled backdrop's luminance range so same-coloured content
    // (e.g. black PDF text under a black-text panel) can't collide. In light themes lift the
    // black floor; in dark themes pull the white ceiling down. Applied to the shared layer, so
    // it only affects panels sampling it via drawLayer — the on-screen content stays untouched.
    val darkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val vibrancy = remember(darkTheme) { backdropVibrancyFilter(darkTheme) }
    layer.colorFilter = vibrancy
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

/**
 * Luminance-range compression of [GLASS_BACKDROP_CONTRAST]. Each RGB channel becomes
 * `scale * c (+ lift)`: in light themes [darkTheme] = false the black floor is lifted
 * (`out = scale*c + contrast`); in dark themes the white ceiling is lowered (`out = scale*c`).
 * Alpha is left untouched. The offset column is on the 0..255 scale Compose expects.
 */
private fun backdropVibrancyFilter(darkTheme: Boolean): ColorFilter {
    val scale = 1f - GLASS_BACKDROP_CONTRAST
    val lift = if (darkTheme) 0f else GLASS_BACKDROP_CONTRAST * 255f
    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                scale,
                0f,
                0f,
                0f,
                lift,
                0f,
                scale,
                0f,
                0f,
                lift,
                0f,
                0f,
                scale,
                0f,
                lift,
                0f,
                0f,
                0f,
                1f,
                0f,
            ),
        ),
    )
}
