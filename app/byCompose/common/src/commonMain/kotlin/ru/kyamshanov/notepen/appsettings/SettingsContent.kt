package ru.kyamshanov.notepen.appsettings

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.kyamshanov.notepen.LIQUID_GLASS_TOP_BAR_HEIGHT
import ru.kyamshanov.notepen.LiquidGlassTopBar
import ru.kyamshanov.notepen.blur.GlassBackdropProvider
import ru.kyamshanov.notepen.blur.glassSource
import ru.kyamshanov.notepen.liquidGlassHero
import ru.kyamshanov.notepen.qrconnect.ClientQrScanViewModel
import ru.kyamshanov.notepen.qrconnect.HostDiscoveryViewModel
import ru.kyamshanov.notepen.qrconnect.HostQrPairingViewModel
import ru.kyamshanov.notepen.qrconnect.ManualConnectViewModel
import ru.kyamshanov.notepen.qrconnect.SyncPairingButton
import ru.kyamshanov.notepen.shortcuts.ShortcutsSettingsDialog
import ru.kyamshanov.notepen.shortcuts.rememberShortcutsSettings
import ru.kyamshanov.notepen.sync.domain.port.PeerServer
import ru.kyamshanov.notepen.sync.domain.port.SyncClient
import ru.kyamshanov.notepen.tablet.LocalTabletInputController
import ru.kyamshanov.notepen.titlebar.LocalTitleBarInteraction

/**
 * Экран глобальных настроек приложения. Три раздела:
 * - **Синхронизация** — встроенная [SyncPairingButton], открывающая QR-диалог.
 * - **Шорткаты** — открывает диалог настройки биндингов шорткатов.
 * - **Always-on-display** — глобальный switch (по умолчанию включено).
 */
@Composable
fun SettingsContent(
    component: SettingsComponentImpl,
    hostQrViewModel: HostQrPairingViewModel? = null,
    clientScanViewModel: ClientQrScanViewModel? = null,
    manualConnectViewModel: ManualConnectViewModel? = null,
    peerServer: PeerServer? = null,
    peerClient: SyncClient? = null,
    hostDiscoveryViewModel: HostDiscoveryViewModel? = null,
    modifier: Modifier = Modifier,
) {
    val settings by component.settings.collectAsState()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topBarTotal = statusBarTop + LIQUID_GLASS_TOP_BAR_HEIGHT
    var showShortcutsDialog by remember { mutableStateOf(false) }

    GlassBackdropProvider {
        Box(modifier = modifier.fillMaxSize()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .liquidGlassHero()
                        .glassSource(),
            )
            SettingsList(
                topInset = topBarTotal,
                alwaysOnDisplay = settings.alwaysOnDisplay,
                onAlwaysOnDisplayChange = component::setAlwaysOnDisplay,
                onOpenShortcuts = { showShortcutsDialog = true },
                hostQrViewModel = hostQrViewModel,
                clientScanViewModel = clientScanViewModel,
                manualConnectViewModel = manualConnectViewModel,
                peerServer = peerServer,
                peerClient = peerClient,
                hostDiscoveryViewModel = hostDiscoveryViewModel,
            )
            SettingsTopBar(modifier = Modifier.align(Alignment.TopCenter), onBack = component::onBack)
        }
    }

    if (showShortcutsDialog) {
        ShortcutsDialogHost(onDismiss = { showShortcutsDialog = false })
    }
}

@Composable
private fun SettingsList(
    topInset: Dp,
    alwaysOnDisplay: Boolean,
    onAlwaysOnDisplayChange: (Boolean) -> Unit,
    onOpenShortcuts: () -> Unit,
    hostQrViewModel: HostQrPairingViewModel?,
    clientScanViewModel: ClientQrScanViewModel?,
    manualConnectViewModel: ManualConnectViewModel?,
    peerServer: PeerServer?,
    peerClient: SyncClient?,
    hostDiscoveryViewModel: HostDiscoveryViewModel?,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = topInset + 8.dp,
                        bottom = 16.dp,
                    ),
                ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsActionSection(
            icon = Icons.Default.Sync,
            title = "Синхронизация",
            subtitle = "Подключение устройств по QR или вручную",
        ) {
            SyncPairingButton(
                hostQrViewModel = hostQrViewModel,
                clientScanViewModel = clientScanViewModel,
                manualConnectViewModel = manualConnectViewModel,
                peerServer = peerServer,
                peerClient = peerClient,
                hostDiscoveryViewModel = hostDiscoveryViewModel,
            )
        }
        SettingsClickableSection(
            icon = Icons.Default.Keyboard,
            title = "Шорткаты",
            subtitle = "Сочетания клавиш и кнопок пера",
            onClick = onOpenShortcuts,
        )
        SettingsToggleSection(
            icon = Icons.Default.Lightbulb,
            title = "Не гасить экран",
            subtitle = "Always-on-display: пока приложение открыто, экран не уходит в сон",
            checked = alwaysOnDisplay,
            onCheckedChange = onAlwaysOnDisplayChange,
        )
    }
}

@Composable
private fun SettingsTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val titleBarInteraction = LocalTitleBarInteraction.current
    LiquidGlassTopBar(
        modifier = modifier,
        title = { Text("Настройки") },
        navigationIcon = {
            IconButton(
                onClick = onBack,
                modifier = titleBarInteraction?.interactive(Modifier) ?: Modifier,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                )
            }
        },
    )
}

@Composable
private fun ShortcutsDialogHost(onDismiss: () -> Unit) {
    val shortcutsState = rememberShortcutsSettings()
    val tabletController = LocalTabletInputController.current
    ShortcutsSettingsDialog(
        settings = shortcutsState.value,
        onChange = { shortcutsState.value = it },
        onDismiss = onDismiss,
        penButtons = tabletController.penButtons,
        // Размытие — настройка ридера (см. DetailsContent). Глобальный
        // экран настроек не редактирует StoredReaderSettings — тумблер
        // блокируем в положении «включено» с no-op handler'ом.
        blurEnabled = true,
        onBlurEnabledChange = {},
    )
}

@Composable
private fun SettingsClickableSection(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    SettingsRowSurface(
        modifier =
            Modifier.pointerInput(onClick) {
                detectTapGestures(onTap = { onClick() })
            },
    ) {
        SettingsRow(icon = icon, title = title, subtitle = subtitle, trailing = {})
    }
}

@Composable
private fun SettingsActionSection(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit,
) {
    SettingsRowSurface {
        SettingsRow(icon = icon, title = title, subtitle = subtitle, trailing = trailing)
    }
}

@Composable
private fun SettingsToggleSection(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsRowSurface {
        SettingsRow(
            icon = icon,
            title = title,
            subtitle = subtitle,
            trailing = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
        )
    }
}

@Composable
private fun SettingsRowSurface(
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

@Composable
private fun SettingsRow(
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
