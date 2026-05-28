package ru.kyamshanov.notepen.mainscreen.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.LocalLibrary
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.oshai.kotlinlogging.KotlinLogging
import ru.kyamshanov.notepen.LiquidGlassCard
import ru.kyamshanov.notepen.mainscreen.platform.isDragAndDropSupported
import ru.kyamshanov.notepen.mainscreen.ui.model.LibraryShelfUiModel

private val logger = KotlinLogging.logger {}
private val SHELF_HEIGHT = 132.dp
private val CARD_WIDTH = 168.dp

/**
 * Секция «Библиотека» на главном экране: горизонтальная полка книг, лежащих
 * в общей папке (расшаривается подключённым устройствам).
 *
 * Поддерживает drop как внутренних перетаскиваний (карточки из «Недавних» /
 * содержимого папок), так и внешних файлов из ОС. Источник перетаскивания
 * детектится по MIME-типу через тот же набор утилит, что и [FolderCard].
 *
 * @param items Список книг в библиотеке; пустой — показывается приглашение.
 * @param onItemClick Открыть книгу в редакторе (id из [LibraryShelfUiModel]).
 * @param onDropInternalUri Внутреннее перетаскивание дошло до полки — uri известен из drag-данных.
 * @param onDropExternalFiles Внешний drop из Finder/проводника.
 * @param modifier Модификатор компонента.
 */
@Composable
fun LibraryShelf(
    items: List<LibraryShelfUiModel>,
    onItemClick: (id: String) -> Unit,
    onDropInternalUri: (sourceUri: String) -> Unit,
    onDropExternalFiles: (uris: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isHovered by remember { mutableStateOf(false) }
    val dropTarget =
        remember(onDropInternalUri, onDropExternalFiles) {
            object : DragAndDropTarget {
                override fun onDrop(event: DragAndDropEvent): Boolean {
                    isHovered = false
                    if (event.isExternalFileDrop()) {
                        val uris = extractExternalFileUris(event)
                        return if (uris.isNotEmpty()) {
                            onDropExternalFiles(uris)
                            true
                        } else {
                            logger.warn { "LibraryShelf.onDrop: external drop carried no supported files" }
                            false
                        }
                    }
                    val uri = extractFileUri(event)
                    return if (uri != null) {
                        onDropInternalUri(uri)
                        true
                    } else {
                        logger.warn { "LibraryShelf.onDrop: could not extract file URI from event" }
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

    val dropModifier =
        if (isDragAndDropSupported) {
            Modifier.dragAndDropTarget(
                shouldStartDragAndDrop = { event ->
                    shouldAcceptDragEvent(event.dragEventMimeTypes()) || event.isExternalFileDrop()
                },
                target = dropTarget,
            )
        } else {
            Modifier
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .then(dropModifier)
                .border(
                    width = if (isHovered) 2.dp else 0.dp,
                    color =
                        if (isHovered) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Transparent
                        },
                    shape = RoundedCornerShape(12.dp),
                ),
    ) {
        if (items.isEmpty()) {
            EmptyShelfHint()
        } else {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    LibraryShelfCard(
                        model = item,
                        onClick = { onItemClick(item.id) },
                        modifier = Modifier.width(CARD_WIDTH),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyShelfHint() {
    LiquidGlassCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(SHELF_HEIGHT),
        onClick = null,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.LocalLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Перетащите сюда книги — они станут доступны на других устройствах",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LibraryShelfCard(
    model: LibraryShelfUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LiquidGlassCard(
        modifier = modifier.height(SHELF_HEIGHT),
        onClick = onClick,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = model.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
            )
        }
    }
}
