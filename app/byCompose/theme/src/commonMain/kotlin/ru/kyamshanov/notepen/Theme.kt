package ru.kyamshanov.notepen

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable

@Composable
expect fun ComposableAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
)

object AppTheme {
    val shapes: AppShapes
        @Composable
        get() = LocalAppShapes.current

    val spacing: AppSpacing
        @Composable
        get() = LocalAppSpacing.current
}
