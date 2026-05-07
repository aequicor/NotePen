package ru.kyamshanov.notepen.mainscreen.ui.component

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.kyamshanov.notepen.mainscreen.ui.model.RecentFileUiModel

/**
 * Карточка недавнего PDF-файла с миниатюрой, именем и статус-бейджем.
 *
 * @param model UI-модель файла.
 * @param onClick Обработчик нажатия на карточку.
 * @param modifier Модификатор компонента.
 */
@Composable
fun RecentFileCard(
    model: RecentFileUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
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
