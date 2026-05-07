package ru.kyamshanov.notepen.mainscreen.ui.model

import ru.kyamshanov.notepen.mainscreen.domain.model.AvailabilityStatus

/**
 * UI-модель записи истории файлов для отображения в главном экране.
 *
 * @property id Уникальный идентификатор записи.
 * @property displayName Отображаемое имя файла.
 * @property openedAt Момент последнего открытия (epochMillis).
 * @property availabilityStatus Текущий статус доступности файла.
 * @property thumbnailState Состояние загрузки миниатюры.
 * @property lastPageIndex Индекс последней просмотренной страницы (0-based).
 */
data class RecentFileUiModel(
    val id: String,
    val displayName: String,
    val openedAt: Long,
    val availabilityStatus: AvailabilityStatus,
    val thumbnailState: ThumbnailState,
    val lastPageIndex: Int,
)
