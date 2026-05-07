package ru.kyamshanov.notepen.mainscreen.ui.model

/**
 * Полное состояние главного экрана.
 *
 * @property recentFiles Список недавних файлов в порядке openedAt DESC.
 * @property folders Список папок пользователя.
 * @property isLoading true во время первичной загрузки данных.
 * @property navigationTarget Активная цель навигации; null означает «остаться на экране».
 * @property safMergeDialog Состояние диалога слияния SAF-записей; null — диалог скрыт.
 * @property createFolderDialog Состояние диалога создания папки; null — диалог скрыт.
 * @property deleteFolderDialog Состояние диалога удаления папки; null — диалог скрыт.
 * @property errorEvent Одноразовое событие ошибки; null — нет активной ошибки.
 * @property dragState Текущее состояние операции перетаскивания файла.
 * @property successEvent Одноразовое событие успеха; null — нет активного события.
 */
data class MainScreenUiState(
    val recentFiles: List<RecentFileUiModel> = emptyList(),
    val folders: List<FolderUiModel> = emptyList(),
    val isLoading: Boolean = true,
    val navigationTarget: NavigationTarget? = null,
    val safMergeDialog: SafMergeDialogState? = null,
    val createFolderDialog: CreateFolderDialogState? = null,
    val deleteFolderDialog: DeleteFolderDialogState? = null,
    val errorEvent: ErrorEvent? = null,
    val dragState: DragState = DragState.None,
    val successEvent: SuccessEvent? = null,
)
