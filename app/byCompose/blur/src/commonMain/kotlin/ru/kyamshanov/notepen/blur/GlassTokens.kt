package ru.kyamshanov.notepen.blur

import androidx.compose.ui.unit.dp

/**
 * Shared design tokens for the "glass" surfaces used across floating panels
 * (tool rail, tool-settings panel, portrait top airbar). Centralised so that
 * tuning the look-and-feel happens in one place.
 */
internal val GlassCornerRadius = 20.dp

/** Faint extra tint on top of the haze material when blur is active (material does the frosting). */
internal const val GLASS_FILL_ALPHA = 0.12f

/** Fill alpha when blur is off (weak device / disabled): denser so controls stay legible. */
internal const val GLASS_FILL_ALPHA_OPAQUE = 0.65f
internal const val GLASS_BORDER_ALPHA = 0.35f
internal val GlassBorderWidth = 1.dp
