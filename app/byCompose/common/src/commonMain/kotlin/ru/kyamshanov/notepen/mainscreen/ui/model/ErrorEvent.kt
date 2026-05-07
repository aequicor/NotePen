package ru.kyamshanov.notepen.mainscreen.ui.model

/** Одноразовое событие ошибки, передаваемое из ViewModel в UI. */
sealed class ErrorEvent {

    /** Файл не найден на диске (удалён или перемещён). */
    object FileNotFound : ErrorEvent()

    /** Файл найден, но недоступен (повреждён, нет прав и т.п.). */
    object FileError : ErrorEvent()

    /** Ошибка записи истории в хранилище. */
    object HistoryFlushFailed : ErrorEvent()

    /** Ошибка генерации миниатюры PDF. */
    object ThumbnailGenerationFailed : ErrorEvent()

    /** Превышен лимит папок (100). */
    object FolderLimitExceeded : ErrorEvent()

    /** Имя папки содержит недопустимые символы. */
    object FolderNameCharsInvalid : ErrorEvent()

    /** Общая ошибка операции с папкой. */
    object FolderOperationFailed : ErrorEvent()
}
