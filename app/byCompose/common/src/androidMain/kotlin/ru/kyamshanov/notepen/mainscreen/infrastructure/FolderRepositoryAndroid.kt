package ru.kyamshanov.notepen.mainscreen.infrastructure

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.kyamshanov.notepen.mainscreen.domain.exception.FileDuplicateInFolderException
import ru.kyamshanov.notepen.mainscreen.domain.exception.FolderLimitExceededException
import ru.kyamshanov.notepen.mainscreen.domain.exception.FolderNameCharsInvalidException
import ru.kyamshanov.notepen.mainscreen.domain.exception.FolderNameInvalidException
import ru.kyamshanov.notepen.mainscreen.domain.exception.FolderNameTooLongException
import ru.kyamshanov.notepen.mainscreen.domain.exception.FolderNotFoundException
import ru.kyamshanov.notepen.mainscreen.domain.model.Folder
import ru.kyamshanov.notepen.mainscreen.domain.model.FolderFileLink
import ru.kyamshanov.notepen.mainscreen.domain.port.FolderRepository
import ru.kyamshanov.notepen.mainscreen.infrastructure.dto.FolderDto
import ru.kyamshanov.notepen.mainscreen.infrastructure.dto.FolderFileLinkDto
import ru.kyamshanov.notepen.mainscreen.domain.model.generateUuid

/**
 * Android-реализация [FolderRepository].
 *
 * Хранит папки в `context.filesDir/folders.json` и связи в `folder_links.json`.
 * Атомарная запись через tmp-файл + rename.
 * [Mutex] защищает от параллельных записей внутри процесса.
 */
class FolderRepositoryAndroid(
    private val context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : FolderRepository {

    private val foldersFile get() = java.io.File(context.filesDir, "folders.json")
    private val linksFile get() = java.io.File(context.filesDir, "folder_links.json")
    private val mutex = Mutex()

    private fun readFolders(): List<Folder> = try {
        foldersFile.takeIf { it.exists() }
            ?.let { json.decodeFromString<List<FolderDto>>(it.readText()).map { dto -> dto.toDomain() } }
            ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    private fun readLinks(): List<FolderFileLink> = try {
        linksFile.takeIf { it.exists() }
            ?.let { json.decodeFromString<List<FolderFileLinkDto>>(it.readText()).map { dto -> dto.toDomain() } }
            ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    private fun writeFolders(folders: List<Folder>) {
        val tmp = java.io.File(context.filesDir, "folders.tmp.json")
        tmp.writeText(json.encodeToString(folders.map { FolderDto.fromDomain(it) }))
        tmp.renameTo(foldersFile)
    }

    private fun writeLinks(links: List<FolderFileLink>) {
        val tmp = java.io.File(context.filesDir, "folder_links.tmp.json")
        tmp.writeText(json.encodeToString(links.map { FolderFileLinkDto.fromDomain(it) }))
        tmp.renameTo(linksFile)
    }

    override suspend fun create(name: String, parentId: String?): Folder = withContext(Dispatchers.IO) {
        mutex.withLock {
            validateName(name)
            val folders = readFolders()
            if (folders.size >= 100) throw FolderLimitExceededException()
            if (parentId != null && folders.none { it.id == parentId }) throw FolderNotFoundException(parentId)
            val folder = Folder(
                id = generateUuid(),
                name = name.trim(),
                createdAt = System.currentTimeMillis(),
                parentId = parentId,
            )
            writeFolders(folders + folder)
            folder
        }
    }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val folders = readFolders()
            val toRemove = descendantsOf(id, folders) + id
            writeFolders(folders.filter { it.id !in toRemove })
            writeLinks(readLinks().filter { it.folderId !in toRemove })
        }
    }

    override suspend fun addFile(folderId: String, uri: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val folders = readFolders()
            if (folders.none { it.id == folderId }) throw FolderNotFoundException(folderId)
            val links = readLinks()
            if (links.any { it.folderId == folderId && it.fileUri == uri }) {
                throw FileDuplicateInFolderException(folderId, uri)
            }
            writeLinks(links + FolderFileLink(folderId, uri, System.currentTimeMillis()))
        }
    }

    override suspend fun removeFile(folderId: String, uri: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            writeLinks(readLinks().filter { !(it.folderId == folderId && it.fileUri == uri) })
        }
    }

    override suspend fun rename(id: String, newName: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            validateName(newName)
            val folders = readFolders()
            if (folders.none { it.id == id }) throw FolderNotFoundException(id)
            writeFolders(folders.map { if (it.id == id) it.copy(name = newName.trim()) else it })
        }
    }

    override suspend fun getAll(): List<Folder> = withContext(Dispatchers.IO) {
        val folders = readFolders()
        val links = readLinks()
        folders.sortedByDescending { folder ->
            links.filter { it.folderId == folder.id }.maxOfOrNull { it.lastOpenedAt } ?: Long.MIN_VALUE
        }
    }

    override suspend fun getFilesInFolder(folderId: String): List<String> = withContext(Dispatchers.IO) {
        if (readFolders().none { it.id == folderId }) throw FolderNotFoundException(folderId)
        readLinks().filter { it.folderId == folderId }.map { it.fileUri }
    }

    /** Все папки-потомки [rootId] на любую глубину (без самого [rootId]). */
    private fun descendantsOf(rootId: String, folders: List<Folder>): Set<String> {
        val byParent = folders.groupBy { it.parentId }
        val result = mutableSetOf<String>()
        val queue = ArrayDeque<String>()
        queue.add(rootId)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            byParent[current]?.forEach { child ->
                if (result.add(child.id)) queue.add(child.id)
            }
        }
        return result
    }

    private fun validateName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw FolderNameInvalidException(name)
        if (trimmed.length > 255) throw FolderNameTooLongException(trimmed.length)
        if (!trimmed.matches(Regex("[\\p{L}\\p{N}\\-_]+"))) throw FolderNameCharsInvalidException(trimmed)
    }
}
