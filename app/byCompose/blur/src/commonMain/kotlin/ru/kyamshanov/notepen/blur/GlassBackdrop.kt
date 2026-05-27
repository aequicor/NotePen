package ru.kyamshanov.notepen.blur

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.HazeMaterials

/**
 * Applies a frosted-glass backdrop to the modified element, sampling the
 * [LocalHazeState] backdrop. Uses haze's SwiftUI-style "material" recipe (blur +
 * luminosity tint + film grain) rather than a plain gaussian, so panels read as
 * frosted glass instead of a flat smear.
 *
 * Cross-platform via haze: hardware `RenderEffect`/Skia on Android 12+ and
 * Desktop, RenderScript on Android 11 and below (forced on via `blurEnabled`).
 * When [LocalBlurEnabled] is `false` it returns the receiver unchanged — the
 * glass illusion then relies on alpha + outline only.
 *
 * [shape] clips the blur so it follows rounded panels; without it haze fills the
 * element's full rectangular bounds and square corners bleed past the surface.
 * [tint] colours the frost so panels keep their identity (e.g. a settings sheet).
 */
@Composable
internal fun Modifier.glassBackdrop(
    shape: Shape = RectangleShape,
    tint: Color = Color.Unspecified,
): Modifier {
    if (!LocalBlurEnabled.current) return this
    val state = LocalHazeState.current
    val material = if (tint.isSpecified) HazeMaterials.thin(tint) else HazeMaterials.thin()
    return clip(shape).hazeEffect(state) {
        style = material
        blurEnabled = true
    }
}
