package ru.kyamshanov.notepen.mainscreen.ui.viewmodel

import com.arkivanov.essenty.lifecycle.Lifecycle
import com.arkivanov.essenty.lifecycle.coroutines.withLifecycle
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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import ru.kyamshanov.notepen.mainscreen.domain.exception.FileDuplicateInFolderException
import ru.kyamshanov.notepen.mainscreen.domain.exception.FileNotInHistoryException
import ru.kyamshanov.notepen.mainscreen.domain.exception.FolderLimitExceededException
import ru.kyamshanov.notepen.mainscreen.domain.exception.FolderNameCharsInvalidException
import ru.kyamshanov.notepen.mainscreen.domain.exception.FolderNotFoundException
import ru.kyamshanov.notepen.mainscreen.domain.model.Folder
import ru.kyamshanov.notepen.mainscreen.domain.model.RecentFile
import ru.kyamshanov.notepen.mainscreen.domain.port.FileHistoryRepository
import ru.kyamshanov.notepen.mainscreen.domain.port.FolderRepository
import ru.kyamshanov.notepen.mainscreen.domain.port.PdfThumbnailGenerator
import ru.kyamshanov.notepen.mainscreen.domain.port.ThumbnailRepository
import ru.kyamshanov.notepen.mainscreen.domain.usecase.AddToHistoryUseCase
import ru.kyamshanov.notepen.mainscreen.domain.usecase.CheckAvailabilityUseCase
import ru.kyamshanov.notepen.mainscreen.domain.usecase.OpenFileResult
import ru.kyamshanov.notepen.mainscreen.domain.usecase.OpenRecentFileUseCase
import ru.kyamshanov.notepen.mainscreen.domain.model.AvailabilityStatus
import ru.kyamshanov.notepen.mainscreen.ui.MainScreenIntent
import ru.kyamshanov.notepen.mainscreen.ui.model.CreateFolderDialogState
import ru.kyamshanov.notepen.mainscreen.ui.model.DeleteFolderDialogState
import ru.kyamshanov.notepen.mainscreen.ui.model.ErrorEvent
import ru.kyamshanov.notepen.mainscreen.ui.model.FolderUiModel
import ru.kyamshanov.notepen.mainscreen.domain.usecase.AddHistoryResult
import ru.kyamshanov.notepen.mainscreen.ui.model.MainScreenUiState
import ru.kyamshanov.notepen.mainscreen.ui.model.NavigationTarget
import ru.kyamshanov.notepen.mainscreen.ui.model.RecentFileUiModel
import ru.kyamshanov.notepen.mainscreen.ui.model.DragState
import ru.kyamshanov.notepen.mainscreen.ui.model.RemoteCatalogUiState
import ru.kyamshanov.notepen.mainscreen.ui.model.RemoteEntryUiModel
import ru.kyamshanov.notepen.mainscreen.ui.model.RemoteFolderUiModel
import ru.kyamshanov.notepen.mainscreen.ui.model.SafMergeDialogState
import ru.kyamshanov.notepen.mainscreen.ui.model.SuccessEvent
import ru.kyamshanov.notepen.mainscreen.ui.model.ThumbnailState
import ru.kyamshanov.notepen.sync.domain.RemoteDocumentOpener
import ru.kyamshanov.notepen.sync.domain.RemoteDocumentResult
import ru.kyamshanov.notepen.sync.domain.model.RemoteCatalog
import ru.kyamshanov.notepen.sync.domain.model.SyncStatus

/**
 * ViewModel главного экрана. Реализует MVI-цикл: Intent → State.
 *
 * Время жизни ограничено [Lifecycle] из Essenty; корутины отменяются при уничтожении компонента.
 *
 * @param lifecycle Жизненный цикл компонента.
 * @param historyRepository Порт для чтения/записи истории файлов.
 * @param folderRepository Порт для управления папками.
 * @param addToHistory UseCase добавления/обновления записи истории.
 * @param checkAvailability UseCase параллельной проверки доступности файлов.
 * @param openRecentFile UseCase синхронной проверки и открытия файла.
 * @param thumbnailRepository Порт кеша миниатюр.
 * @param thumbnailGenerator Порт генерации миниатюр.
 * @param nowMillis Поставщик текущего времени (epochMillis); заменяется в тестах.
 */
