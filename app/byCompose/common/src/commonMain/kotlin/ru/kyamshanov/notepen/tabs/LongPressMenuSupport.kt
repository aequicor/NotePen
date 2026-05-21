package ru.kyamshanov.notepen.tabs

/**
 * `true` on platforms whose primary pointer is a finger (Android), where the
 * tab context menu opens on long-press. `false` on desktop, where it opens on
 * a secondary (right) click instead — long-pressing a mouse button is not a
 * natural gesture there.
 */
expect val SupportsLongPressMenu: Boolean
