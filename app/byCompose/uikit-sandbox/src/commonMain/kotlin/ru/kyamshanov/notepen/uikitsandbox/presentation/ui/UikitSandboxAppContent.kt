@file:OptIn(ExperimentalMaterial3Api::class)

package ru.kyamshanov.notepen.uikitsandbox.presentation.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.ComponentContext
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.ComposableAppTheme
import ru.kyamshanov.notepen.LiquidGlassCard
import ru.kyamshanov.notepen.LiquidGlassTopBar
import ru.kyamshanov.notepen.background.PaperBackgrounds
import ru.kyamshanov.notepen.blur.GlassBackdropProvider
import ru.kyamshanov.notepen.blur.glassSource
import ru.kyamshanov.notepen.liquidGlassHero
import ru.kyamshanov.notepen.uikitsandbox.UikitSandboxComponent
import ru.kyamshanov.notepen.uikitsandbox.presentation.component.UikitSandboxComponentFactory

/**
 * Общий UI standalone UIKit sandbox для Android и Desktop wrapper-ов.
 *
 * Функция держит состояние темы, локали, ориентации, вкладки и активного overlay,
 * а также создаёт internal component через [UikitSandboxComponentFactory].
 *
 * @param componentFactory factory создания sandbox component.
 * @param componentContext Decompose-контекст root-компонента.
 * @param modifier модификатор корневого Compose-узла.
 */
@Composable
internal fun UikitSandboxAppContent(
    componentFactory: UikitSandboxComponentFactory,
    componentContext: ComponentContext,
    modifier: Modifier = Modifier,
) {
    var darkTheme by remember { mutableStateOf(false) }
    var locale by remember { mutableStateOf(SandboxLocale.Ru) }
    var orientation by remember { mutableStateOf(SandboxPreviewOrientation.Portrait) }
    var selectedTab by remember { mutableStateOf(SandboxTab.Components) }
    var activeDialog by remember { mutableStateOf<SandboxDialog?>(null) }
    var backgroundStyle by remember { mutableStateOf(PaperBackgrounds.PLAIN) }
    var replaceWhiteBackground by remember { mutableStateOf(true) }
    val strings = remember(locale) { SandboxStrings.forLocale(locale) }
    val currentStrings by rememberUpdatedState(strings)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val component =
        remember(componentFactory, componentContext) {
            componentFactory.create(
                componentContext = componentContext,
                onWidgetSelected = { id ->
                    scope.launch {
                        snackbarHostState.showSnackbar(currentStrings.widgetSelected(id))
                    }
                },
            )
        }
    val showMessage: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    SandboxApplyOrientation(orientation)
    SandboxThemeSurface(
        darkTheme = darkTheme,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SandboxTopBar(strings = strings, onMessage = showMessage)
            SandboxControlBar(
                strings = strings,
                darkTheme = darkTheme,
                locale = locale,
                orientation = orientation,
                selectedTab = selectedTab,
                onDarkThemeChange = { darkTheme = it },
                onLocaleChange = { locale = it },
                onOrientationChange = { orientation = it },
                onTabSelected = { selectedTab = it },
            )
            SandboxPreviewFrame(
                strings = strings,
                orientation = orientation,
                selectedTab = selectedTab,
                component = component,
                darkTheme = darkTheme,
                onMessage = showMessage,
                onDialogRequest = { activeDialog = it },
                modifier = Modifier.weight(1f),
            )
        }
        SandboxDialogHost(
            dialog = activeDialog,
            strings = strings,
            darkTheme = darkTheme,
            backgroundStyle = backgroundStyle,
            replaceWhiteBackground = replaceWhiteBackground,
            onBackgroundStyleChange = { backgroundStyle = it },
            onReplaceWhiteBackgroundChange = { replaceWhiteBackground = it },
            onDismiss = { activeDialog = null },
            onMessage = showMessage,
        )
    }
}

@Composable
private fun SandboxThemeSurface(
    darkTheme: Boolean,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    ComposableAppTheme(darkTheme = darkTheme, dynamicColor = false) {
        GlassBackdropProvider {
            Scaffold(
                modifier = modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            ) { contentPadding ->
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(contentPadding),
                ) {
                    // glassSource записывает только фон; glass-панели выше сэмплируют этот layer.
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .liquidGlassHero()
                                .glassSource(),
                    )
                    content()
                }
            }
        }
    }
}

