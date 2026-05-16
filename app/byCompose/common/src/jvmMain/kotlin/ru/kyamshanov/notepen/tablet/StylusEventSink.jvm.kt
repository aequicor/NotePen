package ru.kyamshanov.notepen.tablet

import androidx.compose.ui.Modifier

/**
 * Desktop has no MotionEvent — pressure, tilt and barrel are sourced from
 * WinTab32 / Cocoa side-channels that bypass Compose's pointer pipeline
 * entirely. The sink is a no-op here.
 */
actual fun Modifier.stylusEventSink(controller: TabletInputController): Modifier = this
