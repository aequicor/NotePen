package ru.kyamshanov.notepen

/**
 * Desktop (JVM): a horizontal scroll gesture (trackpad / shift-wheel) isn't
 * available to every user, so a horizontal wheel shows leading/trailing scroll
 * buttons. See [WheelScrollButtons].
 */
internal actual val isDesktopPlatform: Boolean = true
