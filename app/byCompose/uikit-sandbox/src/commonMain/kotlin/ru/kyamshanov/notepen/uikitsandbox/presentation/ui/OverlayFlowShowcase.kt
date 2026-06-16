package ru.kyamshanov.notepen.uikitsandbox.presentation.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import ru.kyamshanov.notepen.LiquidGlassAlertDialog
import ru.kyamshanov.notepen.background.DocumentBackgroundSettingsSheet
import ru.kyamshanov.notepen.mainscreen.ui.dialog.CreateFolderDialog
import ru.kyamshanov.notepen.mainscreen.ui.dialog.DeleteFolderDialog
import ru.kyamshanov.notepen.mainscreen.ui.dialog.SafMergeDialog
import ru.kyamshanov.notepen.mainscreen.ui.model.CreateFolderDialogState
import ru.kyamshanov.notepen.shortcuts.ShortcutsSettingsDialog
import ru.kyamshanov.notepen.shortcuts.domain.model.ShortcutsSettings
import ru.kyamshanov.notepen.uikitsandbox.UikitSandboxComponent

/**
 * Секция overlay-сценариев проекта.
 *
 * Отображает кнопки запуска диалогов, sheet-ов и notification-preview.
 *
 * @param strings локализованные demo-строки.
 * @param darkTheme true, если sandbox сейчас в тёмной теме.
 * @param onDialogRequest callback выбора overlay-сценария.
 * @param onMessage callback для snackbar-сообщений.
 */
@Composable
internal fun OverlayShowcase(
    strings: SandboxStrings,
    darkTheme: Boolean,
    onDialogRequest: (SandboxDialog) -> Unit,
    onMessage: (String) -> Unit,
) {
    SectionBlock(title = strings.overlaysTitle, icon = Icons.Default.Notifications) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OverlayButton(strings.createFolder, Icons.Default.Add) { onDialogRequest(SandboxDialog.CreateFolder) }
            OverlayButton(strings.deleteFolder, Icons.Default.Delete) { onDialogRequest(SandboxDialog.DeleteFolder) }
            OverlayButton(strings.safMerge, Icons.Default.CloudQueue) { onDialogRequest(SandboxDialog.SafMerge) }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OverlayButton(strings.shortcuts, Icons.Default.Settings) { onDialogRequest(SandboxDialog.Shortcuts) }
            OverlayButton(strings.backgroundSheet, Icons.Default.Brush) { onDialogRequest(SandboxDialog.BackgroundSheet) }
            OverlayButton(strings.alert, Icons.Default.Notifications) { onDialogRequest(SandboxDialog.Alert) }
        }
        NotificationPreview(strings = strings, darkTheme = darkTheme, onMessage = onMessage)
    }
}

@Composable
private fun OverlayButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    OutlinedButton(onClick = onClick) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun NotificationPreview(
    strings: SandboxStrings,
    darkTheme: Boolean,
    onMessage: (String) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(strings.notificationTitle, style = MaterialTheme.typography.titleMedium)
                AssistChip(
                    onClick = {},
                    label = { Text(if (darkTheme) strings.dark else strings.light) },
                )
            }
            LinearProgressIndicator(
                progress = { 0.62f },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = strings.notificationDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { onMessage(strings.notificationMessage) }) {
                Text(strings.showSnackbar)
            }
        }
    }
}

/**
 * Секция переходов и embedded component boundary.
 *
 * @param strings локализованные demo-строки.
 * @param component публичный component sandbox-фичи.
 * @param onMessage callback для сообщений embedded feature.
 */
@Composable
internal fun FlowShowcase(
    strings: SandboxStrings,
    component: UikitSandboxComponent,
    onMessage: (String) -> Unit,
) {
    varRouteShowcase(strings = strings, component = component, onMessage = onMessage)
}

