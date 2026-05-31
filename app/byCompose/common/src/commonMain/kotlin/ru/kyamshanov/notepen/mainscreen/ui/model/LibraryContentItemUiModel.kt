package ru.kyamshanov.notepen.mainscreen.ui.model

/**
 * Элемент содержимого конкретной библиотеки на sub-экране drill-down.
 *
 * В отличие от полки на главном экране здесь НЕТ абсолютного `uri`: книгу материализует и
 * открывает [ru.kyamshanov.notepen.library.api.Library.open] по [id] (per-library locator),
 * поэтому хранить путь незачем — это исключает соблазн передать «пустой» uri в редактор.
 *
 * @property id Per-library locator книги ([ru.kyamshanov.notepen.library.api.LibraryBookId] value).
 * @property displayName Имя книги.
 * @property sizeBytes Размер в байтах, либо `null` если неизвестен.
 * @property modifiedAt Время последней модификации (epoch millis), либо `null`.
 */
data class LibraryContentItemUiModel(
    val id: String,
    val displayName: String,
    val sizeBytes: Long?,
    val modifiedAt: Long?,
)
