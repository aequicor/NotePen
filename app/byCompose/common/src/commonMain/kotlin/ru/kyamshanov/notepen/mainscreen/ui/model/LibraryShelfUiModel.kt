package ru.kyamshanov.notepen.mainscreen.ui.model

/**
 * Карточка одной книги в секции «Библиотека» на главном экране.
 *
 * @property id Стабильный идентификатор (относительный путь в библиотеке).
 * @property uri Абсолютный URI для открытия в редакторе.
 * @property displayName Имя файла.
 * @property sizeBytes Размер в байтах; `null` если неизвестен.
 * @property modifiedAt Время последней модификации (epoch millis).
 */
data class LibraryShelfUiModel(
    val id: String,
    val uri: String,
    val displayName: String,
    val sizeBytes: Long?,
    val modifiedAt: Long,
)
