package ru.kyamshanov.notepen.mainscreen.infrastructure

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.kyamshanov.notepen.mainscreen.domain.model.AvailabilityStatus
import ru.kyamshanov.notepen.mainscreen.domain.model.FileHistoryManager
import ru.kyamshanov.notepen.mainscreen.domain.model.RecentFile
import ru.kyamshanov.notepen.mainscreen.domain.model.isAnnotationSidecarUri
import ru.kyamshanov.notepen.mainscreen.domain.port.FileHistoryRepository
import ru.kyamshanov.notepen.mainscreen.infrastructure.dto.RecentFileDto

/**
 * Android-реализация [FileHistoryRepository].
 *
 * Хранит историю в `context.filesDir/history.json`.
 * Атомарная запись через tmp-файл + rename.
 * [Mutex] защищает от параллельных записей внутри процесса.
 */
class FileHistoryRepositoryAndroid(
    private val context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : FileHistoryRepository {
    private val historyFile get() = java.io.File(context.filesDir, "history.json")
    private val mutex = Mutex()

    override suspend fun getAll(): List<RecentFile> =
        withContext(Dispatchers.IO) {
            try {
                val text = historyFile.takeIf { it.exists() }?.readText() ?: return@withContext emptyList()
                json.decodeFromString<List<RecentFileDto>>(text)
                    .map { it.toDomain() }
                    .filterNot { isAnnotationSidecarUri(it.uri) }
                    .sortedByDescending { it.openedAt }
            } catch (_: Exception) {
                emptyList()
            }
        }

    override suspend fun upsert(
        file: RecentFile,
        lastPageIndex: Int,
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = getAll().toMutableList()
            val (newList, _) =
                FileHistoryManager.applyUpsert(
                    current,
                    file.copy(lastPageIndex = lastPageIndex),
                )
            writeAtomic(newList)
        }
    }

    override suspend fun updateStatus(
        id: String,
        status: AvailabilityStatus,
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = getAll().map { if (it.id == id) it.copy(availabilityStatus = status) else it }
            writeAtomic(updated)
        }
    }

    override suspend fun updateLastPage(
        uri: String,
        pageIndex: Int,
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = getAll().map { if (it.uri == uri) it.copy(lastPageIndex = pageIndex) else it }
            writeAtomic(updated)
        }
    }

    override suspend fun rollbackUpsert(uri: String) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val current = getAll().filter { it.uri != uri }
                writeAtomic(current)
            }
        }

    private fun writeAtomic(list: List<RecentFile>) {
        val tmp = java.io.File(context.filesDir, "history.tmp.json")
        tmp.writeText(json.encodeToString(list.map { RecentFileDto.fromDomain(it) }))
        tmp.renameTo(historyFile)
    }
}