@Composable
private fun SandboxTopBar(
    strings: SandboxStrings,
    onMessage: (String) -> Unit,
) {
    LiquidGlassTopBar(
        title = { Text(strings.appTitle) },
        navigationIcon = {
            Icon(
                imageVector = Icons.Default.Widgets,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        },
        actions = {
            IconButton(onClick = { onMessage(strings.notificationMessage) }) {
                Icon(Icons.Default.Notifications, contentDescription = strings.notificationAction)
            }
        },
    )
}

@Composable
private fun SandboxControlBar(
    strings: SandboxStrings,
    darkTheme: Boolean,
    locale: SandboxLocale,
    orientation: SandboxPreviewOrientation,
    selectedTab: SandboxTab,
    onDarkThemeChange: (Boolean) -> Unit,
    onLocaleChange: (SandboxLocale) -> Unit,
    onOrientationChange: (SandboxPreviewOrientation) -> Unit,
    onTabSelected: (SandboxTab) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = CONTROL_BAR_ALPHA))
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingSwitch(
                checked = darkTheme,
                icon = if (darkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                label = if (darkTheme) strings.dark else strings.light,
                onCheckedChange = onDarkThemeChange,
            )
            LocaleToggle(strings = strings, locale = locale, onLocaleChange = onLocaleChange)
            OrientationToggle(strings = strings, orientation = orientation, onOrientationChange = onOrientationChange)
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SandboxTab.entries.forEach { tab ->
                FilterChip(
                    selected = selectedTab == tab,
                    onClick = { onTabSelected(tab) },
                    label = { Text(strings.tabLabel(tab)) },
                    leadingIcon = {
                        Icon(tab.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    checked: Boolean,
    icon: ImageVector,
    label: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun LocaleToggle(
    strings: SandboxStrings,
    locale: SandboxLocale,
    onLocaleChange: (SandboxLocale) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(20.dp))
        SandboxLocale.entries.forEach { item ->
            FilterChip(
                selected = locale == item,
                onClick = { onLocaleChange(item) },
                label = { Text(strings.localeLabel(item)) },
            )
        }
    }
}

@Composable
private fun OrientationToggle(
    strings: SandboxStrings,
    orientation: SandboxPreviewOrientation,
    onOrientationChange: (SandboxPreviewOrientation) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Default.ScreenRotation, contentDescription = null, modifier = Modifier.size(20.dp))
        SandboxPreviewOrientation.entries.forEach { item ->
            val icon = if (item == SandboxPreviewOrientation.Portrait) Icons.Default.Smartphone else Icons.Default.DesktopWindows
            FilterChip(
                selected = orientation == item,
                onClick = { onOrientationChange(item) },
                label = { Text(strings.orientationLabel(item)) },
                leadingIcon = {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                },
            )
        }
    }
}

@Composable
private fun SandboxPreviewFrame(
    strings: SandboxStrings,
    orientation: SandboxPreviewOrientation,
    selectedTab: SandboxTab,
    component: UikitSandboxComponent,
    darkTheme: Boolean,
    onMessage: (String) -> Unit,
    onDialogRequest: (SandboxDialog) -> Unit,
    modifier: Modifier = Modifier,
) {
    val frameModifier =
        if (orientation == SandboxPreviewOrientation.Portrait) {
            Modifier.fillMaxHeight().widthIn(max = 520.dp).aspectRatio(PORTRAIT_ASPECT)
        } else {
            Modifier.fillMaxWidth().heightIn(max = 720.dp).aspectRatio(LANDSCAPE_ASPECT)
        }
    Box(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        LiquidGlassCard(
            modifier =
                frameModifier
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp)),
            onClick = null,
        ) {
            AnimatedContent(
                targetState = selectedTab,
                label = "sandbox-tab",
                modifier = Modifier.fillMaxSize(),
            ) { tab ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    item { PreviewHeader(strings = strings, orientation = orientation, selectedTab = tab) }
                    when (tab) {
                        SandboxTab.Components -> {
                            item { FoundationShowcase(strings = strings, onMessage = onMessage) }
                            item { ToolShowcase(strings = strings) }
                        }
                        SandboxTab.Project -> {
                            item { ProjectFragmentsShowcase(strings = strings, onMessage = onMessage) }
                        }
                        SandboxTab.Overlays -> {
                            item {
                                OverlayShowcase(
                                    strings = strings,
                                    darkTheme = darkTheme,
                                    onDialogRequest = onDialogRequest,
                                    onMessage = onMessage,
                                )
                            }
                        }
                        SandboxTab.Flow -> {
                            item {
                                FlowShowcase(
                                    strings = strings,
                                    component = component,
                                    onMessage = onMessage,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewHeader(
    strings: SandboxStrings,
    orientation: SandboxPreviewOrientation,
    selectedTab: SandboxTab,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = strings.previewTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = strings.previewSubtitle(selectedTab, orientation),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ElevatedAssistChip(
            onClick = {},
            label = { Text(strings.orientationLabel(orientation)) },
            leadingIcon = {
                val icon =
                    if (orientation == SandboxPreviewOrientation.Portrait) {
                        Icons.Default.Smartphone
                    } else {
                        Icons.Default.DesktopWindows
                    }
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            },
        )
    }
}
