package ru.kyamshanov.notepen.mainscreen.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.oshai.kotlinlogging.KotlinLogging
import ru.kyamshanov.notepen.mainscreen.platform.isDragAndDropSupported
import ru.kyamshanov.notepen.mainscreen.ui.model.FolderUiModel

private val logger = KotlinLogging.logger {}

/**
 * Predicate that filters drag events to only accept internal application drags.
 *
 * Returns true only when the drag event carries a `text/plain` MIME type, which is the
 * transfer type used by [RecentFileCard] for internal file URI transfers. External OS-level
 * drags (e.g. files from the file manager) will have different MIME types and are silently
 * rejected (EC-11, MEDIUM #2).
 *
 * @param mimeTypes The set of MIME types advertised by the drag event.
 */
internal fun shouldAcceptDragEvent(mimeTypes: Set<String>): Boolean = mimeTypes.contains("text/plain")

/**
 * Карточка папки с иконкой, именем, количеством файлов и кнопкой меню.
 *
 * Поддерживает drag-and-drop цель: при сбрасывании файла вызывает [onDropFile] с URI файла.
 * Визуальная подсветка (фон primaryContainer) управляется локальным hover-состоянием,
 * которое выставляется через `onEntered`/`onExited` callbacks drag-and-drop таргета (AC-6, TC-23).
 * Только та папка, над которой удерживается файл, получает подсветку — не все папки сразу.
 *
 * @param model UI-модель папки.
 * @param onClick Обработчик нажатия на карточку.
 * @param onDelete Обработчик нажатия кнопки меню (удаление).
 * @param onDropFile Обратный вызов при сбрасывании внутреннего файла; принимает URI файла.
 * @param onDropExternalFiles Обратный вызов при сбрасывании внешних файлов из ОС
 *        (Finder/проводник); принимает список путей.
 * @param modifier Модификатор компонента.
 */
@Composable
fun FolderCard(
    model: FolderUiModel,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onDropFile: (fileUri: String) -> Unit = {},
    onDropExternalFiles: (fileUris: List<String>) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var isHovered by remember { mutableStateOf(false) }

    val dropTarget =
        remember(onDropFile, onDropExternalFiles) {
            object : DragAndDropTarget {
                override fun onDrop(event: DragAndDropEvent): Boolean {
                    if (event.isExternalFileDrop()) {
                        val uris = extractExternalFileUris(event)
                        return if (uris.isNotEmpty()) {
                            onDropExternalFiles(uris)
                            true
                        } else {
                            logger.warn { "FolderCard.onDrop: external drop carried no supported files" }
                            false
                        }
                    }
                    val uri = extractFileUri(event)
                    return if (uri != null) {
                        onDropFile(uri)
                        true
                    } else {
                        logger.warn { "FolderCard.onDrop: could not extract file URI from event" }
                        false
                    }
                }

                override fun onEntered(event: DragAndDropEvent) {
                    isHovered = true
                }

                override fun onExited(event: DragAndDropEvent) {
                    isHovered = false
                }

                override fun onEnded(event: DragAndDropEvent) {
                    isHovered = false
                }
            }
        }

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    if (isDragAndDropSupported) {
                        Modifier.dragAndDropTarget(
                            shouldStartDragAndDrop = { event ->
                                shouldAcceptDragEvent(event.dragEventMimeTypes()) || event.isExternalFileDrop()
                            },
                            target = dropTarget,
                        )
                    } else {
                        Modifier
                    },
                ),
        onClick = onClick,
    ) {
        Row(
            Modifier
                .padding(12.dp)
                .background(
                    color =
                        if (isHovered) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.Transparent
                        },
                    shape = RoundedCornerShape(4.dp),
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Folder, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(model.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "${model.fileCount} файлов",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.MoreVert, contentDescription = "Меню папки")
            }
        }
    }
}
