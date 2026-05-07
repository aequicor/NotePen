package ru.kyamshanov.notepen.mainscreen.ui.component

import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
            .dragAndDropSource(
                transferData = { _ ->
                    onDragStarted()
                    createFileUriTransferData(model.uri)
                },
            )
            .dragAndDropTarget(
                shouldStartDragAndDrop = { true },
                target = dragEndMonitor,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
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
