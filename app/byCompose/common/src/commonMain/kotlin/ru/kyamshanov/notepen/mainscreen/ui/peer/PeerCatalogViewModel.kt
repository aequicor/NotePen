package ru.kyamshanov.notepen.mainscreen.ui.peer

import com.arkivanov.essenty.lifecycle.Lifecycle
import com.arkivanov.essenty.lifecycle.coroutines.withLifecycle
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.mainscreen.ui.model.RemoteEntryUiModel
import ru.kyamshanov.notepen.mainscreen.ui.model.RemoteFolderUiModel
import ru.kyamshanov.notepen.sync.domain.RemoteDocumentOpener
import ru.kyamshanov.notepen.sync.domain.RemoteDocumentResult
import ru.kyamshanov.notepen.sync.domain.documentIdToCacheFileName
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.RemoteCatalog
import ru.kyamshanov.notepen.sync.infrastructure.okio_exists

/**
 * ViewModel sub-экрана каталога пира.
 *
 * Подписывается на общую карту каталогов и фильтрует её до одной записи
 * по `peerId`. Открывает документы через [RemoteDocumentOpener] (если он
 * есть — на хост-стороне `opener` может быть null, тогда тап показывает
 * сообщение «не поддерживается»).
 *
 * @param lifecycle Decompose-лайфсайкл.
 * @param peerId Id пира, чей каталог показываем.
 * @param fallbackName Имя пира, известное до получения каталога — используется
 *        как заголовок до первого `RemoteCatalogResponse`.
 * @param catalogsFlow Поток карты всех известных каталогов.
 * @param remoteDocumentOpener Опеннер документов или null (host-side).
 * @param onDocumentReady Колбэк навигации в редактор после успешного открытия.
 */
