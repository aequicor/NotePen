package ru.kyamshanov.notepen

import androidx.compose.runtime.Composable
import ru.kyamshanov.notepen.appsettings.domain.model.ScreenOrientationMode

/**
 * Десктоп: понятия «ориентация окна» нет, окно свободно меняет размер — лок не
 * применяется. No-op (паттерн как у `currentWindowSizePx`).
 */
@Composable
actual fun ApplyScreenOrientation(
    @Suppress("UNUSED_PARAMETER") mode: ScreenOrientationMode,
) {
    // No-op on desktop.
}
