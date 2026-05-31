package ru.kyamshanov.notepen.mainscreen.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.LIQUID_GLASS_TOP_BAR_HEIGHT
import ru.kyamshanov.notepen.LiquidGlassTopBar
import ru.kyamshanov.notepen.NotePenIcons
import ru.kyamshanov.notepen.RailOrientation
import ru.kyamshanov.notepen.SessionsMenu
import ru.kyamshanov.notepen.WheelScrollButtons
import ru.kyamshanov.notepen.blur.GlassBackdropProvider
import ru.kyamshanov.notepen.blur.LocalBlurEnabled
import ru.kyamshanov.notepen.blur.glassSource
import ru.kyamshanov.notepen.currentWindowSizePx
import ru.kyamshanov.notepen.fadingEdges
import ru.kyamshanov.notepen.wheelItem
import ru.kyamshanov.notepen.liquidGlassHero
import ru.kyamshanov.notepen.mainscreen.platform.isDragAndDropSupported
import ru.kyamshanov.notepen.mainscreen.ui.MainScreenIntent
import ru.kyamshanov.notepen.mainscreen.ui.component.EmptyState
import ru.kyamshanov.notepen.mainscreen.ui.component.FolderCard
import ru.kyamshanov.notepen.mainscreen.ui.component.LibraryFolderCard
import ru.kyamshanov.notepen.mainscreen.ui.component.PeerCard
import ru.kyamshanov.notepen.mainscreen.ui.component.RecentFileCard
import ru.kyamshanov.notepen.mainscreen.ui.component.extractExternalFileUris
import ru.kyamshanov.notepen.mainscreen.ui.component.isExternalFileDrop
import ru.kyamshanov.notepen.mainscreen.ui.dialog.CreateFolderDialog
import ru.kyamshanov.notepen.mainscreen.ui.dialog.DeleteFolderDialog
import ru.kyamshanov.notepen.mainscreen.ui.dialog.SafMergeDialog
import ru.kyamshanov.notepen.mainscreen.ui.model.DragState
import ru.kyamshanov.notepen.mainscreen.ui.model.ErrorEvent
import ru.kyamshanov.notepen.mainscreen.ui.model.MainScreenUiState
import ru.kyamshanov.notepen.mainscreen.ui.model.SuccessEvent
import ru.kyamshanov.notepen.qrconnect.ClientQrScanViewModel
import ru.kyamshanov.notepen.qrconnect.HostDiscoveryViewModel
import ru.kyamshanov.notepen.qrconnect.HostQrPairingViewModel
import ru.kyamshanov.notepen.qrconnect.ManualConnectViewModel
import ru.kyamshanov.notepen.qrconnect.SyncPairingButton
import ru.kyamshanov.notepen.session.SessionData
import ru.kyamshanov.notepen.session.createSessionRepository
import ru.kyamshanov.notepen.session.seedFilePath
import ru.kyamshanov.notepen.sync.domain.port.PeerServer
import ru.kyamshanov.notepen.sync.domain.port.SyncClient
import ru.kyamshanov.notepen.titlebar.LocalTitleBarInteraction

private val WIDE_SCREEN_THRESHOLD: Dp = 600.dp
private val RECENT_CARD_WIDTH: Dp = 132.dp

// Крайние карточки «Недавние» в барабане ужимаются мягче иконок тул-рейла: они
// крупные, и сильное ужатие читалось бы как обрезка, а не как карусельный спад.
private const val RECENT_CARD_MIN_SCALE: Float = 0.82f

/**
 * Главный контент экрана: адаптивная вёрстка с TopAppBar, списком файлов и папок,
 * диалогами и Snackbar для ошибок.
 *
 * @param state Текущее состояние экрана.
 * @param onIntent Обработчик интентов.
 * @param onBack Возврат к предыдущему документу, когда библиотека открыта поверх
 *        редактора (кнопкой «+»). `null` — когда главный экран является корнем
 *        навигации; в этом случае кнопка «назад» не показывается.
 * @param hostQrViewModel Вьюмодель хост-панели QR для кнопки синхронизации; `null` — кнопка скрыта.
 * @param clientScanViewModel Вьюмодель сканирования QR на клиенте для кнопки синхронизации.
 * @param manualConnectViewModel Вьюмодель ручного подключения для кнопки синхронизации.
 * @param peerServer Хост-сервер для индикатора статуса синхронизации.
 * @param peerClient Клиент синхронизации для индикатора статуса и панели подключения.
 * @param modifier Модификатор компонента.
 */
