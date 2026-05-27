package ru.kyamshanov.notepen

import androidx.compose.runtime.Composable

/**
 * Live hints for whether the (expensive) backdrop blur should be recommended off.
 *
 * @property isLowEndDevice device classified as low-end (low RAM / few cores)
 * @property isBatteryLow battery is low and not charging
 */
data class BlurAdvice(
    val isLowEndDevice: Boolean = false,
    val isBatteryLow: Boolean = false,
) {
    val shouldRecommendDisablingBlur: Boolean get() = isLowEndDevice || isBatteryLow
}

/** Observes device class and battery to advise on the blur effect. Battery updates live (Android). */
@Composable
expect fun rememberBlurAdvice(): BlurAdvice
