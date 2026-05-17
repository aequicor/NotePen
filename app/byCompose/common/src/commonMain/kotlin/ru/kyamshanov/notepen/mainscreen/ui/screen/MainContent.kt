package ru.kyamshanov.notepen.mainscreen.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import ru.kyamshanov.notepen.mainscreen.ui.MainScreenIntent
import ru.kyamshanov.notepen.mainscreen.ui.component.EmptyState
import ru.kyamshanov.notepen.mainscreen.ui.component.FolderCard
import ru.kyamshanov.notepen.mainscreen.ui.component.RecentFileCard
import ru.kyamshanov.notepen.mainscreen.ui.component.RemoteEntryCard
import ru.kyamshanov.notepen.mainscreen.ui.component.RemoteFolderCard
import ru.kyamshanov.notepen.mainscreen.ui.dialog.CreateFolderDialog
import ru.kyamshanov.notepen.mainscreen.ui.dialog.DeleteFolderDialog
import ru.kyamshanov.notepen.mainscreen.ui.dialog.SafMergeDialog
import ru.kyamshanov.notepen.mainscreen.ui.model.DragState
import ru.kyamshanov.notepen.mainscreen.ui.model.ErrorEvent
import ru.kyamshanov.notepen.mainscreen.ui.model.MainScreenUiState
import ru.kyamshanov.notepen.mainscreen.ui.model.SuccessEvent
import androidx.compose.ui.platform.LocalWindowInfo

private val WIDE_SCREEN_THRESHOLD: Dp = 600.dp

/**
 * Главный контент экрана: адаптивная вёрстка с TopAppBar, списком файлов и папок,
 * диалогами и Snackbar для ошибок.
 *
 * @param state Текущее состояние экрана.
 * @param onIntent Обработчик интентов.
 * @param modifier Модификатор компонента.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(
    state: MainScreenUiState,
    onIntent: (MainScreenIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val windowWidth = LocalWindowInfo.current.containerSize.width
    val isWide = with(LocalDensity.current) { windowWidth.toDp() >= WIDE_SCREEN_THRESHOLD }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { onIntent(MainScreenIntent.ScreenVisible) }

    val errorMessage = when (state.errorEvent) {
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
        null -> null
    }

    LaunchedEffect(state.errorEvent) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
        }
    }

    LaunchedEffect(state.successEvent) {
        val successEvent = state.successEvent ?: return@LaunchedEffect
        val message = when (successEvent) {
            is SuccessEvent.FileAddedToFolder -> "Файл добавлен в «${successEvent.folderName}»"
            SuccessEvent.FileAlreadyInFolder -> "Файл уже есть в этой папке"
        }
        snackbarHostState.showSnackbar(message)
        onIntent(MainScreenIntent.OnSuccessEventHandled)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NotePen") },
                actions = {
                    if (isWide) {
                        TextButton(onClick = { onIntent(MainScreenIntent.OpenFilePicker) }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Открыть")
                        }
                    }
                    IconButton(onClick = { onIntent(MainScreenIntent.OpenCreateFolderDialog) }) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "Новая папка")
                    }
                },
            )
        },
        floatingActionButton = {
            if (!isWide) {
                FloatingActionButton(onClick = { onIntent(MainScreenIntent.OpenFilePicker) }) {
                    Icon(Icons.Default.Add, contentDescription = "Открыть файл")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.recentFiles.isEmpty() && state.folders.isEmpty() && state.remoteSection == null ->
                    EmptyState(
                        onOpenFile = { onIntent(MainScreenIntent.OpenFilePicker) },
                        modifier = Modifier.fillMaxSize(),
                    )
                else -> RecentFilesAndFoldersList(state, onIntent, isWide)
            }
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
) {
    if (isWide) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 280.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (state.recentFiles.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Недавние файлы") }
                items(state.recentFiles, key = { it.id }) { file ->
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
                    )
                }
            }
            state.remoteSection?.let { remote ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader("Remote (${remote.hostName})")
                }
                items(remote.entries, key = { "remote_${it.documentId}" }) { entry ->
                    RemoteEntryCard(
                        model = entry,
                        onClick = {
                            onIntent(
                                MainScreenIntent.OpenRemoteEntry(
                                    documentId = entry.documentId,
                                    displayName = entry.displayName,
                                ),
                            )
                        },
                    )
                }
                items(remote.folders, key = { "remote_folder_${it.folderId}" }) { folder ->
                    RemoteFolderCard(model = folder)
                }
            }
            if (state.folders.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Папки") }
                items(state.folders, key = { "folder_${it.id}" }) { folder ->
                    FolderCard(
                        model = folder,
                        onClick = { /* открытие папки — будущая фича */ },
                        onDelete = { onIntent(MainScreenIntent.RequestDeleteFolder(folder.id)) },
                        onDropFile = { onIntent(MainScreenIntent.DropOnFolder(folderId = folder.id)) },
                    )
                }
            }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            if (state.recentFiles.isNotEmpty()) {
                item { SectionHeader("Недавние файлы") }
                items(state.recentFiles, key = { it.id }) { file ->
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    )
                }
            }
            state.remoteSection?.let { remote ->
                item { Spacer(Modifier.height(24.dp)) }
                item { SectionHeader("Remote (${remote.hostName})") }
                items(remote.entries, key = { "remote_${it.documentId}" }) { entry ->
                    RemoteEntryCard(
                        model = entry,
                        onClick = {
                            onIntent(
                                MainScreenIntent.OpenRemoteEntry(
                                    documentId = entry.documentId,
                                    displayName = entry.displayName,
                                ),
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    )
                }
                items(remote.folders, key = { "remote_folder_${it.folderId}" }) { folder ->
                    RemoteFolderCard(
                        model = folder,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    )
                }
            }
            if (state.folders.isNotEmpty()) {
                item { Spacer(Modifier.height(24.dp)) }
                item { SectionHeader("Папки") }
                items(state.folders, key = { "folder_${it.id}" }) { folder ->
                    FolderCard(
                        model = folder,
                        onClick = { /* открытие папки — будущая фича */ },
                        onDelete = { onIntent(MainScreenIntent.RequestDeleteFolder(folder.id)) },
                        onDropFile = { onIntent(MainScreenIntent.DropOnFolder(folderId = folder.id)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}
