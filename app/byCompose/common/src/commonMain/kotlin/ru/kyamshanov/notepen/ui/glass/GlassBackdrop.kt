package ru.kyamshanov.notepen.ui.glass

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * Applies a backdrop blur to the content behind the modified element so that
 * it reads as frosted glass.
 *
 * Implemented only on Android 12+ (API 31) via the platform `RenderEffect`.
 * On older Android and on JVM/Desktop returns the receiver unchanged — the
 * glass illusion there relies on alpha + outline instead.
 */
expect fun Modifier.glassBackdrop(blurRadius: Dp = GlassBlurRadius): Modifier
