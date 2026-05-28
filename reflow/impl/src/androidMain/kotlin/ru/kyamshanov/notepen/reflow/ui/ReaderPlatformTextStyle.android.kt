package ru.kyamshanov.notepen.reflow.ui

import androidx.compose.ui.text.PlatformTextStyle

@Suppress("DEPRECATION")
internal actual fun readerPlatformTextStyle(): PlatformTextStyle? = PlatformTextStyle(includeFontPadding = false)
