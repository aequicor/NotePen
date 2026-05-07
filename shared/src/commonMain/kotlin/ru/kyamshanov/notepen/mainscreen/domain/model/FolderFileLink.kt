package ru.kyamshanov.notepen.mainscreen.domain.model

/**
 * Связующая запись (join entity) реализующая отношение многие-ко-многим между [Folder] и [RecentFile].
 *
 * @property folderId UUID папки (FK → Folder.id).
 * @property fileUri Нормализованный URI файла (FK → RecentFile.uri).
 * @property lastOpenedAt Момент последнего открытия файла из данной папки (epochMillis).
 */
data class FolderFileLink(
    val folderId: String,
    val fileUri: String,
    val lastOpenedAt: Long,
)
