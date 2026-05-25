package ru.kyamshanov.notepen.mainscreen.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.kyamshanov.notepen.mainscreen.ui.model.PeerSummaryUiModel

/**
 * Карточка подключённого пира (хост/клиент). Визуально однородна с
 * [FolderCard] — пир ведёт себя как «папка», в которую можно «зайти»,
 * чтобы увидеть его recent-файлы и папки.
 *
 * @param model UI-модель пира.
 * @param onClick Обработчик нажатия — открывает sub-экран каталога пира.
 * @param modifier Модификатор компонента.
 */
@Composable
fun PeerCard(
    model: PeerSummaryUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Devices, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(model.displayName, style = MaterialTheme.typography.bodyLarge)
                val subtitle =
                    if (model.isOnline) {
                        "${model.itemCount} элементов"
                    } else {
                        "не в сети — ${model.itemCount} элементов"
                    }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (model.isOnline) {
                            MaterialTheme.colorScheme.outline
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                )
            }
        }
    }
}
