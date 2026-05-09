package ru.kyamshanov.notepen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditOff
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PdfFloatingToolbar(
    isDrawingEnabled: Boolean,
    onToggleDrawing: () -> Unit,
    hasAnnotations: Boolean,
    isSaving: Boolean,
    onSave: () -> Unit,
    scale: Int,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconButton(onClick = onToggleDrawing) {
                Icon(
                    imageVector = if (isDrawingEnabled) Icons.Default.Edit else Icons.Default.EditOff,
                    contentDescription = if (isDrawingEnabled) "Выключить рисование" else "Включить рисование",
                    tint = if (isDrawingEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            IconButton(
                onClick = onSave,
                enabled = hasAnnotations && !isSaving,
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = "Сохранить аннотации",
                        tint = if (hasAnnotations) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        },
                    )
                }
            }

            IconButton(
                onClick = onZoomIn,
                enabled = scale < MAX_SCALE,
            ) {
                Icon(
                    imageVector = Icons.Default.ZoomIn,
                    contentDescription = "Увеличить",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = "$scale%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            IconButton(
                onClick = onZoomOut,
                enabled = scale > MIN_SCALE,
            ) {
                Icon(
                    imageVector = Icons.Default.ZoomOut,
                    contentDescription = "Уменьшить",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

internal const val MIN_SCALE = 10
internal const val MAX_SCALE = 200