class PeerCatalogViewModel(
    lifecycle: Lifecycle,
    private val peerId: String,
    private val fallbackName: String,
    private val catalogsFlow: Flow<Map<DeviceInfo, RemoteCatalog>>,
    private val onlinePeerIdsFlow: Flow<Set<String>>?,
    private val remoteDocumentOpener: RemoteDocumentOpener?,
    /** Каталог локально-кешированных PDF (`destDir` из RemoteDocumentOpener). */
    private val receivedPdfDir: String?,
    private val onDocumentReady: (uri: String, lastPageIndex: Int) -> Unit,
) {
    private val logger = KotlinLogging.logger {}

    private val scope = CoroutineScope(Dispatchers.Main.immediate).withLifecycle(lifecycle)

    private val _state = MutableStateFlow(PeerCatalogUiState(peerName = fallbackName))

    /** Поток состояния sub-экрана (read-only). */
    val state: StateFlow<PeerCatalogUiState> = _state.asStateFlow()

    private var isOpening = false

    /**
     * Путь вложенных папок, в которые вошёл пользователь (id, от корня вниз).
     * Пустой список = корень каталога. Последний элемент — текущая папка.
     */
    private val folderPath = MutableStateFlow<List<String>>(emptyList())

    init {
        val onlineFlow = onlinePeerIdsFlow ?: flowOf(null)
        scope.launch {
            combine(catalogsFlow, onlineFlow, folderPath) { catalogs, onlineIds, path ->
                Triple(catalogs.entries.firstOrNull { it.key.id == peerId }, onlineIds, path)
            }.collect { (entry, onlineIds, path) ->
                if (entry == null) {
                    _state.update { it.copy(isDisconnected = true) }
                    return@collect
                }
                val (device, catalog) = entry
                val isOffline = onlineIds != null && peerId !in onlineIds
                // Элементы пути могли исчезнуть из обновлённого каталога — обрезаем
                // путь до самого длинного валидного префикса (цепочки parent → child).
                val validPath = longestValidPath(path, catalog.folders)
                if (validPath != path) {
                    folderPath.value = validPath
                    return@collect
                }
                val currentFolderId = validPath.lastOrNull()
                val rawEntries = catalog.recent.map { e ->
                    RemoteEntryUiModel(
                        documentId = e.documentId,
                        displayName = e.displayName,
                        fileSize = e.fileSize,
                        lastOpenedAt = e.lastOpenedAt,
                    )
                }
                val entries = when {
                    // Внутри папки показываем только её файлы (folderLinks ⊆ recent).
                    currentFolderId != null -> {
                        val ids = catalog.folderLinks
                            .filter { it.folderId == currentFolderId }
                            .map { it.documentId }
                            .toSet()
                        rawEntries.filter { it.documentId in ids }
                    }
                    isOffline -> rawEntries.filter { hasCachedCopy(it.documentId, it.displayName) }
                    else -> rawEntries
                }
                // Подпапки текущего уровня (parentFolderId == текущая папка / null в корне).
                // Когда пир офлайн — папки скрываем: их содержимое недоступно (не качаем наперёд).
                val folders = if (isOffline) {
                    emptyList()
                } else {
                    val countsByFolder = catalog.folderLinks.groupingBy { it.folderId }.eachCount()
                    catalog.folders
                        .filter { it.parentFolderId == currentFolderId }
                        .map { f ->
                            RemoteFolderUiModel(
                                folderId = f.folderId,
                                name = f.name,
                                fileCount = countsByFolder[f.folderId] ?: 0,
                            )
                        }
                }
                val currentName = currentFolderId?.let { id -> catalog.folders.firstOrNull { it.folderId == id }?.name }
                _state.update {
                    it.copy(
                        peerName = catalog.hostName.ifBlank { device.name },
                        entries = entries,
                        folders = folders,
                        isDisconnected = isOffline,
                        currentFolderId = currentFolderId,
                        currentFolderName = currentName,
                    )
                }
            }
        }
    }

    /** Войти в подпапку пира — показать её содержимое. */
    fun openFolder(folderId: String) {
        folderPath.value = folderPath.value + folderId
    }

    /**
     * Подняться на уровень вверх (выйти из текущей папки). Возвращает true, если
     * подъём был выполнен (мы были внутри папки) — UI использует это, чтобы решить,
     * перехватить ли «назад» или покинуть экран.
     */
    fun exitFolder(): Boolean {
        val path = folderPath.value
        if (path.isEmpty()) return false
        folderPath.value = path.dropLast(1)
        return true
    }

    /**
     * Обрезает [path] до самого длинного префикса, образующего корректную цепочку
     * `parent → child` в [folders]. Защищает от исчезнувших/перемещённых папок.
     */
    private fun longestValidPath(
        path: List<String>,
        folders: List<ru.kyamshanov.notepen.sync.domain.model.RemoteFolder>,
    ): List<String> {
        val byId = folders.associateBy { it.folderId }
        val result = mutableListOf<String>()
        var expectedParent: String? = null
        for (id in path) {
            val folder = byId[id] ?: break
            if (folder.parentFolderId != expectedParent) break
            result.add(id)
            expectedParent = id
        }
        return result
    }

    private fun hasCachedCopy(documentId: String, displayName: String): Boolean {
        val dir = receivedPdfDir ?: return false
        if (displayName.isBlank()) return false
        return okio_exists(joinPath(dir, documentIdToCacheFileName(documentId, displayName)))
    }

    /** Открыть документ из каталога пира. */
    fun openEntry(documentId: String, displayName: String) {
        val opener = remoteDocumentOpener
        if (opener == null) {
            _state.update {
                it.copy(errorMessage = "Открытие документов с этого пира пока не поддерживается")
            }
            return
        }
        if (isOpening) return
        isOpening = true
        scope.launch {
            val message: String? = when (val result = opener.open(documentId, displayName)) {
                is RemoteDocumentResult.Success -> {
                    onDocumentReady(result.localPath, 0)
                    null
                }
                is RemoteDocumentResult.NotFound -> "Документ удалён на хосте"
                is RemoteDocumentResult.Timeout -> "Хост не отвечает — попробуйте ещё раз"
                is RemoteDocumentResult.Failure -> {
                    logger.warn { "Remote open failed for $documentId: ${result.cause::class.simpleName}" }
                    "Не удалось получить файл с хоста"
                }
            }
            isOpening = false
            if (message != null) {
                _state.update { it.copy(errorMessage = message) }
            }
        }
    }

    /** Сбросить одноразовое сообщение об ошибке после показа в UI. */
    fun onErrorShown() {
        _state.update { it.copy(errorMessage = null) }
    }
}

private fun joinPath(dir: String, name: String): String {
    val sep = if (dir.contains('\\')) "\\" else "/"
    return if (dir.endsWith('/') || dir.endsWith('\\')) "$dir$name" else "$dir$sep$name"
}
