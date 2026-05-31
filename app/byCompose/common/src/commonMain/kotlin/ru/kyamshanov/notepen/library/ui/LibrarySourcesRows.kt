package ru.kyamshanov.notepen.library.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ru.kyamshanov.notepen.library.api.LibraryBackendKind
import ru.kyamshanov.notepen.library.api.LibraryConnectionState
import ru.kyamshanov.notepen.library.api.LibraryRole

internal val ConnectedGreen = Color(0xFF2E7D32)
internal val ErrorRed = Color(0xFFC62828)

/** Small coloured dot reflecting a library's [LibraryConnectionState]. */
@Composable
internal fun StatusDot(connectionState: LibraryConnectionState) {
    val color =
        when (connectionState) {
            is LibraryConnectionState.Connected -> ConnectedGreen
            is LibraryConnectionState.Connecting -> MaterialTheme.colorScheme.onSurfaceVariant
            is LibraryConnectionState.Error -> ErrorRed
            is LibraryConnectionState.Disconnected -> MaterialTheme.colorScheme.outline
        }
    Box(
        modifier =
            Modifier
                .size(8.dp)
                .background(color = color, shape = CircleShape),
    )
}

/** Settings-style row with a leading icon, title/subtitle and a trailing [Switch]. */
@Composable
internal fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    BaseRow(icon = icon, title = title, subtitle = subtitle) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Shared layout for an icon + title/subtitle + trailing slot, matching SettingsContent rows. */
@Composable
internal fun BaseRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

/** Rounded tonal surface wrapper used for every list row on the screen. */
@Composable
internal fun LibraryRowSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().then(modifier),
    ) {
        content()
    }
}

/** Muted section label. */
@Composable
internal fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/** A muted explanatory card shown when there are no connected libraries. */
@Composable
internal fun EmptyHint(text: String) {
    LibraryRowSurface {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

internal fun kindIcon(kind: LibraryBackendKind): ImageVector =
    when (kind) {
        LibraryBackendKind.Local -> Icons.Default.Folder
        LibraryBackendKind.PeerLan -> Icons.Default.Wifi
        LibraryBackendKind.GitHub -> Icons.Default.Cloud
        LibraryBackendKind.Cloud -> Icons.Default.Cloud
    }

internal fun roleLabel(role: LibraryRole): String =
    when (role) {
        LibraryRole.Reader -> "Читатель"
        LibraryRole.Librarian -> "Библиотекарь"
    }

internal fun connectionLabel(connectionState: LibraryConnectionState): String =
    when (connectionState) {
        is LibraryConnectionState.Connected -> "В сети"
        is LibraryConnectionState.Connecting -> "Подключение…"
        is LibraryConnectionState.Disconnected -> "Не в сети"
        is LibraryConnectionState.Error -> "Ошибка"
    }