@Composable
private fun varRouteShowcase(
    strings: SandboxStrings,
    component: UikitSandboxComponent,
    onMessage: (String) -> Unit,
) {
    var route by remember { mutableStateOf(SandboxRoute.Library) }
    SectionBlock(title = strings.flowTitle, icon = Icons.Default.PlayArrow) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SandboxRoute.entries.forEach { item ->
                FilterChip(
                    selected = route == item,
                    onClick = { route = item },
                    label = { Text(strings.routeLabel(item)) },
                )
            }
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = SURFACE_PANEL_ALPHA))
                    .padding(12.dp),
        ) {
            AnimatedContent(
                targetState = route,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "route-preview",
                modifier = Modifier.fillMaxSize(),
            ) { currentRoute ->
                when (currentRoute) {
                    SandboxRoute.Library ->
                        RoutePreview(strings = strings, title = strings.routeLibrary, icon = Icons.Default.Folder)
                    SandboxRoute.Editor ->
                        RoutePreview(strings = strings, title = strings.routeEditor, icon = Icons.Default.Brush)
                    SandboxRoute.Settings ->
                        RoutePreview(strings = strings, title = strings.routeSettings, icon = Icons.Default.Settings)
                }
            }
        }
        HorizontalDivider()
        Text(strings.architectureSample, style = MaterialTheme.typography.titleMedium)
        Crossfade(targetState = route, label = "architecture-sample-crossfade") {
            UikitSandboxContent(
                component = component,
                onMessage = onMessage,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 260.dp, max = 420.dp),
            )
        }
    }
}

@Composable
private fun RoutePreview(
    strings: SandboxStrings,
    title: String,
    icon: ImageVector,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(52.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            text = strings.routeDescription,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Host всех dialog/sheet overlay, доступных в sandbox-каталоге.
 *
 * @param dialog выбранный overlay или null, если ничего не открыто.
 * @param strings локализованные demo-строки.
 * @param darkTheme true, если активна тёмная тема.
 * @param backgroundStyle текущий стиль фона документа.
 * @param replaceWhiteBackground true, если белый фон PDF должен заменяться.
 * @param onBackgroundStyleChange callback изменения стиля фона.
 * @param onReplaceWhiteBackgroundChange callback изменения замены белого фона.
 * @param onDismiss callback закрытия overlay.
 * @param onMessage callback для snackbar-сообщений после действия.
 */
@Composable
internal fun SandboxDialogHost(
    dialog: SandboxDialog?,
    strings: SandboxStrings,
    darkTheme: Boolean,
    backgroundStyle: String,
    replaceWhiteBackground: Boolean,
    onBackgroundStyleChange: (String) -> Unit,
    onReplaceWhiteBackgroundChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
) {
    when (dialog) {
        null -> Unit
        SandboxDialog.CreateFolder ->
            CreateFolderDialog(
                state = CreateFolderDialogState(currentName = strings.newFolderName, isConfirmEnabled = true),
                onNameChange = {},
                onConfirm = {
                    onDismiss()
                    onMessage(strings.createdMessage)
                },
                onDismiss = onDismiss,
            )
        SandboxDialog.DeleteFolder ->
            DeleteFolderDialog(
                folderName = strings.folderName,
                onConfirm = {
                    onDismiss()
                    onMessage(strings.deletedMessage)
                },
                onDismiss = onDismiss,
            )
        SandboxDialog.SafMerge ->
            SafMergeDialog(
                existing = sampleRecentFile(strings),
                newUri = "content://notepen/sandbox/${strings.fileName}",
                onMerge = {
                    onDismiss()
                    onMessage(strings.mergedMessage)
                },
                onReject = onDismiss,
            )
        SandboxDialog.Shortcuts ->
            ShortcutsSettingsDialog(
                settings = ShortcutsSettings(),
                onChange = {},
                onDismiss = onDismiss,
                penButtons = remember { MutableStateFlow(emptySet<Int>()) },
                blurEnabled = true,
                onBlurEnabledChange = {},
            )
        SandboxDialog.BackgroundSheet ->
            DocumentBackgroundSettingsSheet(
                currentStyle = backgroundStyle,
                replaceWhiteBackground = replaceWhiteBackground,
                isDark = darkTheme,
                onStyleChange = onBackgroundStyleChange,
                onReplaceWhiteChange = onReplaceWhiteBackgroundChange,
                onDismiss = onDismiss,
            )
        SandboxDialog.Alert ->
            LiquidGlassAlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(strings.alertTitle) },
                text = { Text(strings.alertText) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDismiss()
                            onMessage(strings.alertConfirmed)
                        },
                    ) {
                        Text(strings.confirm)
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text(strings.cancel) }
                },
            )
    }
}
