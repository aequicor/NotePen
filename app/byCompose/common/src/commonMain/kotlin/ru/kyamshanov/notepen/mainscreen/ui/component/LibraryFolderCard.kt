package ru.kyamshanov.notepen.mainscreen.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Icon
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
import ru.kyamshanov.notepen.LiquidGlassCard
import ru.kyamshanov.notepen.mainscreen.platform.isDragAndDropSupported
import ru.kyamshanov.notepen.mainscreen.ui.model.LibraryCardUiModel

private val logger = KotlinLogging.logger {}

// Material 3 IconButton default footprint — должно совпадать с
// трейлинг-IconButton'ом в [FolderCard], иначе плитки в одном гриде
// получаются разной высоты.
private val MENU_BUTTON_SLOT = 48.dp

/**
 * Карточка одной подключённой библиотеки в секции «Папки» на главном экране: имя библиотеки,
 * число книг и (для роли Читатель) пометка «Только чтение».
 *
 * Drop принимается ТОЛЬКО если [LibraryCardUiModel.canAdd] (роль Библиотекарь): у Reader-карточки
 * цель перетаскивания не навешивается вовсе. Кнопки-меню (удаление) нет — библиотеки
 * добавляются/отключаются на экране «Источники библиотек».
 *
 * @param model UI-модель библиотеки (имя, тип, число книг, право на добавление).
 * @param onClick Открыть sub-экран содержимого этой библиотеки.
 * @param onDropInternalUri Внутренний drop карточки/файла из истории — добавить в библиотеку.
 * @param onDropExternalFiles Внешний drop из ОС — добавить файлы в библиотеку.
 */
@Composable
fun LibraryFolderCard(
    model: LibraryCardUiModel,
    onClick: () -> Unit,
    onDropInternalUri: (sourceUri: String) -> Unit = {},
    onDropExternalFiles: (uris: List<String>) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var isHovered by remember { mutableStateOf(false) }
    val dropTarget =
        remember(onDropInternalUri, onDropExternalFiles) {
            libraryDropTarget(
                onHoverChange = { isHovered = it },
                onDropInternalUri = onDropInternalUri,
                onDropExternalFiles = onDropExternalFiles,
            )
        }
    // Reader-карточка не принимает drop: цель навешиваем только при canAdd.
    val acceptsDrop = isDragAndDropSupported && model.canAdd
    LiquidGlassCard(
        onClick = onClick,
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    if (acceptsDrop) {
                        Modifier.dragAndDropTarget(
                            shouldStartDragAndDrop = { event ->
                                shouldAcceptDragEvent(event.dragEventMimeTypes()) ||
                                    event.isExternalFileDrop()
                            },
                            target = dropTarget,
                        )
                    } else {
                        Modifier
                    },
                ),
    ) {
        LibraryFolderCardContent(model = model, isHovered = isHovered)
    }
}

@Composable
private fun LibraryFolderCardContent(
    model: LibraryCardUiModel,
    isHovered: Boolean,
) {
    Row(
        Modifier
            .padding(12.dp)
            .background(
                color =
                    if (isHovered) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                shape = RoundedCornerShape(4.dp),
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LibraryBadge()
        Spacer(Modifier.width(12.dp))
        // weight(1f) вместо fillMaxWidth — иначе Column клеймит всю ширину Row'а
        // и съедает место под трейлинг-спейсер ниже.
        Column(Modifier.weight(1f)) {
            Text(model.displayName, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "${model.bookCount} книг",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            if (!model.canAdd) {
                Text(
                    text = "Только чтение",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        // Высоту строки задаёт у FolderCard его трейлинг-IconButton (48dp). У библиотеки
        // кнопки-меню нет — пустой блок того же размера выравнивает высоту карточек в гриде.
        Spacer(Modifier.size(MENU_BUTTON_SLOT))
    }
}

@Composable
private fun LibraryBadge() {
    // Акцентный бэйдж с иконкой книги отделяет библиотеку от обычных папок,
    // у которых на этом месте стандартная серая Folder-иконка.
    Box(
        modifier =
            Modifier
                .size(28.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(6.dp),
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.MenuBook,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(18.dp),
        )
    }
}

private fun libraryDropTarget(
    onHoverChange: (Boolean) -> Unit,
    onDropInternalUri: (sourceUri: String) -> Unit,
    onDropExternalFiles: (uris: List<String>) -> Unit,
): DragAndDropTarget =
    object : DragAndDropTarget {
        override fun onDrop(event: DragAndDropEvent): Boolean {
            onHoverChange(false)
            if (event.isExternalFileDrop()) {
                val uris = extractExternalFileUris(event)
                return if (uris.isNotEmpty()) {
                    onDropExternalFiles(uris)
                    true
                } else {
                    logger.warn { "LibraryFolderCard.onDrop: external drop carried no supported files" }
                    false
                }
            }
            val uri = extractFileUri(event)
            return if (uri != null) {
                onDropInternalUri(uri)
                true
            } else {
                logger.warn { "LibraryFolderCard.onDrop: could not extract file URI from event" }
                false
            }
        }

        override fun onEntered(event: DragAndDropEvent) {
            onHoverChange(true)
        }

        override fun onExited(event: DragAndDropEvent) {
            onHoverChange(false)
        }

        override fun onEnded(event: DragAndDropEvent) {
            onHoverChange(false)
        }
    }
