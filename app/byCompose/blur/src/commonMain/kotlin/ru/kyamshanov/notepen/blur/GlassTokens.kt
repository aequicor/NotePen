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
 * Blur applied to the captured backdrop. Light enough to keep the panel transparent,
 * strong enough that text bleeding through doesn't collide with the panel's own content.
 */
internal val GlassBlurRadius = 12.dp

/** Luminosity tint baked into the glass; low so it reads as transparent but still delineated. */
internal const val GLASS_TINT_ALPHA = 0.2f

/**
 * Vibrancy: how far the backdrop's luminance range is compressed toward the panel's tint
 * before it shows through. `0.30` keeps a 70% range, so pure-black backdrop text is lifted to
 * ~30% grey (light theme) — or pure white is pulled down to ~70% (dark theme) — guaranteeing the
 * panel's own dark/light content never collides with same-coloured content bleeding through.
 */
internal const val GLASS_BACKDROP_CONTRAST = 0.40f

/** Fill alpha when blur is off (weak device / disabled): denser so controls stay legible. */
internal const val GLASS_FILL_ALPHA_OPAQUE = 0.55f

/** Outline alpha for the crisp hairline edge (and the blur-off fallback border). */
internal const val GLASS_BORDER_ALPHA = 0.4f
internal val GlassBorderWidth = 1.dp
