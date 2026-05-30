package ru.kyamshanov.notepen.library.ui

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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.LIQUID_GLASS_TOP_BAR_HEIGHT
import ru.kyamshanov.notepen.LiquidGlassTopBar
import ru.kyamshanov.notepen.blur.GlassBackdropProvider
import ru.kyamshanov.notepen.blur.glassSource
import ru.kyamshanov.notepen.liquidGlassHero
import ru.kyamshanov.notepen.titlebar.LocalTitleBarInteraction

/**
 * Экран «Источники библиотек»: список подключённых библиотек (иконка типа,
 * бейдж роли, статус соединения, отключение), кнопка «Добавить библиотеку»
 * (локальная папка — desktop; LAN-пир — из уже сопряжённых; GitHub — диалог
 * с owner/name и токеном, M3), тумблер «Открывать при старте» и (desktop)
 * «Открыть свою библиотеку».
 *
 * Стиль повторяет [ru.kyamshanov.notepen.appsettings.SettingsContent]:
 * GlassBackdropProvider + LiquidGlassTopBar + скруглённые Surface-строки.
 */
@Composable
fun LibrarySourcesContent(
    component: LibrarySourcesComponentImpl,
    modifier: Modifier = Modifier,
) {
    val state by component.viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(state.errorMessage) {
        val msg = state.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        component.viewModel.onErrorShown()
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topBarTotal = statusBarTop + LIQUID_GLASS_TOP_BAR_HEIGHT

    GlassBackdropProvider {
        Box(modifier = modifier.fillMaxSize()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .liquidGlassHero()
                        .glassSource(),
            )
            LibrarySourcesList(
                topInset = topBarTotal,
                state = state,
                onDisconnect = component.viewModel::disconnect,
                onAddLan = component.viewModel::addLanLibrary,
                onAddLocal = {
                    val pick = component.onPickLocalFolder ?: return@LibrarySourcesList
                    coroutineScope.launch {
                        pick()?.let { component.viewModel.addLocalLibrary(it) }
                    }
                },
                localFolderSupported = component.onPickLocalFolder != null,
                onAddGitHub = component.viewModel::addGitHubLibrary,
                googleDriveSupported = state.googleDriveSupported,
                onAddGoogleDrive = component.viewModel::addGoogleDriveLibrary,
                onToggleStartup = component.viewModel::setOpenLibraryAtStartup,
                onOpenMyLibrary = component.viewModel::openMyLibrary,
            )
            state.googleDevicePrompt?.let { prompt ->
                GoogleDeviceCodeDialog(
                    prompt = prompt,
                    onCancel = component.viewModel::cancelGoogleSignIn,
                )
            }
            LibrarySourcesTopBar(
                modifier = Modifier.align(Alignment.TopCenter),
                onBack = component::onBack,
            )
            SnackbarHost(
                hostState = snackbarHostState,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars),
            )
        }
    }
}

@Composable
private fun LibrarySourcesList(
    topInset: Dp,
    state: LibrarySourcesUiState,
    onDisconnect: (String) -> Unit,
    onAddLan: (peerId: String, host: String?) -> Unit,
    onAddLocal: () -> Unit,
    localFolderSupported: Boolean,
    onAddGitHub: (repo: String, token: String) -> Unit,
    googleDriveSupported: Boolean,
    onAddGoogleDrive: (folderId: String) -> Unit,
    onToggleStartup: (Boolean) -> Unit,
    onOpenMyLibrary: () -> Unit,
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
        SectionHeader("Подключённые библиотеки")
        if (state.libraries.isEmpty()) {
            EmptyHint("Пока ни одной библиотеки. Добавьте локальную папку или подключитесь к устройству по LAN.")
        } else {
            state.libraries.forEach { lib ->
                LibraryRowSurface {
                    LibraryRow(model = lib, onDisconnect = { onDisconnect(lib.id) })
                }
            }
        }

        SectionHeader("Добавить библиотеку")
        AddLibraryRow(
            availablePeers = state.availablePeers,
            localFolderSupported = localFolderSupported,
            onAddLan = onAddLan,
            onAddLocal = onAddLocal,
            onAddGitHub = onAddGitHub,
            googleDriveSupported = googleDriveSupported,
            onAddGoogleDrive = onAddGoogleDrive,
        )

        SectionHeader("Настройки")
        LibrarySettingsSection(
            state = state,
            onToggleStartup = onToggleStartup,
            onOpenMyLibrary = onOpenMyLibrary,
        )
    }
}

