package ru.kyamshanov.notepen.blur

import androidx.compose.ui.unit.dp

/**
 * Shared design tokens for the "liquid glass" surfaces used across floating panels
 * (tool rail, tool-settings panel, portrait top airbar, page pill, sidebars).
 * Centralised so that tuning the look-and-feel happens in one place.
 *
 * Tuned for an Apple "liquid glass" aesthetic: the backdrop stays visible but softened
 * by a light blur with a faint tint and a crisp hairline edge — transparent yet
 * delineated, with the panel's own content staying legible on top.
 */
internal val GlassCornerRadius = 24.dp

/**
 * Blur applied to the captured backdrop. Strong enough to read as a clear "frosted glass"
 * refraction over a vibrant background, while keeping panel content sharp on top.
 */
internal val GlassBlurRadius = 20.dp

/**
 * Luminosity tint baked into the glass. Platform-specific because `BlurEffect` is only
 * dependable on desktop (Skia) — Android SDK 31+ reports support, but EMUI/HarmonyOS and
 * other OEM builds silently ignore `RenderEffect`, leaving PDF text readable through any
 * transparent bar. Desktop gets a real frosted-glass tint; Android stays opaque so panels
 * always mask content beneath them regardless of OEM support.
 */
internal expect val GLASS_TINT_ALPHA: Float

/**
 * Vibrancy: how far the backdrop's luminance range is compressed toward the panel's tint
 * before it shows through. `0.30` keeps a 70% range, so pure-black backdrop text is lifted to
 * ~30% grey (light theme) — or pure white is pulled down to ~70% (dark theme) — guaranteeing the
 * panel's own dark/light content never collides with same-coloured content bleeding through.
 */
internal const val GLASS_BACKDROP_CONTRAST = 0.40f

/** Fill alpha when blur is off (weak device / disabled): denser so controls stay legible. */
internal const val GLASS_FILL_ALPHA_OPAQUE = 0.95f

/** Outline alpha for the crisp hairline edge (and the blur-off fallback border). */
internal const val GLASS_BORDER_ALPHA = 0.4f
internal val GlassBorderWidth = 1.dp

/**
 * Refraction edge band width, as a fraction of the panel's corner radius. The shader
 * displaces samples only inside this band, leaving the deep interior a 1:1 copy of the
 * backdrop. A value near `1.0` puts the lens entirely within the rounded corner; larger
 * values bleed the lens further into the panel for a softer rim.
 */
internal const val GLASS_EDGE_BAND_FACTOR = 1.0f

/**
 * Peak displacement at the rim, as a fraction of the corner radius. `0.30` keeps the
 * lens visibly curved at rounded corners without "swallowing" content. The shader also
 * gates displacement by corner proximity, so a long bar with small corner radius gets
 * almost no displacement along its flat edges regardless of this value.
 */
internal const val GLASS_REFRACTION_STRENGTH_FACTOR = 0.30f

/**
 * Pad ring drawn around the visible glass shape that gives the refraction shader real
 * backdrop pixels to sample beyond the rim. Must be at least the worst-case displacement
 * (currently corner_radius × [GLASS_REFRACTION_STRENGTH_FACTOR]); 24dp covers every glass
 * surface we render at typical scales.
 */
internal val GLASS_REFRACTION_PAD = 24.dp
