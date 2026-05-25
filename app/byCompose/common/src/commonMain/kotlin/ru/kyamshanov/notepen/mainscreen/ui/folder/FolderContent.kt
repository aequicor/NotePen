package ru.kyamshanov.notepen.mainscreen.ui.folder

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.kyamshanov.notepen.mainscreen.platform.isDragAndDropSupported
import ru.kyamshanov.notepen.mainscreen.ui.component.FolderCard
import ru.kyamshanov.notepen.mainscreen.ui.component.RecentFileCard
import ru.kyamshanov.notepen.mainscreen.ui.component.extractExternalFileUris
import ru.kyamshanov.notepen.mainscreen.ui.component.isExternalFileDrop
import ru.kyamshanov.notepen.mainscreen.ui.dialog.CreateFolderDialog
import ru.kyamshanov.notepen.mainscreen.ui.dialog.DeleteFolderDialog
import ru.kyamshanov.notepen.mainscreen.ui.model.RecentFileUiModel
import ru.kyamshanov.notepen.resolveDocumentDisplayName
import ru.kyamshanov.notepen.titlebar.LocalTitleBarEndInset
import ru.kyamshanov.notepen.titlebar.LocalTitleBarInteraction
import ru.kyamshanov.notepen.titlebar.LocalTitleBarStartInset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderContent(
    component: FolderContentsComponentImpl,
    modifier: Modifier = Modifier,
) {
    val state by component.viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        val msg = state.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        component.viewModel.onErrorShown()
    }

    LaunchedEffect(state.navigateToFilePicker) {
        if (!state.navigateToFilePicker) return@LaunchedEffect
        component.viewModel.onFilePickerLaunched()
        val uri = component.onOpenFilePicker()
        component.viewModel.onFilePicked(
            uri = uri,
            displayName = uri?.let { resolveDocumentDisplayName(it) } ?: "",
        )
    }

    val titleBarInteraction = LocalTitleBarInteraction.current
    val titleBarStartInset = LocalTitleBarStartInset.current
    val titleBarEndInset = LocalTitleBarEndInset.current
    Scaffold(
        topBar = {
            val barModifier = Modifier.fillMaxWidth()
            TopAppBar(
                modifier = titleBarInteraction?.dragArea(barModifier) ?: barModifier,
                title = { Text(state.folderName) },
                windowInsets =
                    WindowInsets(left = titleBarStartInset, right = titleBarEndInset)
                        .union(TopAppBarDefaults.windowInsets),
                navigationIcon = {
                    IconButton(
                        onClick = component.onBack,
                        modifier = titleBarInteraction?.interactive(Modifier) ?: Modifier,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { component.viewModel.requestAddExisting() },
                        modifier = titleBarInteraction?.interactive(Modifier) ?: Modifier,
                    ) {
                        Icon(Icons.Filled.LibraryAdd, contentDescription = "Добавить из недавних")
                    }
                    IconButton(
                        onClick = { component.viewModel.openCreateFolderDialog() },
                        modifier = titleBarInteraction?.interactive(Modifier) ?: Modifier,
                    ) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = "Создать папку")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { component.viewModel.requestImport() }) {
                Icon(Icons.Filled.Add, contentDescription = "Добавить файл")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { padding ->
        var isExternalDropHovered by remember { mutableStateOf(false) }
        val dropTarget =
            remember(component) {
                object : DragAndDropTarget {
                    override fun onDrop(event: DragAndDropEvent): Boolean {
                        isExternalDropHovered = false
                        val uris = extractExternalFileUris(event)
                        return if (uris.isNotEmpty()) {
                            component.viewModel.addExternalFiles(uris)
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

        Box(
            Modifier
                .fillMaxSize()
                .then(
                    if (isDragAndDropSupported) {
                        Modifier.dragAndDropTarget(
                            shouldStartDragAndDrop = { event -> event.isExternalFileDrop() },
                            target = dropTarget,
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
            if (state.subfolders.isEmpty() && state.files.isEmpty()) {
                Text(
                    text = if (state.isLoading) "Загрузка…" else "Папка пуста",
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    color = MaterialTheme.colorScheme.outline,
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize().padding(padding),
                ) {
                    items(
                        state.subfolders,
                        key = { "folder_${it.id}" },
                        span = { GridItemSpan(maxLineSpan) },
                    ) { folder ->
                        FolderCard(
                            model = folder,
                            onClick = { component.viewModel.openSubfolder(folder.id, folder.name) },
                            onDelete = { component.viewModel.requestDeleteSubfolder(folder.id) },
                        )
                    }
                    items(state.files, key = { it.id }) { file ->
                        RecentFileCard(
                            model = file,
                            onClick = { component.viewModel.openFile(file.uri, file.lastPageIndex) },
                        )
                    }
                }
            }
        }
    }

    state.createFolderDialog?.let { dialog ->
        CreateFolderDialog(
            state = dialog,
            onNameChange = { component.viewModel.onCreateFolderNameChanged(it) },
            onConfirm = { component.viewModel.createSubfolder(dialog.currentName) },
            onDismiss = { component.viewModel.dismissCreateFolderDialog() },
        )
    }

    state.deleteFolderDialog?.let { dialog ->
        DeleteFolderDialog(
            folderName = dialog.folderName,
            onConfirm = { component.viewModel.deleteSubfolder(dialog.folderId) },
            onDismiss = { component.viewModel.dismissDeleteFolderDialog() },
        )
    }

    state.addExistingCandidates?.let { candidates ->
        AddExistingFilesDialog(
            candidates = candidates,
            onConfirm = { uris -> component.viewModel.addExistingFiles(uris) },
            onDismiss = { component.viewModel.dismissAddExisting() },
        )
    }
}

/**
 * Диалог выбора недавних файлов (мультивыбор) для добавления в открытую папку.
 *
 * @param candidates Недавние файлы, которых ещё нет в папке.
 * @param onConfirm Вызывается со списком URI выбранных файлов.
 * @param onDismiss Закрытие без добавления.
 */
@Composable
private fun AddExistingFilesDialog(
    candidates: List<RecentFileUiModel>,
    onConfirm: (uris: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val selected = remember(candidates) { mutableStateListOf<String>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить из недавних") },
        text = {
            if (candidates.isEmpty()) {
                Text("Нет недавних файлов для добавления")
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(candidates, key = { it.id }) { file ->
                        val checked = file.uri in selected
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (checked) selected.remove(file.uri) else selected.add(file.uri)
                                    }
                                    .padding(vertical = 4.dp),
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    if (checked) selected.remove(file.uri) else selected.add(file.uri)
                                },
                            )
                            Text(
                                text = file.displayName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selected.toList()) },
                enabled = selected.isNotEmpty(),
            ) {
                Text(if (selected.isEmpty()) "Добавить" else "Добавить (${selected.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}