@Composable
fun MainContent(
    state: MainScreenUiState,
    onIntent: (MainScreenIntent) -> Unit,
    onBack: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    onOpenLibrarySources: (() -> Unit)? = null,
    hostQrViewModel: HostQrPairingViewModel? = null,
    clientScanViewModel: ClientQrScanViewModel? = null,
    manualConnectViewModel: ManualConnectViewModel? = null,
    peerServer: PeerServer? = null,
    peerClient: SyncClient? = null,
    hostDiscoveryViewModel: HostDiscoveryViewModel? = null,
    modifier: Modifier = Modifier,
) {
    val windowWidth = currentWindowSizePx().width
    val isWide = with(LocalDensity.current) { windowWidth.toDp() >= WIDE_SCREEN_THRESHOLD }
    val snackbarHostState = remember { SnackbarHostState() }

    // Sessions menu (restore-only on the library). The repository is owned here so
    // the menu can read the named list and save the pending restore; lastSession is
    // the crash-survivor autosave for the "restore last" button.
    val coroutineScope = rememberCoroutineScope()
    val sessionRepository = remember { createSessionRepository() }
    val lastSession by produceState<SessionData?>(initialValue = null, sessionRepository) {
        value = sessionRepository.loadAutosave()
    }
    var sessionsExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { onIntent(MainScreenIntent.ScreenVisible) }

    val errorMessage =
        when (state.errorEvent) {
            ErrorEvent.FileNotFound -> "Файл не найден"
            ErrorEvent.FileError -> "Ошибка доступа к файлу"
            ErrorEvent.HistoryFlushFailed -> "Не удалось обновить историю"
            ErrorEvent.ThumbnailGenerationFailed -> "Не удалось создать миниатюру"
            ErrorEvent.FolderLimitExceeded -> "Достигнут лимит папок"
            ErrorEvent.FolderNameCharsInvalid -> "Недопустимые символы в имени папки"
            ErrorEvent.FolderOperationFailed -> "Ошибка операции с папкой"
            ErrorEvent.FileNotInHistory -> "Файл не найден в истории"
            ErrorEvent.RemoteDocumentNotFound -> "Документ удалён на хосте"
            ErrorEvent.RemoteDocumentTimeout -> "Хост не отвечает — попробуйте ещё раз"
            ErrorEvent.RemoteDocumentFailed -> "Не удалось получить файл с хоста"
            ErrorEvent.LibraryCopyFailed -> "Не удалось добавить в Библиотеку"
            null -> null
        }

    LaunchedEffect(state.errorEvent) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
        }
    }

    LaunchedEffect(state.successEvent) {
        val successEvent = state.successEvent ?: return@LaunchedEffect
        val message =
            when (successEvent) {
                is SuccessEvent.FileAddedToFolder -> "Файл добавлен в «${successEvent.folderName}»"
                SuccessEvent.FileAlreadyInFolder -> "Файл уже есть в этой папке"
                SuccessEvent.FileAddedToLibrary -> "Книга добавлена в Библиотеку"
            }
        snackbarHostState.showSnackbar(message)
        onIntent(MainScreenIntent.OnSuccessEventHandled)
    }

    val titleBarInteraction = LocalTitleBarInteraction.current
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topBarTotal = statusBarTop + LIQUID_GLASS_TOP_BAR_HEIGHT
    var isExternalDropHovered by remember { mutableStateOf(false) }
    val libraryDropTarget =
        remember(onIntent) {
            object : DragAndDropTarget {
                override fun onDrop(event: DragAndDropEvent): Boolean {
                    isExternalDropHovered = false
                    val uris = extractExternalFileUris(event)
                    return if (uris.isNotEmpty()) {
                        onIntent(MainScreenIntent.ExternalFilesDroppedOnLibrary(uris))
                        true
                    } else {
                        false
                    }
                }

                override fun onEntered(event: DragAndDropEvent) {
                    isExternalDropHovered = true
                }

                override fun onExited(event: DragAndDropEvent) {
                    isExternalDropHovered = false
                }

                override fun onEnded(event: DragAndDropEvent) {
                    isExternalDropHovered = false
                }
            }
        }
    GlassBackdropProvider {
        Box(modifier = modifier.fillMaxSize()) {
            // Hero-фон: единственный glassSource. Все glass-поверхности (бар,
            // карточки) преломляют именно градиент, а не сам список — это даёт
            // спокойное стекло, не "дрожащее" под прокруткой.
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .liquidGlassHero()
                        .glassSource(),
            )
            // Контент над фоном — список с карточками, drag-and-drop, hover-border.
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .then(
                            if (isDragAndDropSupported) {
                                Modifier.dragAndDropTarget(
                                    shouldStartDragAndDrop = { event -> event.isExternalFileDrop() },
                                    target = libraryDropTarget,
                                )
                            } else {
                                Modifier
                            },
                        )
                        .border(
                            width = if (isExternalDropHovered) 2.dp else 0.dp,
                            color =
                                if (isExternalDropHovered) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.Transparent
                                },
                        ),
            ) {
                when {
                    state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    state.recentFiles.isEmpty() &&
                        state.folders.isEmpty() &&
                        state.peers.isEmpty() &&
                        state.libraries.isNullOrEmpty() ->
                        EmptyState(
                            onOpenFile = { onIntent(MainScreenIntent.OpenFilePicker) },
                            modifier = Modifier.fillMaxSize().padding(top = topBarTotal),
                        )
                    else -> RecentFilesAndFoldersList(state, onIntent, isWide, topInset = topBarTotal)
                }
            }

            LiquidGlassTopBar(
                modifier = Modifier.align(Alignment.TopCenter),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // NotePen brand mark: project's pen-brush glyph on a tinted
                        // primaryContainer chip — a lightweight stand-in until a
                        // dedicated logo vector ships in composeResources.
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .size(22.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(6.dp),
                                    ),
                        ) {
                            Icon(
                                imageVector = NotePenIcons.Brush,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("NotePen")
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = titleBarInteraction?.interactive(Modifier) ?: Modifier,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Назад к документу",
                            )
                        }
                    }
                },
                actions = {
                    if (isWide) {
                        TextButton(
                            onClick = { onIntent(MainScreenIntent.OpenFilePicker) },
                            modifier = titleBarInteraction?.interactive(Modifier) ?: Modifier,
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Открыть")
                        }
                    }
                    IconButton(
                        onClick = { onIntent(MainScreenIntent.OpenCreateFolderDialog) },
                        modifier = titleBarInteraction?.interactive(Modifier) ?: Modifier,
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "Новая папка")
                    }
                    Box {
                        IconButton(
                            onClick = { sessionsExpanded = true },
                            modifier = titleBarInteraction?.interactive(Modifier) ?: Modifier,
                        ) {
                            Icon(Icons.Default.Layers, contentDescription = "Сессии")
                        }
                        SessionsMenu(
                            expanded = sessionsExpanded,
                            sessionRepository = sessionRepository,
                            lastSession = lastSession,
                            onCaptureCurrent = null,
                            onRestore = { data ->
                                sessionsExpanded = false
                                coroutineScope.launch {
                                    sessionRepository.savePendingRestore(data)
                                    data.seedFilePath()?.let {
                                        onIntent(MainScreenIntent.RestoreSession(it))
                                    }
                                }
                            },
                            onDismiss = { sessionsExpanded = false },
                        )
                    }
                    SyncPairingButton(
                        hostQrViewModel = hostQrViewModel,
                        clientScanViewModel = clientScanViewModel,
                        manualConnectViewModel = manualConnectViewModel,
                        peerServer = peerServer,
                        peerClient = peerClient,
                        hostDiscoveryViewModel = hostDiscoveryViewModel,
                    )
                    if (onOpenLibrarySources != null) {
                        IconButton(
                            onClick = onOpenLibrarySources,
                            modifier = titleBarInteraction?.interactive(Modifier) ?: Modifier,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.LibraryBooks,
                                contentDescription = "Источники библиотек",
                            )
                        }
                    }
                    if (onOpenSettings != null) {
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = titleBarInteraction?.interactive(Modifier) ?: Modifier,
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Настройки")
                        }
                    }
                },
            )

            if (!isWide) {
                FloatingActionButton(
                    onClick = { onIntent(MainScreenIntent.OpenFilePicker) },
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(16.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Открыть файл")
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars),
            )
        }
    }

    state.createFolderDialog?.let { dialog ->
        CreateFolderDialog(
            state = dialog,
            onNameChange = { onIntent(MainScreenIntent.FolderDialogNameChanged(it)) },
            onConfirm = { onIntent(MainScreenIntent.CreateFolder(dialog.currentName)) },
            onDismiss = { onIntent(MainScreenIntent.DismissCreateFolderDialog) },
        )
    }

    state.deleteFolderDialog?.let { dialog ->
        DeleteFolderDialog(
            folderName = dialog.folderName,
            onConfirm = { onIntent(MainScreenIntent.DeleteFolder(dialog.folderId)) },
            onDismiss = { onIntent(MainScreenIntent.DismissDeleteFolderDialog) },
        )
    }

    state.safMergeDialog?.let { dialog ->
        SafMergeDialog(
            existing = dialog.existingRecord,
            newUri = dialog.newUri,
            onMerge = {
                onIntent(
                    MainScreenIntent.MergeSafRecords(
                        keepId = dialog.existingRecord.id,
                        discardId = "",
                        newUri = dialog.newUri,
                    ),
                )
            },
            onReject = {
                onIntent(
                    MainScreenIntent.RejectSafMerge(
                        existingId = dialog.existingRecord.id,
                        newUri = dialog.newUri,
                    ),
                )
            },
        )
    }
}

