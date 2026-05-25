package ru.kyamshanov.notepen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

private val GlassBorderWidth = 1.dp
private const val GlassBackgroundAlpha = 0.18f
private const val GlassBorderAlpha = 0.40f

private val ArrowBackVector: ImageVector =
    ImageVector.Builder(
        name = "ArrowBack",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(20f, 11f)
            horizontalLineTo(7.83f)
            lineToRelative(5.59f, -5.59f)
            lineTo(12f, 4f)
            lineToRelative(-8f, 8f)
            lineToRelative(8f, 8f)
            lineToRelative(1.41f, -1.41f)
            lineTo(7.83f, 13f)
            horizontalLineTo(20f)
            verticalLineToRelative(-2f)
            close()
        }
    }.build()

/**
 * Circular back-navigation button styled with glassmorphism (semi-transparent overlay + border).
 * Uses [AppTheme.shapes.component] for shape and [AppTheme.spacing.touchTarget] for size.
 */
@Composable
fun GlassmorphismBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = AppTheme.shapes.component
    val size = AppTheme.spacing.touchTarget
    Box(
        modifier =
            modifier
                .size(size)
                .clip(shape)
                .background(Color.White.copy(alpha = GlassBackgroundAlpha))
                .border(GlassBorderWidth, Color.White.copy(alpha = GlassBorderAlpha), shape)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = ArrowBackVector,
            contentDescription = "Navigate back",
            tint = Color.White,
        )
    }
}
