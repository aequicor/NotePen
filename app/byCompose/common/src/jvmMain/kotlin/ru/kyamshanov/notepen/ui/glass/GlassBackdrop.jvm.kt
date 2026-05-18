package ru.kyamshanov.notepen.ui.glass

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * JVM/Desktop: identity. Skia-backed Compose on Desktop has no portable
 * backdrop-blur primitive; the glass illusion is carried by alpha + outline.
 */
actual fun Modifier.glassBackdrop(blurRadius: Dp): Modifier = this
