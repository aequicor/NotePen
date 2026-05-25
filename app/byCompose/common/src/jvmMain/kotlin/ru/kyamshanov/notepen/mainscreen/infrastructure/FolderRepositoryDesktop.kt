package ru.kyamshanov.notepen.mainscreen.infrastructure

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
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
import ru.kyamshanov.notepen.mainscreen.domain.model.generateUuid
import ru.kyamshanov.notepen.mainscreen.domain.port.FolderRepository
import ru.kyamshanov.notepen.mainscreen.infrastructure.dto.FolderDto
import ru.kyamshanov.notepen.mainscreen.infrastructure.dto.FolderFileLinkDto

/**
 * Desktop (JVM)-реализация [FolderRepository].
 *
 * Хранит папки в `$HOME/.notepen/folders.json` и связи в `folder_links.json`.
 * Двухуровневая блокировка (CC-10):
 * - [Mutex] — исключает параллельный доступ из потоков одного JVM-процесса.
 * - [java.nio.channels.FileLock] — исключает доступ из разных JVM-процессов.
 *
 * ACT-NOW R4: FileLock приобретается строго внутри withContext(Dispatchers.IO).
 */
class FolderRepositoryDesktop(
    private val dataDir: java.io.File = getAppDataDir(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : FolderRepository {
    private val foldersFile get() = java.io.File(dataDir, "folders.json")
    private val linksFile get() = java.io.File(dataDir, "folder_links.json")
    private val lockFile get() = java.io.File(dataDir, "folders.lock")
    private val inProcessMutex = Mutex()

    override suspend fun create(
        name: String,
        parentId: String?,
    ): Folder =
        withLockedIO {
            validateName(name)
            val folders = readFolders()
            if (folders.size >= 100) throw FolderLimitExceededException()
            if (parentId != null && folders.none { it.id == parentId }) throw FolderNotFoundException(parentId)
            val folder =
                Folder(
                    id = generateUuid(),
                    name = name.trim(),
                    createdAt = System.currentTimeMillis(),
                    parentId = parentId,
                )
            writeFolders(folders + folder)
            folder
        }

    override suspend fun delete(id: String): Unit =
        withLockedIO {
            val folders = readFolders()
            val toRemove = descendantsOf(id, folders) + id
            writeFolders(folders.filter { it.id !in toRemove })
            writeLinks(readLinks().filter { it.folderId !in toRemove })
        }

    override suspend fun addFile(
        folderId: String,
        uri: String,
    ): Unit =
        withLockedIO {
            val folders = readFolders()
            if (folders.none { it.id == folderId }) throw FolderNotFoundException(folderId)
            val links = readLinks()
            if (links.any { it.folderId == folderId && it.fileUri == uri }) {
                throw FileDuplicateInFolderException(folderId, uri)
            }
            writeLinks(links + FolderFileLink(folderId, uri, System.currentTimeMillis()))
        }

    override suspend fun removeFile(
        folderId: String,
        uri: String,
    ): Unit =
        withLockedIO {
            writeLinks(readLinks().filter { !(it.folderId == folderId && it.fileUri == uri) })
        }

    override suspend fun rename(
        id: String,
        newName: String,
    ): Unit =
        withLockedIO {
            validateName(newName)
            val folders = readFolders()
            if (folders.none { it.id == id }) throw FolderNotFoundException(id)
            writeFolders(folders.map { if (it.id == id) it.copy(name = newName.trim()) else it })
        }

    override suspend fun getAll(): List<Folder> =
        withContext(Dispatchers.IO) {
            val folders = readFolders()
            val links = readLinks()
            folders.sortedByDescending { folder ->
                links.filter { it.folderId == folder.id }.maxOfOrNull { it.lastOpenedAt } ?: Long.MIN_VALUE
            }
        }

    override suspend fun getFilesInFolder(folderId: String): List<String> =
        withContext(Dispatchers.IO) {
            if (readFolders().none { it.id == folderId }) throw FolderNotFoundException(folderId)
            readLinks().filter { it.folderId == folderId }.map { it.fileUri }
        }

    /**
     * Двухуровневая блокировка: Mutex → withContext(IO) → FileLock.
     * ACT-NOW R4: FileLock приобретается строго внутри withContext(Dispatchers.IO).
     */
    private suspend fun <T> withLockedIO(block: () -> T): T =
        inProcessMutex.withLock {
            withContext(Dispatchers.IO) {
                java.io.RandomAccessFile(lockFile, "rw").use { raf ->
                    raf.channel.use { channel ->
                        val lock = channel.lock() // блокирующий — ОК внутри IO
                        try {
                            block()
                        } finally {
                            lock.release()
                        }
                    }
                }
            }
        }

    private fun readFolders(): List<Folder> =
        try {
            foldersFile.takeIf { it.exists() }?.let {
                json.decodeFromString<List<FolderDto>>(it.readText()).map { dto -> dto.toDomain() }
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

    private fun readLinks(): List<FolderFileLink> =
        try {
            linksFile.takeIf { it.exists() }?.let {
                json.decodeFromString<List<FolderFileLinkDto>>(it.readText()).map { dto -> dto.toDomain() }
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

    private fun writeFolders(folders: List<Folder>) {
        val tmp = java.io.File(dataDir, "folders.tmp.json")
        tmp.writeText(json.encodeToString(folders.map { FolderDto.fromDomain(it) }))
        java.nio.file.Files.move(tmp.toPath(), foldersFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }

    private fun writeLinks(links: List<FolderFileLink>) {
        val tmp = java.io.File(dataDir, "folder_links.tmp.json")
        tmp.writeText(json.encodeToString(links.map { FolderFileLinkDto.fromDomain(it) }))
        java.nio.file.Files.move(tmp.toPath(), linksFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }

    /** Все папки-потомки [rootId] на любую глубину (без самого [rootId]). */
    private fun descendantsOf(
        rootId: String,
        folders: List<Folder>,
    ): Set<String> {
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
