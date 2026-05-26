package ru.kyamshanov.notepen.mainscreen.ui

/** Интенты (действия пользователя) для главного экрана. */
sealed class MainScreenIntent {
    /** Открыть системный файловый менеджер для выбора PDF. */
    object OpenFilePicker : MainScreenIntent()

    /** Экран стал видимым — инициировать загрузку данных. */
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

    /**
     * Пользователь начал перетаскивать файл.
     *
     * @property fileId Идентификатор записи в истории.
     * @property fileUri Нормализованный URI файла.
     * @property displayName Отображаемое имя файла.
     */
    data class DragStarted(
        val fileId: String,
        val fileUri: String,
        val displayName: String,
    ) : MainScreenIntent()

    /** Пользователь отменил перетаскивание файла (опустил вне папки или нажал Escape). */
    object DragCancelled : MainScreenIntent()

    /**
     * Пользователь бросил файл на папку.
     *
     * @property folderId UUID папки, на которую был брошен файл.
     */
    data class DropOnFolder(val folderId: String) : MainScreenIntent()

    /**
     * Внешний(ие) файл(ы) из ОС (Finder/проводник) брошены на библиотеку (главный экран) —
     * открыть первый в редакторе, остальные добавить в недавние.
     *
     * @property uris Канонические пути брошенных файлов.
     */
    data class ExternalFilesDroppedOnLibrary(val uris: List<String>) : MainScreenIntent()

    /**
     * Внешний(ие) файл(ы) из ОС (Finder/проводник) брошены на карточку папки — добавить в папку.
     *
     * @property folderId UUID папки, на которую были брошены файлы.
     * @property uris Канонические пути брошенных файлов.
     */
    data class ExternalFilesDroppedOnFolder(
        val folderId: String,
        val uris: List<String>,
    ) : MainScreenIntent()

    /** UI подтвердил обработку события успеха — сбросить [ru.kyamshanov.notepen.mainscreen.ui.model.SuccessEvent]. */
    object OnSuccessEventHandled : MainScreenIntent()

    /**
     * Пользователь тапнул по плитке пира в секции «Подключённые устройства».
     *
     * @property peerId Стабильный id подключённого хоста/клиента.
     * @property displayName Имя пира для заголовка sub-экрана.
     */
    data class OpenPeer(val peerId: String, val displayName: String) : MainScreenIntent()

    /**
     * Пользователь тапнул по карточке папки — открыть sub-экран содержимого.
     *
     * @property folderId UUID папки.
     * @property folderName Имя папки для заголовка sub-экрана.
     */
    data class OpenFolder(val folderId: String, val folderName: String) : MainScreenIntent()

    /**
     * Восстановить сессию, выбранную в меню «Сессии» на библиотеке: открыть
     * редактор на «первичном» документе сессии. Сама сессия к этому моменту уже
     * сохранена как pending-restore в хранилище — редактор подхватит её при
     * монтировании и развернёт полный рабочий стол.
     *
     * @property seedUri URI документа, на котором открыть редактор
     *   (см. `SessionData.seedFilePath()`).
     */
    data class RestoreSession(val seedUri: String) : MainScreenIntent()
}
