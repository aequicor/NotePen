package ru.kyamshanov.notepen.mainscreen.ui.component

import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMoveRtl
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.kyamshanov.notepen.LiquidGlassCard
import ru.kyamshanov.notepen.LiquidGlassDropdownMenu
import ru.kyamshanov.notepen.mainscreen.platform.isDragAndDropSupported
import ru.kyamshanov.notepen.mainscreen.ui.model.FolderUiModel
import ru.kyamshanov.notepen.mainscreen.ui.model.RecentFileUiModel

/**
 * Карточка недавнего PDF-файла с миниатюрой, именем и статус-бейджем.
 *
 * Поддерживает drag-and-drop: при перетаскивании вызывает [onDragStarted] и [onDragCancelled].
 * Визуально "тускнеет" (alpha = 0.5) когда [isBeingDragged] = true (AC-1, TC-23).
 *
 * @param model UI-модель файла.
 * @param onClick Обработчик нажатия на карточку.
 * @param onDragStarted Обратный вызов при начале перетаскивания.
 * @param onDragCancelled Обратный вызов при завершении или отмене перетаскивания.
 * @param isBeingDragged true, когда карточка активно перетаскивается.
 * @param folders Список доступных папок для пункта «Переместить в папку…» в меню.
 * @param onAddToFolder Колбэк добавления файла в выбранную папку.
 * @param onAddToLibrary Колбэк копирования файла в общую Библиотеку. `null` — пункт скрыт.
 * @param onDelete Колбэк удаления документа (убирает запись из истории; файл на диске остаётся).
 * @param modifier Модификатор компонента.
 */
@Composable
fun RecentFileCard(
    model: RecentFileUiModel,
    onClick: () -> Unit,
    onDragStarted: () -> Unit = {},
    onDragCancelled: () -> Unit = {},
    isBeingDragged: Boolean = false,
    folders: List<FolderUiModel> = emptyList(),
    onAddToFolder: (folderId: String) -> Unit = {},
    onAddToLibrary: (() -> Unit)? = null,
    onDelete: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val dragEndMonitor =
        remember(onDragCancelled) {
            object : DragAndDropTarget {
                override fun onDrop(event: DragAndDropEvent): Boolean = false

                override fun onEnded(event: DragAndDropEvent) {
                    onDragCancelled()
                }
            }
        }

    LiquidGlassCard(
        onClick = onClick,
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    if (isDragAndDropSupported) {
                        Modifier
                            .dragAndDropSource(
                                transferData = { _ ->
                                    onDragStarted()
                                    createFileUriTransferData(model.uri)
                                },
                            )
                            .dragAndDropTarget(
                                shouldStartDragAndDrop = { true },
                                target = dragEndMonitor,
                            )
                    } else {
                        Modifier
                    },
                ),
    ) {
        Column(
            Modifier
                .padding(12.dp)
                .alpha(if (isBeingDragged) 0.5f else 1.0f),
        ) {
            ThumbnailView(
                state = model.thumbnailState,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            // Имя + меню в одной Row: имя берёт weight=1 и переносится
            // на две строки (minLines=2 держит высоту), кнопка стоит
            // справа по центру блока имени.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = model.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                DocumentMenuButton(
                    folders = folders,
                    onAddToFolder = onAddToFolder,
                    onAddToLibrary = onAddToLibrary,
                    onDelete = onDelete,
                )
            }
            StatusBadge(
                status = model.availabilityStatus,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * Кнопка-меню документа: трёхточечный `MoreVert`. Открывает
 * [LiquidGlassDropdownMenu] со списком папок-приёмников, пунктом
 * «Переместить в Библиотеку» (если доступна) и удалением.
 */
@Composable
internal fun DocumentMenuButton(
    folders: List<FolderUiModel>,
    onAddToFolder: (folderId: String) -> Unit,
    onAddToLibrary: (() -> Unit)?,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(
            onClick = { menuExpanded = true },
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Меню документа",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LiquidGlassDropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DocumentMenuContent(
                folders = folders,
                onAddToFolder = { folderId ->
                    menuExpanded = false
                    onAddToFolder(folderId)
                },
                onAddToLibrary =
                    onAddToLibrary?.let { handler ->
                        {
                            menuExpanded = false
                            handler()
                        }
                    },
                onDelete = {
                    menuExpanded = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun DocumentMenuContent(
    folders: List<FolderUiModel>,
    onAddToFolder: (folderId: String) -> Unit,
    onAddToLibrary: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    val hasMoveSection = folders.isNotEmpty() || onAddToLibrary != null
    if (hasMoveSection) {
        MoveSectionHeader()
        onAddToLibrary?.let { MoveToLibraryItem(onClick = it) }
        folders.forEach { folder ->
            MoveToFolderItem(folder) { onAddToFolder(folder.id) }
        }
        HorizontalDivider()
    }
    DeleteDocumentItem(onClick = onDelete)
}

@Composable
private fun MoveSectionHeader() {
    // Неинтерактивный заголовок: подписывает блок «Переместить» с папками
    // и Библиотекой, не сливаясь с самими действиями.
    DropdownMenuItem(
        enabled = false,
        text = {
            Text(
                text = "Переместить в",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        onClick = {},
    )
}

@Composable
private fun MoveToLibraryItem(onClick: () -> Unit) {
    DropdownMenuItem(
        leadingIcon = {
            Icon(imageVector = Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
        },
        text = { Text("Библиотека") },
        onClick = onClick,
    )
}

@Composable
private fun MoveToFolderItem(
    folder: FolderUiModel,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        leadingIcon = {
            Icon(imageVector = Icons.Default.DriveFileMoveRtl, contentDescription = null)
        },
        text = { Text(folder.name) },
        onClick = onClick,
    )
}

@Composable
private fun DeleteDocumentItem(onClick: () -> Unit) {
    DropdownMenuItem(
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        text = { Text(text = "Удалить", color = MaterialTheme.colorScheme.error) },
        onClick = onClick,
    )
}
