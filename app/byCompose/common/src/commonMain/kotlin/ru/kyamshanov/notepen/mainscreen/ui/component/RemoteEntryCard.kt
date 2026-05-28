package ru.kyamshanov.notepen.mainscreen.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.kyamshanov.notepen.LiquidGlassCard
import ru.kyamshanov.notepen.mainscreen.ui.model.RemoteEntryUiModel
import ru.kyamshanov.notepen.mainscreen.ui.model.RemoteFolderUiModel

/**
 * Card for one entry in the host's library (Remote section, tablet side).
 *
 * Phase 2 renders read-only; Phase 3 wires the tap to a
 * `NavigationTarget.RemoteEditor(documentId)` flow.
 */
@Composable
fun RemoteEntryCard(
    model: RemoteEntryUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LiquidGlassCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.CloudQueue,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = model.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val sizeLabel = model.fileSize?.let { humanReadableBytes(it) } ?: "—"
                Text(
                    text = sizeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                if (model.isOrphanedOnHost) {
                    Text(
                        text = "удалён на хосте",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else if (model.pendingCount > 0) {
                    Text(
                        text = "не синхронизировано (${model.pendingCount})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/** Compact card for a host-side folder shown inside the Remote section. */
@Composable
fun RemoteFolderCard(
    model: RemoteFolderUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LiquidGlassCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${model.fileCount} файлов",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

private fun humanReadableBytes(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        bytes < kb -> "$bytes B"
        bytes < mb -> "${(bytes / kb).format1()} KB"
        bytes < gb -> "${(bytes / mb).format1()} MB"
        else -> "${(bytes / gb).format1()} GB"
    }
}

private fun Double.format1(): String {
    val rounded = (this * 10).toLong() / 10.0
    val whole = rounded.toLong()
    val frac = ((rounded - whole) * 10).toLong()
    return if (frac == 0L) "$whole" else "$whole.$frac"
}
