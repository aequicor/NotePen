package ru.kyamshanov.notepen.mainscreen.infrastructure

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.kyamshanov.notepen.mainscreen.domain.port.LibraryFolder
import ru.kyamshanov.notepen.mainscreen.domain.port.LibraryFolderItem
import ru.kyamshanov.notepen.sync.domain.port.CatalogChangeNotifier
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private val logger = KotlinLogging.logger {}

/**
 * Desktop-реализация [LibraryFolder] поверх обычной директории.
 *
 * Корнем выступает та же папка, из которой `FileSystemLibraryManifestProvider`
 * строит каталог для пиров (по умолчанию `~/NotePen Library`). После каждого
 * успешного копирования дёргается [notifier], чтобы подключённые устройства
 * получили обновлённый каталог.
 */
class FileSystemLibraryFolder(
    private val root: File,
    private val isBook: (File) -> Boolean,
    private val notifier: CatalogChangeNotifier,
    scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
) : LibraryFolder {
    private val rootCanonical: File = root.apply { mkdirs() }.canonicalFile
    private val mutex = Mutex()
    private val _items = MutableStateFlow<List<LibraryFolderItem>>(emptyList())
    override val items: StateFlow<List<LibraryFolderItem>> = _items.asStateFlow()

    init {
        scope.launch { refresh() }
    }

    override suspend fun refresh() {
        val snapshot = withContext(ioDispatcher) { scan() }
        _items.value = snapshot
    }

    override suspend fun addCopy(sourceUri: String): Result<LibraryFolderItem> =
        runCatching {
            val source =
                resolveLocalFile(sourceUri)
                    ?: error("Источник недоступен: $sourceUri")
            require(source.isFile) { "Не файл: ${source.path}" }
            require(isBook(source)) { "Неподдерживаемое расширение: ${source.name}" }

            // Уже внутри библиотеки — копия не нужна, просто возвращаем существующий элемент.
            if (source.canonicalFile.toPath().startsWith(rootCanonical.toPath())) {
                refresh()
                _items.value.firstOrNull { it.uri == source.canonicalFile.path }
                    ?: error("Файл уже в библиотеке, но не виден в скане: ${source.path}")
            } else {
                val target = mutex.withLock { reserveTarget(source.name) }
                withContext(ioDispatcher) {
                    Files.copy(
                        source.toPath(),
                        target.toPath(),
                        StandardCopyOption.COPY_ATTRIBUTES,
                    )
                }
                refresh()
                notifier.notifyChanged()
                _items.value.firstOrNull { it.uri == target.canonicalFile.path }
                    ?: toItem(target)
            }
        }.onFailure { logger.warn(it) { "addCopy failed for $sourceUri" } }

    /**
     * Подбирает свободное имя в библиотеке: при коллизии добавляет суффикс
     * ` (2)`, ` (3)` перед расширением. Резервирование делается под [mutex],
     * иначе два параллельных drop'а могли бы выбрать одно и то же имя.
     */
    private fun reserveTarget(originalName: String): File {
        val base = originalName.substringBeforeLast('.', originalName)
        val ext = originalName.substringAfterLast('.', "")
        val suffix = if (ext.isEmpty()) "" else ".$ext"
        var candidate = File(rootCanonical, originalName)
        var counter = 2
        while (candidate.exists()) {
            candidate = File(rootCanonical, "$base ($counter)$suffix")
            counter++
        }
        // Создаём «заглушку» чтобы parallel-резервации видели имя занятым.
        // Files.copy ниже всё равно перезапишет содержимое; флаг REPLACE_EXISTING
        // не используется — мы только что сами создали пустой маркер.
        candidate.createNewFile()
        return candidate
    }

    private fun scan(): List<LibraryFolderItem> {
        if (!rootCanonical.isDirectory) return emptyList()
        return rootCanonical
            .walkTopDown()
            .filter { it.isFile && isBook(it) }
            .mapNotNull { file ->
                val canonical = file.canonicalFile
                if (!canonical.toPath().startsWith(rootCanonical.toPath())) return@mapNotNull null
                toItem(canonical)
            }
            .sortedByDescending { it.modifiedAt }
            .toList()
    }

    private fun toItem(file: File): LibraryFolderItem {
        val canonical = file.canonicalFile
        val relative = canonical.relativeTo(rootCanonical).path.replace(File.separatorChar, '/')
        return LibraryFolderItem(
            id = relative,
            uri = canonical.path,
            displayName = canonical.name,
            sizeBytes = canonical.length().takeIf { it >= 0 },
            modifiedAt = canonical.lastModified(),
        )
    }

    /**
     * Принимает либо `file://`-URI, либо абсолютный путь — на desktop оба
     * варианта встречаются (history хранит сырые пути, drag-and-drop из ОС
     * приносит file://).
     */
    private fun resolveLocalFile(raw: String): File? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return runCatching {
            if (trimmed.startsWith("file:")) {
                File(URI(trimmed))
            } else {
                File(trimmed)
            }
        }.getOrNull()
    }
}
