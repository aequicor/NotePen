package ru.kyamshanov.notepen.mainscreen.ui.model

/**
 * UI-модель папки для отображения в главном экране.
 *
 * @property id Уникальный идентификатор папки.
 * @property name Отображаемое имя папки.
 * @property fileCount Количество файлов в папке.
 * @property createdAt Момент создания папки (epochMillis).
 * @property lastFileOpenedAt Момент последнего открытия файла из папки (epochMillis), null если файлов нет.
 */
data class FolderUiModel(
    val id: String,
    val name: String,
    val fileCount: Int,
    val createdAt: Long,
    val lastFileOpenedAt: Long?,
)
