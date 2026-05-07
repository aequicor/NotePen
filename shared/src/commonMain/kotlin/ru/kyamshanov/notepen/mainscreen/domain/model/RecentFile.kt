package ru.kyamshanov.notepen.mainscreen.domain.model

/**
 * Доменная сущность, представляющая одну запись в истории недавних файлов.
 *
 * @property id Уникальный идентификатор записи (UUID v4).
 * @property uri Первичный ключ доступа к файлу (нормализованный URI).
 * @property displayName Отображаемое имя файла.
 * @property fileSize Размер файла в байтах (вторичный атрибут для fuzzy-match на Android).
 * @property openedAt Момент инициирования открытия файла пользователем (epochMillis).
 * @property availabilityStatus Статус доступности файла.
 * @property thumbnailKey Ключ для поиска в ThumbnailCache (null = миниатюра ещё не запрошена).
 * @property fileMtime Время модификации файла на момент последней генерации миниатюры (epochMillis).
 * @property lastPageIndex Индекс страницы (0-based), на которой пользователь находился при последнем открытии.
 */
data class RecentFile(
    val id: String,
    val uri: String,
    val displayName: String,
    val fileSize: Long? = null,
    val openedAt: Long,
    val availabilityStatus: AvailabilityStatus = AvailabilityStatus.UNKNOWN,
    val thumbnailKey: String? = null,
    val fileMtime: Long? = null,
    val lastPageIndex: Int = 0,
)
