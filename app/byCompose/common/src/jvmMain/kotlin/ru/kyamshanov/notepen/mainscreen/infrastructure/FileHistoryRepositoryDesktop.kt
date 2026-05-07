package ru.kyamshanov.notepen.mainscreen.infrastructure

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.kyamshanov.notepen.mainscreen.domain.model.AvailabilityStatus
import ru.kyamshanov.notepen.mainscreen.domain.model.FileHistoryManager
import ru.kyamshanov.notepen.mainscreen.domain.model.RecentFile
import ru.kyamshanov.notepen.mainscreen.domain.port.FileHistoryRepository
import ru.kyamshanov.notepen.mainscreen.infrastructure.dto.RecentFileDto

/**
 * Desktop (JVM)-реализация [FileHistoryRepository].
 *
 * Хранит историю в `$HOME/.notepen/history.json`.
 * Двухуровневая блокировка (CC-10):
 * - [Mutex] — исключает параллельный доступ из потоков одного JVM-процесса.
 * - [java.nio.channels.FileLock] — исключает доступ из разных JVM-процессов.
 *
 * ACT-NOW R4: все операции с FileLock — исключительно внутри withContext(Dispatchers.IO).
 */
class FileHistoryRepositoryDesktop(
    private val dataDir: java.io.File = getAppDataDir(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : FileHistoryRepository {

    private val historyFile get() = java.io.File(dataDir, "history.json")
    private val lockFile get() = java.io.File(dataDir, "history.lock")
    private val inProcessMutex = Mutex()

    override suspend fun getAll(): List<RecentFile> = withContext(Dispatchers.IO) {
        try {
            val text = historyFile.takeIf { it.exists() }?.readText()
                ?: return@withContext emptyList()
            json.decodeFromString<List<RecentFileDto>>(text)
                .map { it.toDomain() }
                .sortedByDescending { it.openedAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun upsert(file: RecentFile, lastPageIndex: Int) {
        withLockedIO {
            val current = readUnsafe()
            val (newList, _) = FileHistoryManager.applyUpsert(
                current,
                file.copy(lastPageIndex = lastPageIndex),
            )
            writeAtomicUnsafe(newList)
        }
    }

    override suspend fun updateStatus(id: String, status: AvailabilityStatus) {
        withLockedIO {
            val updated = readUnsafe().map { if (it.id == id) it.copy(availabilityStatus = status) else it }
            writeAtomicUnsafe(updated)
        }
    }

    override suspend fun updateLastPage(uri: String, pageIndex: Int) {
        withLockedIO {
            val updated = readUnsafe().map { if (it.uri == uri) it.copy(lastPageIndex = pageIndex) else it }
            writeAtomicUnsafe(updated)
        }
    }

    override suspend fun rollbackUpsert(uri: String) {
        withLockedIO {
            writeAtomicUnsafe(readUnsafe().filter { it.uri != uri })
        }
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

    private fun readUnsafe(): List<RecentFile> = try {
        historyFile.takeIf { it.exists() }?.let {
            json.decodeFromString<List<RecentFileDto>>(it.readText()).map { dto -> dto.toDomain() }
        } ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    private fun writeAtomicUnsafe(list: List<RecentFile>) {
        val tmp = java.io.File(dataDir, "history.tmp.json")
        tmp.writeText(json.encodeToString(list.map { RecentFileDto.fromDomain(it) }))
        java.nio.file.Files.move(tmp.toPath(), historyFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }
}
