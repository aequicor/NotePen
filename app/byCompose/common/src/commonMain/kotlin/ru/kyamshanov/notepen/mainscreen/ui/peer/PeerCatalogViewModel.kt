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

    init {
        val onlineFlow = onlinePeerIdsFlow ?: flowOf(null)
        scope.launch {
            combine(catalogsFlow, onlineFlow) { catalogs, onlineIds ->
                catalogs.entries.firstOrNull { it.key.id == peerId } to onlineIds
            }.collect { (entry, onlineIds) ->
                if (entry == null) {
                    _state.update { it.copy(isDisconnected = true) }
                    return@collect
                }
                val (device, catalog) = entry
                val isOffline = onlineIds != null && peerId !in onlineIds
                val rawEntries = catalog.recent.map { e ->
                    RemoteEntryUiModel(
                        documentId = e.documentId,
                        displayName = e.displayName,
                        fileSize = e.fileSize,
                        lastOpenedAt = e.lastOpenedAt,
                    )
                }
                val entries = if (isOffline) {
                    rawEntries.filter { hasCachedCopy(it.documentId, it.displayName) }
                } else {
                    rawEntries
                }
                // Папки оставляем только когда пир онлайн — иначе содержимое
                // папок недоступно (мы не качаем содержимое наперёд).
                val folders = if (isOffline) {
                    emptyList()
                } else {
                    val countsByFolder = catalog.folderLinks.groupingBy { it.folderId }.eachCount()
                    catalog.folders.map { f ->
                        RemoteFolderUiModel(
                            folderId = f.folderId,
                            name = f.name,
                            fileCount = countsByFolder[f.folderId] ?: 0,
                        )
                    }
                }
                _state.update {
                    it.copy(
                        peerName = catalog.hostName.ifBlank { device.name },
                        entries = entries,
                        folders = folders,
                        isDisconnected = isOffline,
                    )
                }
            }
        }
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
