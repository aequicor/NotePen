package ru.kyamshanov.notepen.mainscreen.ui

/** Интенты (действия пользователя) для главного экрана. */
sealed class MainScreenIntent {

    /** Экран стал видимым — инициировать загрузку данных. */
    object OpenFilePicker : MainScreenIntent()

    /** Открыть системный файловый менеджер для выбора PDF. */
    object ScreenVisible : MainScreenIntent()

    /**
     * Открыть недавний файл из истории.
     *
     * @property id Идентификатор записи в истории.
     */
    data class OpenRecentFile(val id: String) : MainScreenIntent()

    /** Отменить текущую навигацию (пользователь вернулся назад). */
    object CancelNavigation : MainScreenIntent()

    /**
     * Подтвердить слияние SAF-записей: сохранить существующую запись под новым URI.
     *
     * @property keepId UUID существующей записи, которую сохраняем.
     * @property discardId UUID старой записи, которую удаляем.
     * @property newUri Новый нормализованный URI.
     */
    data class MergeSafRecords(
        val keepId: String,
        val discardId: String,
        val newUri: String,
    ) : MainScreenIntent()

    /**
     * Отклонить слияние SAF-записей: добавить новый URI как отдельную запись.
     *
     * @property existingId UUID существующей записи (будет помечена FILE_ERROR).
     * @property newUri Новый нормализованный URI для отдельной записи.
     */
    data class RejectSafMerge(val existingId: String, val newUri: String) : MainScreenIntent()

    /**
     * Создать новую папку.
     *
     * @property name Имя папки (уже отфильтрованное в UI).
     */
    data class CreateFolder(val name: String) : MainScreenIntent()

    /**
     * Удалить папку.
     *
     * @property id UUID папки.
     */
    data class DeleteFolder(val id: String) : MainScreenIntent()

    /**
     * Запросить подтверждение удаления папки (показать диалог).
     *
     * @property id UUID папки.
     */
    data class RequestDeleteFolder(val id: String) : MainScreenIntent()

    /**
     * Добавить файл в папку.
     *
     * @property folderId UUID папки.
     * @property fileUri Нормализованный URI файла.
     */
    data class AddFileToFolder(val folderId: String, val fileUri: String) : MainScreenIntent()

    /**
     * Удалить файл из папки.
     *
     * @property folderId UUID папки.
     * @property uri Нормализованный URI файла.
     */
    data class RemoveFileFromFolder(val folderId: String, val uri: String) : MainScreenIntent()

    /**
     * Переименовать папку.
     *
     * @property id UUID папки.
     * @property newName Новое имя.
     */
    data class RenameFolder(val id: String, val newName: String) : MainScreenIntent()

    /** Открыть диалог создания новой папки. */
    object OpenCreateFolderDialog : MainScreenIntent()

    /** Закрыть диалог создания папки без сохранения. */
    object DismissCreateFolderDialog : MainScreenIntent()

    /** Закрыть диалог удаления папки без подтверждения. */
    object DismissDeleteFolderDialog : MainScreenIntent()

    /**
     * Пользователь изменил текст в поле имени папки.
     *
     * @property name Введённый текст (до фильтрации).
     */
    data class FolderDialogNameChanged(val name: String) : MainScreenIntent()

    /**
     * Результат выбора файла в системном файловом менеджере (CC-2).
     *
     * Вызывается UI после того, как пользователь выбрал файл через [OpenFilePicker].
     * Если пользователь закрыл менеджер без выбора — [uri] равен null.
     *
     * @property uri URI выбранного файла или null.
     * @property displayName Отображаемое имя файла.
     * @property fileSize Размер файла в байтах (null если неизвестен).
     */
    data class FilePickerResult(
        val uri: String?,
        val displayName: String,
        val fileSize: Long?,
    ) : MainScreenIntent()
}
