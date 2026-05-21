package ru.kyamshanov.notepen.mainscreen.ui.component

import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import ru.kyamshanov.notepen.mainscreen.platform.isDragAndDropSupported
import ru.kyamshanov.notepen.mainscreen.ui.model.FolderUiModel
import ru.kyamshanov.notepen.mainscreen.ui.model.RecentFileUiModel

/**
 * Карточка недавнего PDF-файла с миниатюрой, именем и статус-бейджем.
 *
 * Поддерживает drag-and-drop: при перетаскивании вызывает [onDragStarted] и [onDragCancelled].
 * Визуально "тускнеет" (alpha = 0.5) когда [isBeingDragged] = true (AC-1, TC-23).
 *
 * Завершение или отмена drag-сессии обнаруживается через мониторинговый [DragAndDropTarget],
 * у которого `onEnded` вызывает [onDragCancelled] (HIGH #1, AC-3, AC-4).
 *
 * @param model UI-модель файла.
 * @param onClick Обработчик нажатия на карточку.
 * @param onDragStarted Обратный вызов, вызываемый при начале перетаскивания.
 * @param onDragCancelled Обратный вызов, вызываемый при завершении или отмене перетаскивания.
 * @param isBeingDragged true, когда карточка активно перетаскивается.
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
    modifier: Modifier = Modifier,
) {
    // Monitoring target: does not accept drops (onDrop returns false), but participates in
    // the drag session to detect when it ends via onEnded → calls onDragCancelled (AC-3, AC-4).
    val dragEndMonitor = remember(onDragCancelled) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean = false

            override fun onEnded(event: DragAndDropEvent) {
                onDragCancelled()
            }
        }
    }

    Card(
        onClick = onClick,
        modifier = modifier
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
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            Modifier
                .padding(12.dp)
                .alpha(if (isBeingDragged) 0.5f else 1.0f),
        ) {
            Box(Modifier.fillMaxWidth()) {
                ThumbnailView(
                    state = model.thumbnailState,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (folders.isNotEmpty()) {
                    var menuExpanded by remember { mutableStateOf(false) }
                    Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.CreateNewFolder,
                                contentDescription = "Добавить в папку",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            folders.forEach { folder ->
                                DropdownMenuItem(
                                    text = { Text(folder.name) },
                                    onClick = {
                                        menuExpanded = false
                                        onAddToFolder(folder.id)
                                    },
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = model.displayName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            StatusBadge(
                status = model.availabilityStatus,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
