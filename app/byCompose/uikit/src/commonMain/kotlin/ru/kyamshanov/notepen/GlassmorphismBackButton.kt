package ru.kyamshanov.notepen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val GlassBorderWidth = 1.dp
private const val GlassBackgroundAlpha = 0.18f
private const val GlassBorderAlpha = 0.40f

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
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(Color.White.copy(alpha = GlassBackgroundAlpha))
            .border(GlassBorderWidth, Color.White.copy(alpha = GlassBorderAlpha), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Navigate back",
            tint = Color.White,
        )
    }
}
