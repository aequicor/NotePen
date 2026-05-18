package ru.kyamshanov.notepen.ui.glass

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * Android: identity for now. Compose UI 1.9.0 does not expose a backdrop-blur
 * modifier — `RenderEffect.createBlurEffect` applied via `graphicsLayer`
 * blurs the panel's own content (icons, text), not what is behind it, which
 * makes controls illegible. Real backdrop blur requires either the `haze`
 * library or `Window.setBackgroundBlurRadius` (window-level, not per-view).
 *
 * Until one of those is wired in, the glass illusion is carried by the
 * lowered fill alpha plus the outline border in [GlassSurface].
 */
actual fun Modifier.glassBackdrop(blurRadius: Dp): Modifier = this
