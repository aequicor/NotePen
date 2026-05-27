package ru.kyamshanov.notepen.mainscreen.ui.model

/** Одноразовое событие успеха, передаваемое из ViewModel в UI. */
sealed class SuccessEvent {
    /**
     * Файл успешно добавлен в папку.
     *
     * @property folderName Отображаемое имя папки, в которую добавлен файл (для Snackbar AC-2).
     */
    data class FileAddedToFolder(
        val folderName: String,
    ) : SuccessEvent()

    /** Файл уже находится в данной папке (дубликат — не является ошибкой, AC-5). */
    object FileAlreadyInFolder : SuccessEvent()
}
