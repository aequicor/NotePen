package ru.kyamshanov.notepen

import androidx.compose.runtime.Composable

/** Desktop manages its own screen timeout — no action needed. */
@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    // no-op
}
