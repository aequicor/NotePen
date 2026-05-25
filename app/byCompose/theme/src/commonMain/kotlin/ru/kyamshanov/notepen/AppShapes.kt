package ru.kyamshanov.notepen

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

@Immutable
data class AppShapes(
    val component: Shape,
    val surface: Shape,
)

internal val LocalAppShapes =
    staticCompositionLocalOf {
        AppShapes(
            component = RoundedCornerShape(ZeroCornerSize),
            surface = RoundedCornerShape(ZeroCornerSize),
        )
    }

internal val DefaultAppShapes =
    AppShapes(
        component = RoundedCornerShape(percent = 50),
        surface = RoundedCornerShape(size = 40.dp),
    )
