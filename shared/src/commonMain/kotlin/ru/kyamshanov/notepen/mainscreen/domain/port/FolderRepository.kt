package ru.kyamshanov.notepen.mainscreen.domain.port

import ru.kyamshanov.notepen.mainscreen.domain.exception.FileDuplicateInFolderException
import ru.kyamshanov.notepen.mainscreen.domain.exception.FileNotInHistoryException
import ru.kyamshanov.notepen.mainscreen.domain.exception.FolderLimitExceededException
import ru.kyamshanov.notepen.mainscreen.domain.exception.FolderNameCharsInvalidException
import ru.kyamshanov.notepen.mainscreen.domain.exception.FolderNameInvalidException
import ru.kyamshanov.notepen.mainscreen.domain.exception.FolderNameTooLongException
import ru.kyamshanov.notepen.mainscreen.domain.exception.FolderNotFoundException
import ru.kyamshanov.notepen.mainscreen.domain.model.Folder

/**
 * Порт для персистирования и чтения папок и их связей с файлами.
 * Декларируется в `:shared`. Реализуется в инфраструктурном слое.
 */
interface FolderRepository {

    /**
     * Создаёт новую папку с указанным именем.
     *
     * @param name Имя папки. Не может быть пустым или состоять только из пробелов.
     *             Максимум 255 символов. Символы: Unicode letters + digits + `-` + `_`.
     * @param parentId Идентификатор родительской папки или `null` для папки верхнего уровня.
     * @return Созданная папка с присвоенным UUID и временной меткой createdAt.
     * @throws FolderLimitExceededException если уже существует 100 папок (AC-41).
     * @throws FolderNameInvalidException если name пустое или состоит только из пробелов.
     * @throws FolderNameTooLongException если name.length > 255 (AC-34).
     * @throws FolderNotFoundException если [parentId] != null и родитель не существует.
     */
    suspend fun create(name: String, parentId: String? = null): Folder

    /**
     * Удаляет папку по идентификатору.
     * Каскадно удаляет вложенные папки (на любую глубину) и все FolderFileLink
     * удаляемых папок. RecentFile-записи не удаляются.
     * Если папка не существует — операция игнорируется (idempotent).
     */
    suspend fun delete(id: String)

    /**
     * Добавляет файл в папку, создавая запись FolderFileLink.
     *
     * @param folderId UUID папки.
     * @param uri Нормализованный URI файла. ДОЛЖЕН соответствовать существующей RecentFile.
     * @throws FolderNotFoundException если папка с folderId не существует.
     * @throws FileNotInHistoryException если RecentFile с указанным uri не существует (AC-37).
     * @throws FileDuplicateInFolderException если файл уже добавлен в эту папку.
     */
    suspend fun addFile(folderId: String, uri: String)

    /**
     * Удаляет ссылку файла из папки (FolderFileLink). RecentFile-запись не удаляется.
     * Если ссылка не существует — операция игнорируется (idempotent).
     *
     * @param folderId UUID папки.
     * @param uri Нормализованный URI файла.
     */
    suspend fun removeFile(folderId: String, uri: String)

    /**
     * Переименовывает папку.
     *
     * @param id UUID папки.
     * @param newName Новое имя. Те же правила валидации, что и при создании.
     * @throws FolderNotFoundException если папка с id не существует.
     * @throws FolderNameInvalidException если newName пустое или состоит только из пробелов.
     * @throws FolderNameTooLongException если newName.length > 255.
     * @throws FolderNameCharsInvalidException если newName содержит недопустимые символы.
     */
    suspend fun rename(id: String, newName: String)

    /**
     * Возвращает все папки, отсортированные по max(lastOpenedAt) файлов внутри DESC.
     * Папка без файлов сортируется по createdAt ASC (последней среди папок без файлов).
     * Никогда не бросает исключение — при ошибке возвращает пустой список.
     */
    suspend fun getAll(): List<Folder>

    /**
     * Возвращает URI файлов, добавленных в указанную папку. Порядок не гарантирован.
     *
     * @throws FolderNotFoundException если папка с folderId не существует.
     */
    suspend fun getFilesInFolder(folderId: String): List<String>
}
