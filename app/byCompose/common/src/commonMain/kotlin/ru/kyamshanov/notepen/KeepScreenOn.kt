package ru.kyamshanov.notepen

import androidx.compose.runtime.Composable

/**
 * Keeps the screen on for as long as this composable is in the composition
 * when [enabled] is true. On Android this sets [android.view.View.keepScreenOn];
 * the effect is cleared on disposal or when [enabled] flips to false.
 *
 * Desktop has no managed screen timeout — the actual is a no-op there.
 */
@Composable
expect fun KeepScreenOn(enabled: Boolean)
