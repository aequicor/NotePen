package ru.kyamshanov.notepen.reflow.ui

import androidx.compose.ui.text.PlatformTextStyle

/**
 * Платформенно-зависимая часть `TextStyle` ридера: на Android — отключение
 * `includeFontPadding` (Android-Compose иначе добавляет fontMetrics-padding в
 * render и измеренная `TextMeasurer.size.height` расходится с фактической
 * высотой `BasicText` на 5-30 px). На JVM/desktop fontPadding не существует —
 * возвращается `null`.
 */
internal expect fun readerPlatformTextStyle(): PlatformTextStyle?