class MainScreenViewModel(
    lifecycle: Lifecycle,
    private val historyRepository: FileHistoryRepository,
    private val folderRepository: FolderRepository,
    private val addToHistory: AddToHistoryUseCase,
    private val checkAvailability: CheckAvailabilityUseCase,
    private val openRecentFile: OpenRecentFileUseCase,
    private val thumbnailRepository: ThumbnailRepository,
    private val thumbnailGenerator: PdfThumbnailGenerator,
    /**
     * Tablet-side stream of the host's current library catalog. Null when the
     * app instance is not a sync client (e.g. the desktop host itself).
     */
    private val remoteCatalogFlow: Flow<RemoteCatalog?>? = null,
    /**
     * Tablet-side opener for documents from the host's library. Null disables
     * the Remote-section tap → open flow (e.g. host-only app instance).
     */
    private val remoteDocumentOpener: RemoteDocumentOpener? = null,
    /**
     * Stream of `documentId → pendingCount` from
     * [ru.kyamshanov.notepen.sync.domain.port.PendingDeltaQueue]. Combined
     * with [remoteCatalogFlow] to drive the "не синхронизировано (N)" badge
     * on Remote tiles. Null disables the badge.
     */
    private val pendingDeltaCounts: Flow<Map<String, Int>>? = null,
    /**
     * Stream of `documentId → SyncStatus` from
     * [ru.kyamshanov.notepen.sync.domain.port.RemoteDocumentStatusRegistry].
     * Drives the "удалён на хосте" badge.
     */
    private val remoteDocumentStatuses: Flow<Map<String, SyncStatus>>? = null,
    private val nowMillis: () -> Long = { currentTimeMillis() },
) {
    private val logger = KotlinLogging.logger {}

    private val scope = CoroutineScope(Dispatchers.Main.immediate).withLifecycle(lifecycle)

    private val _state = MutableStateFlow(MainScreenUiState())

    /** Поток состояния экрана (read-only). */
    val state: StateFlow<MainScreenUiState> = _state.asStateFlow()

    init {
        remoteCatalogFlow?.let { catalogFlow ->
            val countsFlow = pendingDeltaCounts ?: flowOf(emptyMap())
            val statusFlow = remoteDocumentStatuses ?: flowOf(emptyMap())
            scope.launch {
                combine(catalogFlow, countsFlow, statusFlow) { catalog, counts, statuses ->
                    catalog?.toUiState(counts, statuses)
                }.collect { uiState ->
                    _state.update { it.copy(remoteSection = uiState) }
                }
            }
        }
    }

    /** Защита от двойного нажатия (CC-7). */
    private var isNavigating = false

    /** Семафор, ограничивающий параллельную генерацию миниатюр (CC-13). */
    private val thumbnailSemaphore = Semaphore(4)

    /**
     * Принять интент от UI.
     */
    fun onIntent(intent: MainScreenIntent) {
        scope.launch { handleIntent(intent) }
    }

    private suspend fun handleIntent(intent: MainScreenIntent) {
        when (intent) {
            is MainScreenIntent.ScreenVisible -> loadInitialData()
            is MainScreenIntent.OpenFilePicker -> openFilePicker()
            is MainScreenIntent.OpenRecentFile -> openRecentFileById(intent.id)
            is MainScreenIntent.CancelNavigation -> cancelNavigation()
            is MainScreenIntent.MergeSafRecords -> mergeSafRecords(intent)
            is MainScreenIntent.RejectSafMerge -> rejectSafMerge(intent)
            is MainScreenIntent.CreateFolder -> createFolder(intent.name)
            is MainScreenIntent.DeleteFolder -> deleteFolder(intent.id)
            is MainScreenIntent.RequestDeleteFolder -> requestDeleteFolder(intent.id)
            is MainScreenIntent.AddFileToFolder -> addFileToFolder(intent.folderId, intent.fileUri)
            is MainScreenIntent.RemoveFileFromFolder -> removeFileFromFolder(intent.folderId, intent.uri)
            is MainScreenIntent.RenameFolder -> renameFolder(intent.id, intent.newName)
            is MainScreenIntent.OpenCreateFolderDialog -> openCreateFolderDialog()
            is MainScreenIntent.DismissCreateFolderDialog ->
                _state.update { it.copy(createFolderDialog = null) }
            is MainScreenIntent.DismissDeleteFolderDialog ->
                _state.update { it.copy(deleteFolderDialog = null) }
            is MainScreenIntent.FolderDialogNameChanged -> updateFolderDialogName(intent.name)
            is MainScreenIntent.FilePickerResult -> handleFilePickerResult(intent)
            is MainScreenIntent.DragStarted -> handleDragStarted(intent)
            is MainScreenIntent.DragCancelled -> handleDragCancelled()
            is MainScreenIntent.DropOnFolder -> handleDropOnFolder(intent)
            is MainScreenIntent.OnSuccessEventHandled ->
                _state.update { it.copy(successEvent = null) }
            is MainScreenIntent.OpenRemoteEntry -> openRemoteEntry(intent.documentId, intent.displayName)
        }
    }

    /**
     * Asks the host to stream the document and, on success, sets a navigation
     * target to the local copy. Errors surface as one-shot [ErrorEvent]s.
     */
    private suspend fun openRemoteEntry(documentId: String, displayName: String) {
        val opener = remoteDocumentOpener ?: run {
            logger.warn { "OpenRemoteEntry($documentId) ignored — no opener wired" }
            return
        }
        if (isNavigating) return
        isNavigating = true
        logger.info { "Opening remote document $documentId ($displayName)" }
        val event: ErrorEvent? = when (val result = opener.open(documentId, displayName)) {
            is RemoteDocumentResult.Success -> {
                _state.update { it.copy(navigationTarget = NavigationTarget.Editor(result.localPath, 0)) }
                null
            }
            is RemoteDocumentResult.NotFound -> ErrorEvent.RemoteDocumentNotFound
            is RemoteDocumentResult.Timeout -> ErrorEvent.RemoteDocumentTimeout
            is RemoteDocumentResult.Failure -> {
                logger.warn { "Remote open failed for $documentId: ${result.cause::class.simpleName}" }
                ErrorEvent.RemoteDocumentFailed
            }
        }
        if (event != null) {
            isNavigating = false
            _state.update { it.copy(errorEvent = event) }
        }
    }

    private fun handleDragStarted(intent: MainScreenIntent.DragStarted) {
        _state.update {
            it.copy(
                dragState = DragState.Active(
                    fileId = intent.fileId,
                    fileUri = intent.fileUri,
                    displayName = intent.displayName,
                ),
            )
        }
    }

    private fun handleDragCancelled() {
        _state.update { it.copy(dragState = DragState.None) }
    }

    private suspend fun handleDropOnFolder(intent: MainScreenIntent.DropOnFolder) {
        // EC-4: silently ignore drop while loading
        if (_state.value.isLoading) return
        val currentDrag = _state.value.dragState as? DragState.Active ?: return
        val folderName = _state.value.folders.firstOrNull { it.id == intent.folderId }?.name ?: intent.folderId
        _state.update { it.copy(dragState = DragState.None) }
        addFileToFolder(intent.folderId, currentDrag.fileUri, folderName)
    }

    private suspend fun loadInitialData() {
        _state.update { it.copy(isLoading = true) }
        try {
            val files = withTimeout(5_000) { historyRepository.getAll() }
            val folders = folderRepository.getAll()
            _state.update {
                it.copy(
                    recentFiles = files.map { f -> f.toUiModel() },
                    folders = folders.map { folder -> folder.toUiModel(getFolderFileCount(folder.id)) },
                    isLoading = false,
                )
            }
            launchAvailabilityCheck(files)
            launchThumbnailGeneration(files)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn { "loadInitialData failed: ${e::class.simpleName}" }
            _state.update { it.copy(isLoading = false, errorEvent = ErrorEvent.HistoryFlushFailed) }
        }
    }

    private fun launchAvailabilityCheck(files: List<RecentFile>) {
        scope.launch {
            checkAvailability.execute(files).collect { update ->
                _state.update { state ->
                    state.copy(
                        recentFiles = state.recentFiles.map { model ->
                            if (model.id == update.id) {
                                // CC-23: preserve ARCHIVED_UNAVAILABLE — live check cannot downgrade it
                                if (model.availabilityStatus == AvailabilityStatus.ARCHIVED_UNAVAILABLE) model
                                else model.copy(availabilityStatus = update.status)
                            } else model
                        },
                    )
                }
            }
        }
    }

    private fun launchThumbnailGeneration(files: List<RecentFile>) {
        files.forEach { file ->
            scope.launch {
                thumbnailSemaphore.withPermit {
                    val cached = thumbnailRepository.get(file.uri, file.fileMtime)
                    if (cached != null) {
                        updateThumbnail(file.id, ThumbnailState.Ready(cached))
                        return@withPermit
                    }
                    updateThumbnail(file.id, ThumbnailState.Loading)
                    val result = thumbnailGenerator.generate(file.uri, widthPx = 280, heightPx = 400)
                    result.fold(
                        onSuccess = { data ->
                            thumbnailRepository.put(file.uri, data, file.fileMtime)
                            updateThumbnail(file.id, ThumbnailState.Ready(data))
                        },
                        onFailure = { cause ->
                            logger.warn { "Thumbnail generation failed for ${file.id}: ${cause::class.simpleName}: ${cause.message}" }
                            updateThumbnail(file.id, ThumbnailState.Error)
                        },
                    )
                }
            }
        }
    }

    private fun updateThumbnail(id: String, thumbnailState: ThumbnailState) {
        _state.update { s ->
            s.copy(
                recentFiles = s.recentFiles.map { m ->
                    if (m.id == id) m.copy(thumbnailState = thumbnailState) else m
                },
            )
        }
    }

    private fun openFilePicker() {
        _state.update { it.copy(navigationTarget = NavigationTarget.FilePicker) }
    }

    /**
     * Обрабатывает результат выбора файла из системного файлового менеджера (CC-2).
     *
     * Если [AddHistoryResult.SafFuzzyMatchDetected] — показывает диалог слияния SAF-записей.
     * Если [AddHistoryResult.Added] или [AddHistoryResult.Moved] — открывает редактор.
     */
    private suspend fun handleFilePickerResult(intent: MainScreenIntent.FilePickerResult) {
        val uri = intent.uri ?: run {
            _state.update { it.copy(navigationTarget = null) }
            return
        }
        val result = addToHistory.execute(
            uri = uri,
            displayName = intent.displayName,
            fileSize = intent.fileSize,
            openedAt = nowMillis(),
            lastPageIndex = 0,
        )
        result.fold(
            onSuccess = { addResult ->
                when (addResult) {
                    is AddHistoryResult.Added -> {
                        _state.update { s ->
                            s.copy(
                                recentFiles = listOf(addResult.record.toUiModel()) + s.recentFiles,
                                navigationTarget = NavigationTarget.Editor(uri, 0),
                            )
                        }
                        launchThumbnailGeneration(listOf(addResult.record))
                    }
                    is AddHistoryResult.Moved -> {
                        _state.update { it.copy(navigationTarget = NavigationTarget.Editor(uri, 0)) }
                    }
                    is AddHistoryResult.SafFuzzyMatchDetected -> {
                        val existingUiRecord = _state.value.recentFiles
                            .firstOrNull { it.id == addResult.existing.id }
                            ?: addResult.existing.toUiModel()
                        _state.update { s ->
                            s.copy(
                                safMergeDialog = SafMergeDialogState(
                                    existingRecord = existingUiRecord,
                                    newUri = addResult.newUri,
                                ),
                            )
                        }
                    }
                }
            },
            onFailure = {
                _state.update { it.copy(errorEvent = ErrorEvent.HistoryFlushFailed) }
            },
        )
    }

    private suspend fun openRecentFileById(id: String) {
        if (isNavigating) return
        val record = _state.value.recentFiles.firstOrNull { it.id == id } ?: return
        isNavigating = true
        val allFiles = historyRepository.getAll()
        val domainRecord = allFiles.firstOrNull { it.id == id } ?: run {
            isNavigating = false
            return
        }
        val uri = domainRecord.uri
        when (val result = openRecentFile.execute(uri)) {
            is OpenFileResult.Success -> {
                try {
                    val upsertResult = addToHistory.execute(
                        uri = uri,
                        displayName = domainRecord.displayName,
                        fileSize = domainRecord.fileSize,
                        openedAt = nowMillis(),
                        lastPageIndex = domainRecord.lastPageIndex,
                    )
                    if (upsertResult.isFailure) {
                        _state.update { it.copy(errorEvent = ErrorEvent.HistoryFlushFailed) }
                    }
                } catch (_: Exception) {
                    _state.update { it.copy(errorEvent = ErrorEvent.HistoryFlushFailed) }
                }
                val lastPage = record.lastPageIndex
                _state.update { it.copy(navigationTarget = NavigationTarget.Editor(uri, lastPage)) }
            }
            is OpenFileResult.NotAvailable -> {
                isNavigating = false
                val errorEvent = when (result.status) {
                    AvailabilityStatus.NOT_FOUND -> ErrorEvent.FileNotFound
                    else -> ErrorEvent.FileError
                }
                _state.update { s ->
                    s.copy(
                        recentFiles = s.recentFiles.map { m ->
                            if (m.id == id) {
                                // CC-23: preserve ARCHIVED_UNAVAILABLE — a live check cannot downgrade it
                                val newStatus = if (m.availabilityStatus == AvailabilityStatus.ARCHIVED_UNAVAILABLE) {
                                    AvailabilityStatus.ARCHIVED_UNAVAILABLE
                                } else {
                                    result.status
                                }
                                m.copy(availabilityStatus = newStatus)
                            } else m
                        },
                        errorEvent = errorEvent,
                    )
                }
            }
        }
    }

    private suspend fun cancelNavigation() {
        val target = _state.value.navigationTarget
        if (target is NavigationTarget.Editor) {
            try {
                historyRepository.rollbackUpsert(target.uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn { "rollbackUpsert failed (best-effort): ${e::class.simpleName}" }
            }
        }
        _state.update { it.copy(navigationTarget = null) }
        isNavigating = false
    }

    private fun mergeSafRecords(intent: MainScreenIntent.MergeSafRecords) {
        // AC-5b merge: update existing record's URI, discard old record.
        // Full SAF merge implementation is deferred to the stage that adds the SAF merge UI.
        // This handler closes the dialog; the merge itself is a no-op until the UI stage lands.
        _state.update { currentState ->
            currentState.copy(
                safMergeDialog = currentState.safMergeDialog?.takeIf { it.newUri != intent.newUri },
            )
        }
    }

    private suspend fun rejectSafMerge(intent: MainScreenIntent.RejectSafMerge) {
        _state.update { it.copy(safMergeDialog = null) }
        // CC-1: existing record gets FILE_ERROR; new URI is added as a fresh entry.
        try {
            historyRepository.updateStatus(intent.existingId, AvailabilityStatus.FILE_ERROR)
            _state.update { s ->
                s.copy(
                    recentFiles = s.recentFiles.map { m ->
                        if (m.id == intent.existingId) m.copy(availabilityStatus = AvailabilityStatus.FILE_ERROR) else m
                    },
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn { "rejectSafMerge updateStatus failed: ${e::class.simpleName}" }
        }
        // CC-1 (new-URI branch): upsert the new URI as a fresh history entry and reflect it in state.
        val newDisplayName = intent.newUri.substringAfterLast('/').ifBlank { intent.newUri }
        val addResult = addToHistory.execute(
            uri = intent.newUri,
            displayName = newDisplayName,
            fileSize = null,
            openedAt = nowMillis(),
        )
        addResult.fold(
            onSuccess = { result ->
                val newRecord = when (result) {
                    is AddHistoryResult.Added -> result.record
                    is AddHistoryResult.Moved -> result.record
                    is AddHistoryResult.SafFuzzyMatchDetected -> null
                }
                if (newRecord != null) {
                    _state.update { s ->
                        val alreadyPresent = s.recentFiles.any { it.id == newRecord.id }
                        if (alreadyPresent) s
                        else s.copy(recentFiles = (listOf(newRecord.toUiModel()) + s.recentFiles))
                    }
                }
            },
            onFailure = { e ->
                logger.warn { "rejectSafMerge addToHistory failed: ${e::class.simpleName}" }
            },
        )
    }

    private suspend fun createFolder(name: String) {
        _state.update { it.copy(createFolderDialog = null) }
        try {
            val folder = folderRepository.create(name)
            _state.update { s ->
                s.copy(
                    folders = (s.folders + folder.toUiModel(0))
                        .sortedByDescending { it.lastFileOpenedAt ?: 0L },
                )
            }
        } catch (_: FolderLimitExceededException) {
            _state.update { it.copy(errorEvent = ErrorEvent.FolderLimitExceeded) }
        } catch (_: FolderNameCharsInvalidException) {
            _state.update { it.copy(errorEvent = ErrorEvent.FolderNameCharsInvalid) }
        } catch (_: Exception) {
            _state.update { it.copy(errorEvent = ErrorEvent.FolderOperationFailed) }
        }
    }

    private suspend fun deleteFolder(id: String) {
        _state.update { it.copy(deleteFolderDialog = null) }
        try {
            folderRepository.delete(id)
            _state.update { s -> s.copy(folders = s.folders.filter { it.id != id }) }
        } catch (_: Exception) {
            _state.update { it.copy(errorEvent = ErrorEvent.FolderOperationFailed) }
        }
    }

    private fun requestDeleteFolder(id: String) {
        val folder = _state.value.folders.firstOrNull { it.id == id } ?: return
        _state.update { it.copy(deleteFolderDialog = DeleteFolderDialogState(id, folder.name)) }
    }

    private suspend fun addFileToFolder(folderId: String, fileUri: String, folderName: String? = null) {
        try {
            folderRepository.addFile(folderId, fileUri)
            val rawName = folderName ?: _state.value.folders.firstOrNull { it.id == folderId }?.name ?: folderId
            val name = if (rawName.length > 40) rawName.take(40) + "…" else rawName
            _state.update { it.copy(successEvent = SuccessEvent.FileAddedToFolder(name)) }
            refreshFolderFileCount(folderId)
        } catch (e: FileDuplicateInFolderException) {
            // AC-5: duplicate is not an error — show informational success event
            _state.update { it.copy(successEvent = SuccessEvent.FileAlreadyInFolder) }
        } catch (e: FileNotInHistoryException) {
            logger.warn { "addFileToFolder: file not in history — ${e::class.simpleName}" }
            _state.update { it.copy(errorEvent = ErrorEvent.FileNotInHistory) }
        } catch (e: FolderNotFoundException) {
            logger.warn { "addFileToFolder: folder not found — ${e::class.simpleName}" }
            _state.update { it.copy(errorEvent = ErrorEvent.FolderOperationFailed) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn { "addFileToFolder failed: ${e::class.simpleName}" }
            _state.update { it.copy(errorEvent = ErrorEvent.FolderOperationFailed) }
        }
    }

    private suspend fun removeFileFromFolder(folderId: String, uri: String) {
        try {
            folderRepository.removeFile(folderId, uri)
            refreshFolderFileCount(folderId)
        } catch (_: Exception) {
            _state.update { it.copy(errorEvent = ErrorEvent.FolderOperationFailed) }
        }
    }

    private suspend fun renameFolder(id: String, newName: String) {
        try {
            folderRepository.rename(id, newName)
            _state.update { s ->
                s.copy(
                    folders = s.folders.map { f -> if (f.id == id) f.copy(name = newName) else f },
                )
            }
        } catch (_: FolderNameCharsInvalidException) {
            _state.update { it.copy(errorEvent = ErrorEvent.FolderNameCharsInvalid) }
        } catch (_: Exception) {
            _state.update { it.copy(errorEvent = ErrorEvent.FolderOperationFailed) }
        }
    }

    private fun updateFolderDialogName(name: String) {
        val filtered = name.replace(Regex("[^\\p{L}\\p{N}\\-_]"), "").take(255)
        _state.update { s ->
            s.copy(
                createFolderDialog = s.createFolderDialog?.copy(
                    currentName = filtered,
                    isConfirmEnabled = filtered.isNotEmpty(),
                ),
            )
        }
    }

    private suspend fun refreshFolderFileCount(folderId: String) {
        val count = try {
            folderRepository.getFilesInFolder(folderId).size
        } catch (_: Exception) {
            return
        }
        _state.update { s ->
            s.copy(
                folders = s.folders.map { f ->
                    if (f.id == folderId) f.copy(fileCount = count) else f
                },
            )
        }
    }

    private suspend fun getFolderFileCount(folderId: String): Int =
        try { folderRepository.getFilesInFolder(folderId).size } catch (_: Exception) { 0 }

    /** Вызывается UI после обработки навигационного события. */
    fun onNavigationHandled() {
        _state.update { it.copy(navigationTarget = null) }
        isNavigating = false
    }

    /** Вызывается UI после показа сообщения об ошибке. */
    fun onErrorEventHandled() {
        _state.update { it.copy(errorEvent = null) }
    }

    /** Открыть диалог создания папки. */
    fun openCreateFolderDialog() {
        _state.update { it.copy(createFolderDialog = CreateFolderDialogState("", false)) }
    }
}

// --- Mapper extensions ---

private fun RecentFile.toUiModel() = RecentFileUiModel(
    id = id,
    uri = uri,
    displayName = displayName,
    openedAt = openedAt,
    availabilityStatus = availabilityStatus,
    thumbnailState = ThumbnailState.Loading,
    lastPageIndex = lastPageIndex,
)

private fun Folder.toUiModel(fileCount: Int) = FolderUiModel(
    id = id,
    name = name,
    fileCount = fileCount,
    createdAt = createdAt,
    lastFileOpenedAt = null,
)

private fun RemoteCatalog.toUiState(
    pendingCounts: Map<String, Int>,
    statuses: Map<String, SyncStatus>,
): RemoteCatalogUiState {
    val entries = recent.map { entry ->
        RemoteEntryUiModel(
            documentId = entry.documentId,
            displayName = entry.displayName,
            fileSize = entry.fileSize,
            lastOpenedAt = entry.lastOpenedAt,
            pendingCount = pendingCounts[entry.documentId] ?: 0,
            isOrphanedOnHost = statuses[entry.documentId] == SyncStatus.OrphanedOnHost,
        )
    }
    val countsByFolder = folderLinks.groupingBy { it.folderId }.eachCount()
    val folders = folders.map { folder ->
        RemoteFolderUiModel(
            folderId = folder.folderId,
            name = folder.name,
            fileCount = countsByFolder[folder.folderId] ?: 0,
        )
    }
    return RemoteCatalogUiState(hostName = hostName, entries = entries, folders = folders)
}

/** Platform-provided current time in milliseconds since Unix epoch. */
internal expect fun currentTimeMillis(): Long