@Composable
private fun RecentFilesAndFoldersList(
    state: MainScreenUiState,
    onIntent: (MainScreenIntent) -> Unit,
    isWide: Boolean,
    topInset: Dp = 0.dp,
) {
    if (isWide) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 280.dp),
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = topInset + 8.dp,
                    bottom = 16.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            // 8dp pulls section headers tight to their content; the wider 16dp
            // section break is added explicitly as a Spacer between sections.
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (state.peers.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Подключённые устройства") }
                items(state.peers, key = { "peer_${it.peerId}" }) { peer ->
                    PeerCard(
                        model = peer,
                        onClick = {
                            onIntent(MainScreenIntent.OpenPeer(peer.peerId, peer.displayName))
                        },
                    )
                }
            }
            if (state.recentFiles.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Недавние файлы") }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    RecentFilesRow(state, onIntent)
                }
            }
            // Библиотеки закреплены первыми плитками в секции «Папки» — по карточке
            // на каждую подключённую библиотеку, рядом с обычными папками пользователя.
            val libraries = state.libraries.orEmpty()
            if (libraries.isNotEmpty() || state.folders.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Папки") }
                // Без GridItemSpan(maxLineSpan): карточки библиотек встают в обычные
                // ячейки Adaptive-грида рядом с папками, а не растягиваются на всю строку.
                items(libraries, key = { "library_${it.id}" }) { library ->
                    LibraryFolderCard(
                        model = library,
                        onClick = { onIntent(MainScreenIntent.OpenLibraryFolder(library.id)) },
                        onDropInternalUri = { uri -> onIntent(MainScreenIntent.AddToLibrary(library.id, uri)) },
                        onDropExternalFiles = { uris ->
                            uris.distinct().forEach { onIntent(MainScreenIntent.AddToLibrary(library.id, it)) }
                        },
                    )
                }
                items(state.folders, key = { "folder_${it.id}" }) { folder ->
                    FolderCard(
                        model = folder,
                        onClick = { onIntent(MainScreenIntent.OpenFolder(folder.id, folder.name)) },
                        onDelete = { onIntent(MainScreenIntent.RequestDeleteFolder(folder.id)) },
                        onDropFile = { onIntent(MainScreenIntent.DropOnFolder(folderId = folder.id)) },
                        onDropExternalFiles = { uris ->
                            onIntent(MainScreenIntent.ExternalFilesDroppedOnFolder(folder.id, uris))
                        },
                    )
                }
            }
        }
    } else {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = topInset + 8.dp,
                    bottom = 16.dp,
                ),
        ) {
            if (state.peers.isNotEmpty()) {
                item { SectionHeader("Подключённые устройства") }
                items(state.peers, key = { "peer_${it.peerId}" }) { peer ->
                    PeerCard(
                        model = peer,
                        onClick = {
                            onIntent(MainScreenIntent.OpenPeer(peer.peerId, peer.displayName))
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
            if (state.recentFiles.isNotEmpty()) {
                item { SectionHeader("Недавние файлы") }
                item { RecentFilesRow(state, onIntent) }
            }
            // Библиотеки — первыми плитками в секции «Папки» (по карточке на каждую);
            // см. развёрнутую логику выше в широком layout'е.
            val libraries = state.libraries.orEmpty()
            if (libraries.isNotEmpty() || state.folders.isNotEmpty()) {
                item { Spacer(Modifier.height(24.dp)) }
                item { SectionHeader("Папки") }
                items(libraries, key = { "library_${it.id}" }) { library ->
                    LibraryFolderCard(
                        model = library,
                        onClick = { onIntent(MainScreenIntent.OpenLibraryFolder(library.id)) },
                        onDropInternalUri = { uri -> onIntent(MainScreenIntent.AddToLibrary(library.id, uri)) },
                        onDropExternalFiles = { uris ->
                            uris.distinct().forEach { onIntent(MainScreenIntent.AddToLibrary(library.id, it)) }
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                    )
                }
                items(state.folders, key = { "folder_${it.id}" }) { folder ->
                    FolderCard(
                        model = folder,
                        onClick = { onIntent(MainScreenIntent.OpenFolder(folder.id, folder.name)) },
                        onDelete = { onIntent(MainScreenIntent.RequestDeleteFolder(folder.id)) },
                        onDropFile = { onIntent(MainScreenIntent.DropOnFolder(folderId = folder.id)) },
                        onDropExternalFiles = { uris ->
                            onIntent(MainScreenIntent.ExternalFilesDroppedOnFolder(folder.id, uris))
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentFilesRow(
    state: MainScreenUiState,
    onIntent: (MainScreenIntent) -> Unit,
) {
    // Карусельный спад: плитки уменьшаются, тускнеют и подтягиваются к центру по
    // мере приближения к краям viewport. Тот же «барабан», что у тул-рейла и
    // ридера, — общий [wheelItem]: трансформа целиком в graphicsLayer (footprint
    // не трогаем), поэтому видимый диапазон под скроллом не «осциллирует».
    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxWidth()) {
        // Внутри скроллящейся полосы отключаем live-сэмплинг backdrop у
        // GlassSurface: каждая карточка обновляет `positionInWindow` через
        // `onGloballyPositioned` — асинхронно draw-pass'у, и под быстрым
        // скроллом блюр успевает сэмплить по устаревшей координате на 1 кадр
        // → визуально мерцает. Flat fallback (tint + outline) выглядит так же
        // "frosted", но без рассинхронизации и без 8× сэмплинга backdrop.
        CompositionLocalProvider(LocalBlurEnabled provides false) {
            LazyRow(
                state = listState,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        // Viewport-level кромочная маска: даже после falloff'а
                        // (scale + alpha) у плитки прямо на кромке остаётся
                        // ненулевая видимость → визуальный "разрез". Этот
                        // DstIn-градиент дотягивает альфу до 0 на самой кромке
                        // viewport, скрывая клиппинг. Гасим маску с той
                        // стороны, где скроллить больше некуда (стоп) — там
                        // нечего скрывать, плитки полноразмерны.
                        .fadingEdges(
                            orientation = RailOrientation.HORIZONTAL,
                            edgeWidth = 56.dp,
                            fadeStart = listState.canScrollBackward,
                            fadeEnd = listState.canScrollForward,
                        ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                itemsIndexed(state.recentFiles, key = { _, file -> file.id }) { index, file ->
                    RecentFileCard(
                        model = file,
                        onClick = { onIntent(MainScreenIntent.OpenRecentFile(file.id)) },
                        onDragStarted = {
                            onIntent(
                                MainScreenIntent.DragStarted(
                                    fileId = file.id,
                                    fileUri = file.uri,
                                    displayName = file.displayName,
                                ),
                            )
                        },
                        onDragCancelled = { onIntent(MainScreenIntent.DragCancelled) },
                        isBeingDragged = (state.dragState as? DragState.Active)?.fileId == file.id,
                        folders = state.folders,
                        onAddToFolder = { folderId ->
                            onIntent(MainScreenIntent.AddFileToFolder(folderId, file.uri))
                        },
                        addableLibraries = state.libraries.orEmpty().filter { it.canAdd },
                        onAddToLibrary = { libraryId -> onIntent(MainScreenIntent.AddToLibrary(libraryId, file.uri)) },
                        onDelete = { onIntent(MainScreenIntent.DeleteRecentFile(file.id)) },
                        modifier =
                            Modifier
                                .width(RECENT_CARD_WIDTH)
                                .wheelItem(listState = listState, index = index, minScale = RECENT_CARD_MIN_SCALE),
                    )
                }
            }
        }
        // Desktop chevrons на левом/правом крае карусели; visible только когда
        // canScrollBackward/Forward (на тач-устройствах no-op).
        WheelScrollButtons(
            state = listState,
            tint = MaterialTheme.colorScheme.onSurface,
            background = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        // headlineSmall + Bold + full-opacity onSurface: на пастельном hero-градиенте
        // ничто слабее уже не "пробивает". Шрифт намеренно крупнее карточечного
        // bodyMedium, чтобы заголовок прочно читался как раздел.
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}
