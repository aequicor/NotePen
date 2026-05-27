package ru.kyamshanov.notepen.blur

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow

/**
 * Shared backdrop captured once per editor screen and sampled by every glass panel
 * on top. Holds the [GraphicsLayer] that records the backdrop pixels plus the
 * backdrop's window origin, so a panel can sample the region *actually* behind it
 * (position-correct) rather than a fixed corner of the source.
 *
 * [layer] is `null` when blur is disabled — panels then fall back to a flat tint.
 */
class GlassBackdrop internal constructor(
    internal val layer: GraphicsLayer?,
) {
    internal var sourceOriginInWindow: Offset by mutableStateOf(Offset.Zero)
}

/**
 * Draws the frosted-glass backdrop for a panel of [shape]: copies the region of the
 * shared [LocalGlassBackdrop] that is *actually* behind this element (position-correct)
 * and blurs it through a framework [graphicsLayer] render effect, with a light [tint] on
 * top. Place on a `Modifier.matchParentSize()` element behind the panel content so the
 * content on top stays crisp. No-op when blur is disabled or no backdrop is present.
 *
 * Uses Compose's [BlurEffect] (honored by the layer on every target) rather than a raw
 * Skia/AGSL runtime-shader effect — the latter is silently dropped by Compose Desktop.
 */
@Composable
internal fun Modifier.glassBackdropLayer(
    shape: Shape,
    tint: Color,
): Modifier {
    val backdrop = LocalGlassBackdrop.current
    val sourceLayer = backdrop?.layer
    if (backdrop == null || sourceLayer == null) return this
    var origin by remember { mutableStateOf(Offset.Zero) }
    val tintColor = if (tint.isSpecified) tint.copy(alpha = GLASS_TINT_ALPHA) else Color.Unspecified
    return this
        .onGloballyPositioned { origin = it.positionInWindow() }
        .graphicsLayer {
            this.clip = true
            this.shape = shape
            val blurPx = GlassBlurRadius.toPx()
            // Clamp (not Decal): extends edge pixels so the rim doesn't fade to transparent.
            this.renderEffect = BlurEffect(blurPx, blurPx, TileMode.Clamp)
        }
        .drawBehind {
            val offX = origin.x - backdrop.sourceOriginInWindow.x
            val offY = origin.y - backdrop.sourceOriginInWindow.y
            translate(-offX, -offY) { drawLayer(sourceLayer) }
            if (tintColor.isSpecified) drawRect(color = tintColor)
        }
}
