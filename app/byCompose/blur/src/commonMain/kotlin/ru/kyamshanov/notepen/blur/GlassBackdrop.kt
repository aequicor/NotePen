package ru.kyamshanov.notepen.blur

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection

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
 * Draws the liquid-glass backdrop for a panel of [shape]: copies the region of the
 * shared [LocalGlassBackdrop] that is *actually* behind this element (position-correct),
 * tints it, then runs a refraction shader so straight lines from the backdrop curve
 * through the rounded rim. Place on a `Modifier.matchParentSize()` element behind the
 * panel content so the content on top stays crisp. No-op when blur is disabled or no
 * backdrop is present.
 *
 * The refraction is a SkSL/AGSL runtime shader (see [refractionRenderEffect]) and is
 * what gives the surface its lens-like edge — `BlurEffect` alone leaves straight
 * backdrop lines straight, which reads as a flat translucent disc rather than glass.
 * On platforms where runtime shaders aren't available (Android < 13) we fall back to
 * [BlurEffect] so the panel still reads as frosted glass rather than a flat plate.
 */
@Composable
internal fun Modifier.glassBackdropLayer(
    shape: Shape,
    tint: Color,
): Modifier {
    val backdrop = LocalGlassBackdrop.current
    val sourceLayer = backdrop?.layer
    if (backdrop == null || sourceLayer == null) return this
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    var shapeOrigin by remember { mutableStateOf(Offset.Zero) }
    var shapeSizePx by remember { mutableStateOf(IntSize.Zero) }
    val tintColor = if (tint.isSpecified) tint.copy(alpha = GLASS_TINT_ALPHA) else Color.Unspecified
    val padPx = with(density) { GLASS_REFRACTION_PAD.toPx() }.toInt()
    // Grow the inner element by `padPx` on each side so the refraction shader has real
    // backdrop pixels to sample from past the visible rim. The outer layout still reports
    // the original size to the parent — the consumer never sees the pad zone.
    return this
        .onGloballyPositioned { shapeOrigin = it.positionInWindow() }
        .layout { measurable, constraints ->
            val w = constraints.maxWidth
            val h = constraints.maxHeight
            val grownW = w + padPx * 2
            val grownH = h + padPx * 2
            val placeable = measurable.measure(Constraints.fixed(grownW, grownH))
            shapeSizePx = IntSize(w, h)
            layout(w, h) {
                placeable.place(-padPx, -padPx)
            }
        }
        .graphicsLayer {
            // No clipping on this layer — the shader masks pixels outside the shape
            // itself (returns transparent for d >= 0). Clipping here to the shape would
            // again starve the refraction sampler at the rim.
            this.clip = false
            this.shape = RectangleShape
            val sw = shapeSizePx.width.toFloat()
            val sh = shapeSizePx.height.toFloat()
            this.renderEffect =
                glassRenderEffect(
                    shapeWidthPx = sw,
                    shapeHeightPx = sh,
                    padPx = padPx.toFloat(),
                    shape = shape,
                    density = density,
                    layoutDirection = layoutDirection,
                )
        }
        .drawBehind {
            // The drawScope here covers the inner (grown) rect of size shape + 2*pad.
            // Translate the captured backdrop so the visible shape lines up with the
            // panel's window position; the pad ring around it picks up real backdrop
            // pixels that the shader reaches for at the rim.
            val offX = shapeOrigin.x - backdrop.sourceOriginInWindow.x - padPx
            val offY = shapeOrigin.y - backdrop.sourceOriginInWindow.y - padPx
            translate(-offX, -offY) { drawLayer(sourceLayer) }
            if (tintColor.isSpecified) drawRect(color = tintColor)
        }
}

/**
 * Resolves the corner radius the [shape] would round to for a panel sized
 * [widthPx] × [heightPx] — used both as the SDF radius and to scale the refraction
 * band/strength so the lens curves match the visible rim. Falls back to half the
 * shorter side (i.e. a circle) when the shape isn't corner-based, which is the
 * safe default for our glass surfaces (`CircleShape`, `RoundedCornerShape`).
 */
private fun shapeCornerPx(
    shape: Shape,
    widthPx: Float,
    heightPx: Float,
    density: Density,
): Float {
    val short = minOf(widthPx, heightPx)
    if (shape is CornerBasedShape) {
        val corner: CornerSize = shape.topStart
        return corner.toPx(Size(widthPx, heightPx), density).coerceAtMost(short / 2f)
    }
    return short / 2f
}

/**
 * Builds the glass [RenderEffect] for the current panel size and shape: refraction
 * via [refractionRenderEffect] when available, [BlurEffect] otherwise. Returns `null`
 * when the panel hasn't been measured yet — the layer falls back to a no-op effect
 * for that single frame, which is fine: `drawBehind` still paints the backdrop + tint.
 */
private fun glassRenderEffect(
    shapeWidthPx: Float,
    shapeHeightPx: Float,
    padPx: Float,
    shape: Shape,
    density: Density,
    @Suppress("UNUSED_PARAMETER") layoutDirection: LayoutDirection,
): RenderEffect? {
    val refraction =
        if (shapeWidthPx > 0f && shapeHeightPx > 0f) {
            val cornerPx = shapeCornerPx(shape, shapeWidthPx, shapeHeightPx, density)
            val edgeBandPx = (cornerPx * GLASS_EDGE_BAND_FACTOR).coerceAtLeast(1f)
            val strengthPx = cornerPx * GLASS_REFRACTION_STRENGTH_FACTOR
            refractionRenderEffect(
                innerWidthPx = shapeWidthPx + 2f * padPx,
                innerHeightPx = shapeHeightPx + 2f * padPx,
                padPx = padPx,
                shapeWidthPx = shapeWidthPx,
                shapeHeightPx = shapeHeightPx,
                cornerRadiusPx = cornerPx,
                edgeBandPx = edgeBandPx,
                strengthPx = strengthPx,
            )
        } else {
            null
        }
    // Platforms without runtime shaders (Android < 13) and the first pre-measure frame
    // fall back to a plain blur so panels don't render as flat translucent plates.
    return refraction ?: with(density) {
        val blurPx = GlassBlurRadius.toPx()
        BlurEffect(blurPx, blurPx, TileMode.Clamp)
    }
}