@Composable
private fun LibrarySettingsSection(
    state: LibrarySourcesUiState,
    onToggleStartup: (Boolean) -> Unit,
    onOpenMyLibrary: () -> Unit,
) {
    LibraryRowSurface {
        ToggleRow(
            icon = Icons.Default.RocketLaunch,
            title = "Открывать при старте",
            subtitle = "Автоматически подключать сохранённые библиотеки при запуске",
            checked = state.openLibraryAtStartup,
            onCheckedChange = onToggleStartup,
        )
    }
    if (state.serveOverLanSupported) {
        LibraryRowSurface {
            ActionRow(
                icon = Icons.Default.Wifi,
                title = "Открыть свою библиотеку",
                subtitle =
                    if (state.serving) {
                        "Библиотека доступна другим устройствам в локальной сети"
                    } else {
                        "Раздать свою библиотеку другим устройствам по локальной сети"
                    },
                trailing = {
                    OutlinedButton(onClick = onOpenMyLibrary, enabled = !state.serving) {
                        Text(if (state.serving) "Раздаётся" else "Открыть")
                    }
                },
            )
        }
    }
}

@Composable
private fun LibraryRow(
    model: LibrarySourceUiModel,
    onDisconnect: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Icon(
            imageVector = kindIcon(model.kind),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(model.displayName, style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(model.connectionState)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${connectionLabel(model.connectionState)} · ${model.bookCount} книг",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        AssistChip(
            onClick = {},
            enabled = false,
            label = { Text(roleLabel(model.role)) },
        )
        IconButton(onClick = onDisconnect) {
            Icon(Icons.Default.Delete, contentDescription = "Отключить")
        }
    }
}

@Composable
private fun AddLibraryLabel() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("Добавить библиотеку", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Локальная папка, LAN, GitHub или Google Drive",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AddLibraryRow(
    availablePeers: List<AvailablePeerUiModel>,
    localFolderSupported: Boolean,
    onAddLan: (peerId: String, host: String?) -> Unit,
    onAddLocal: () -> Unit,
    onAddGitHub: (repo: String, token: String) -> Unit,
    googleDriveSupported: Boolean,
    onAddGoogleDrive: (folderId: String) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var gitHubDialogVisible by remember { mutableStateOf(false) }
    var googleDriveDialogVisible by remember { mutableStateOf(false) }
    LibraryRowSurface(
        modifier =
            Modifier.pointerInput(Unit) {
                detectTapGestures(onTap = { menuExpanded = true })
            },
    ) {
        Box {
            AddLibraryLabel()
            AddLibraryMenu(
                expanded = menuExpanded,
                onDismiss = { menuExpanded = false },
                availablePeers = availablePeers,
                localFolderSupported = localFolderSupported,
                googleDriveSupported = googleDriveSupported,
                onAddLan = { peerId, host ->
                    menuExpanded = false
                    onAddLan(peerId, host)
                },
                onAddLocal = {
                    menuExpanded = false
                    onAddLocal()
                },
                onAddGitHub = {
                    menuExpanded = false
                    gitHubDialogVisible = true
                },
                onAddGoogleDrive = {
                    menuExpanded = false
                    googleDriveDialogVisible = true
                },
            )
        }
    }
    if (gitHubDialogVisible) {
        GitHubLibraryDialog(
            onDismiss = { gitHubDialogVisible = false },
            onConfirm = { repo, token ->
                gitHubDialogVisible = false
                onAddGitHub(repo, token)
            },
        )
    }
    if (googleDriveDialogVisible) {
        GoogleDriveLibraryDialog(
            onDismiss = { googleDriveDialogVisible = false },
            onConfirm = { folderId ->
                googleDriveDialogVisible = false
                onAddGoogleDrive(folderId)
            },
        )
    }
}

