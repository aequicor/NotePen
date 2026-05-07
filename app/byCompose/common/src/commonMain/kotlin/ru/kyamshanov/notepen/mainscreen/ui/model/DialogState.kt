package ru.kyamshanov.notepen.mainscreen.ui.model

/**
 * Состояние диалога подтверждения слияния SAF-записей.
 *
 * @property existingRecord Существующая запись в истории.
 * @property newUri Новый URI, пришедший от SAF.
 */
data class SafMergeDialogState(
    val existingRecord: RecentFileUiModel,
    val newUri: String,
)

/**
 * Состояние диалога создания папки.
 *
 * @property currentName Текущее введённое имя (уже отфильтрованное).
 * @property isConfirmEnabled true, если кнопку «Создать» можно нажать.
 */
data class CreateFolderDialogState(
    val currentName: String,
    val isConfirmEnabled: Boolean,
)

/**
 * Состояние диалога подтверждения удаления папки.
 *
 * @property folderId UUID папки для удаления.
 * @property folderName Имя папки для отображения в диалоге.
 */
data class DeleteFolderDialogState(
    val folderId: String,
    val folderName: String,
)
