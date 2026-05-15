package ru.kyamshanov.notepen.sync

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ru.kyamshanov.notepen.sync.domain.projection.ProjectionViewerController

private const val POINTER_RADIUS = 16f
private const val POINTER_ALPHA = 0.7f

/**
 * Viewer overlay: draws the host's pointer dot and shows the attach/detach button.
 *
 * The pointer position is normalised [0..1] within the visible page; the
 * overlay occupies the same bounds as the page content so it maps directly.
 */
@Composable
fun ProjectionViewerOverlay(
    controller: ProjectionViewerController,
    modifier: Modifier = Modifier,
) {
    val frame by controller.currentFrame.collectAsState()
    val following by controller.following.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        val px = frame?.pointerX
        val py = frame?.pointerY
        if (px != null && py != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color.Red.copy(alpha = POINTER_ALPHA),
                    radius = POINTER_RADIUS,
                    center = Offset(px * size.width, py * size.height),
                )
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            shape = MaterialTheme.shapes.small,
            tonalElevation = 2.dp,
        ) {
            Button(
                onClick = { if (following) controller.detach() else controller.attach() },
                colors = if (following) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                } else {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                },
            ) {
                Icon(
                    imageVector = if (following) Icons.Default.CenterFocusStrong else Icons.Default.OpenWith,
                    contentDescription = null,
                )
                Text(if (following) " Следую" else " Свободно")
            }
        }
    }
}

/**
 * Host indicator shown while projection is broadcasting.
 */
@Composable
fun ProjectionHostBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(8.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 2.dp,
    ) {
        Text(
            text = "● Трансляция",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}
