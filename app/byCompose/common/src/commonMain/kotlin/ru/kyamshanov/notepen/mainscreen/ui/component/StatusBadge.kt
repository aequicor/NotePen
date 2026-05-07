package ru.kyamshanov.notepen.mainscreen.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.kyamshanov.notepen.mainscreen.domain.model.AvailabilityStatus

/**
 * Бейдж статуса доступности файла.
 *
 * Для [AvailabilityStatus.AVAILABLE] и [AvailabilityStatus.UNKNOWN] бейдж не отображается.
 *
 * @param status Статус доступности файла.
 * @param modifier Модификатор компонента.
 */
@Composable
fun StatusBadge(status: AvailabilityStatus, modifier: Modifier = Modifier) {
    val (label, color) = when (status) {
        AvailabilityStatus.AVAILABLE -> return
        AvailabilityStatus.UNKNOWN -> return
        AvailabilityStatus.NOT_FOUND -> "Не найден" to MaterialTheme.colorScheme.error
        AvailabilityStatus.FILE_ERROR -> "Ошибка" to MaterialTheme.colorScheme.error
        AvailabilityStatus.ARCHIVED_UNAVAILABLE -> "Архив / Недоступен" to MaterialTheme.colorScheme.outline
    }
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.extraSmall,
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
