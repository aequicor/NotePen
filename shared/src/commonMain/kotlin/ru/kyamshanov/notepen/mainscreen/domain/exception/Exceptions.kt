package ru.kyamshanov.notepen.mainscreen.domain.exception

/** Ошибка записи истории файлов в хранилище. */
class HistoryFlushException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Превышен лимит папок (100). */
class FolderLimitExceededException : Exception("Folder limit (100) exceeded")

/** Имя папки пустое или состоит только из пробельных символов. */
class FolderNameInvalidException(name: String) : Exception("Folder name invalid: '$name'")

/** Длина имени папки превышает 255 символов. */
class FolderNameTooLongException(length: Int) : Exception("Folder name too long: $length chars")

/** Имя папки содержит символы вне whitelist: Unicode letters + digits + '-' + '_'. */
class FolderNameCharsInvalidException(name: String) : Exception("Folder name contains invalid chars: '$name'")

/** Обращение к несуществующей папке. */
class FolderNotFoundException(id: String) : Exception("Folder not found: $id")

/** addFile вызван с URI, отсутствующим в FileHistoryRepository. */
class FileNotInHistoryException(uri: String) : Exception("File not in history: $uri")

/** Файл уже добавлен в данную папку. */
class FileDuplicateInFolderException(folderId: String, uri: String) :
    Exception("File $uri already in folder $folderId")

/** PDF повреждён, зашифрован, нулевой размер, OOM при рендеринге. */
class ThumbnailGenerationException(message: String, cause: Throwable? = null) : Exception(message, cause)