@Composable
private fun AddLibraryMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    availablePeers: List<AvailablePeerUiModel>,
    localFolderSupported: Boolean,
    googleDriveSupported: Boolean,
    onAddLan: (peerId: String, host: String?) -> Unit,
    onAddLocal: () -> Unit,
    onAddGitHub: () -> Unit,
    onAddGoogleDrive: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (localFolderSupported) {
            DropdownMenuItem(
                text = { Text("Локальная папка…") },
                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                onClick = onAddLocal,
            )
        }
        if (availablePeers.isEmpty()) {
            DropdownMenuItem(
                text = { Text("Устройства по LAN не найдены") },
                leadingIcon = { Icon(Icons.Default.Wifi, contentDescription = null) },
                enabled = false,
                onClick = {},
            )
        } else {
            availablePeers.forEach { peer ->
                DropdownMenuItem(
                    text = { Text(peer.displayName) },
                    leadingIcon = { Icon(Icons.Default.Computer, contentDescription = null) },
                    onClick = { onAddLan(peer.peerId, peer.host) },
                )
            }
        }
        DropdownMenuItem(
            text = { Text("GitHub…") },
            leadingIcon = { Icon(Icons.Default.Cloud, contentDescription = null) },
            onClick = onAddGitHub,
        )
        if (googleDriveSupported) {
            DropdownMenuItem(
                text = { Text("Google Drive…") },
                leadingIcon = { Icon(Icons.Default.Cloud, contentDescription = null) },
                onClick = onAddGoogleDrive,
            )
        }
    }
}

/**
 * Dialog to add a GitHub-repo library: the user types the `owner/name` slug and an optional
 * access token. A token grants the Librarian role (upload); leaving it blank connects read-only.
 * The token is sent as-is to the registry and persisted in plaintext (see [LibraryConnection.GitHub]
 * KDoc) — the field warns the user accordingly.
 */
@Composable
private fun GitHubLibraryDialog(
    onDismiss: () -> Unit,
    onConfirm: (repo: String, token: String) -> Unit,
) {
    var repo by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("GitHub-библиотека") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Репозиторий читается как полка: книги берутся из папки books/.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = repo,
                    onValueChange = { repo = it },
                    singleLine = true,
                    label = { Text("Репозиторий (owner/name)") },
                    placeholder = { Text("octocat/library") },
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text("Токен (для записи; необязательно)") },
                    supportingText = {
                        Text(
                            "С токеном — роль Библиотекарь (загрузка). Хранится в открытом виде.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(repo, token) },
                enabled = repo.trim().trim('/').contains('/'),
            ) {
                Text("Подключить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

/**
 * Dialog to add a Google Drive library: the user pastes the shared folder's id. Confirming starts the
 * Google OAuth device flow (read-only scope → Reader role); the device-code panel
 * ([GoogleDeviceCodeDialog]) then appears while the user authorizes in a browser.
 */
@Composable
private fun GoogleDriveLibraryDialog(
    onDismiss: () -> Unit,
    onConfirm: (folderId: String) -> Unit,
) {
    var folderId by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Google Drive-библиотека") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Папка Google Drive читается как полка. Вставьте id папки (из ссылки drive.google.com/…/folders/<id>).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = folderId,
                    onValueChange = { folderId = it },
                    singleLine = true,
                    label = { Text("Id папки") },
                    placeholder = { Text("1A2b3C…") },
                    supportingText = {
                        Text(
                            "Откроется вход через Google. Доступ только для чтения — роль Читатель.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(folderId) },
                enabled = folderId.trim().isNotEmpty(),
            ) {
                Text("Войти и подключить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

/**
 * Shows the active Google device-flow prompt: the user code to type and the verification URL to
 * open. Stays up (non-dismissable except via «Отмена») while the ViewModel polls for authorization.
 */
@Composable
private fun GoogleDeviceCodeDialog(
    prompt: GoogleDeviceCodeUiModel,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Вход через Google") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Откройте в браузере:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(prompt.verificationUri, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "и введите код:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(prompt.userCode, style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "Ожидание подтверждения…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Отмена") }
        },
    )
}

@Composable
private fun LibrarySourcesTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val titleBarInteraction = LocalTitleBarInteraction.current
    LiquidGlassTopBar(
        modifier = modifier,
        title = { Text("Источники библиотек") },
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
