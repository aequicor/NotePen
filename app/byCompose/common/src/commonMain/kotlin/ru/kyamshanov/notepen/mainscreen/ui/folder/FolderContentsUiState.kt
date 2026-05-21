package ru.kyamshanov.notepen.mainscreen.ui.folder

import ru.kyamshanov.notepen.mainscreen.ui.model.CreateFolderDialogState
import ru.kyamshanov.notepen.mainscreen.ui.model.DeleteFolderDialogState
import ru.kyamshanov.notepen.mainscreen.ui.model.FolderUiModel
import ru.kyamshanov.notepen.mainscreen.ui.model.RecentFileUiModel

/**
 * Состояние sub-экрана содержимого папки.
 *
 * @property folderName Имя папки (заголовок).
 * @property subfolders Вложенные папки текущей папки.
 * @property files Файлы, привязанные к папке.
 * @property isLoading Идёт первичная загрузка списка.
 * @property navigateToFilePicker Запрос на открытие системного диалога выбора файла.
 * @property createFolderDialog Состояние диалога создания вложенной папки, либо null.
 * @property deleteFolderDialog Состояние диалога удаления вложенной папки, либо null.
 * @property addExistingCandidates Недавние файлы, которых ещё нет в папке, для диалога
 *           добавления из недавних; `null` — диалог закрыт.
 * @property errorMessage Одноразовое сообщение об ошибке для показа в snackbar.
 */
data class FolderContentsUiState(
    val folderName: String,
    val subfolders: List<FolderUiModel> = emptyList(),
    val files: List<RecentFileUiModel> = emptyList(),
    val isLoading: Boolean = false,
    val navigateToFilePicker: Boolean = false,
    val createFolderDialog: CreateFolderDialogState? = null,
    val deleteFolderDialog: DeleteFolderDialogState? = null,
    val addExistingCandidates: List<RecentFileUiModel>? = null,
    val errorMessage: String? = null,
)
