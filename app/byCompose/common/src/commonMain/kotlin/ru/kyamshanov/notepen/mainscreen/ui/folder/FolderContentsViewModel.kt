package ru.kyamshanov.notepen.mainscreen.ui.folder

import com.arkivanov.essenty.lifecycle.Lifecycle
import com.arkivanov.essenty.lifecycle.coroutines.withLifecycle
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import ru.kyamshanov.notepen.mainscreen.domain.exception.FileDuplicateInFolderException
import ru.kyamshanov.notepen.mainscreen.domain.exception.FileNotInHistoryException
import ru.kyamshanov.notepen.mainscreen.domain.exception.FolderNotFoundException
import ru.kyamshanov.notepen.mainscreen.domain.model.Folder
import ru.kyamshanov.notepen.mainscreen.domain.model.RecentFile
import ru.kyamshanov.notepen.mainscreen.domain.port.FileHistoryRepository
import ru.kyamshanov.notepen.mainscreen.domain.port.FolderRepository
import ru.kyamshanov.notepen.mainscreen.domain.port.PdfThumbnailGenerator
import ru.kyamshanov.notepen.mainscreen.domain.port.ThumbnailRepository
import ru.kyamshanov.notepen.mainscreen.domain.usecase.AddToHistoryUseCase
import ru.kyamshanov.notepen.mainscreen.ui.model.CreateFolderDialogState
import ru.kyamshanov.notepen.mainscreen.ui.model.DeleteFolderDialogState
import ru.kyamshanov.notepen.mainscreen.ui.model.FolderUiModel
import ru.kyamshanov.notepen.mainscreen.ui.model.RecentFileUiModel
import ru.kyamshanov.notepen.mainscreen.ui.model.ThumbnailState
import ru.kyamshanov.notepen.mainscreen.ui.viewmodel.currentTimeMillis
import ru.kyamshanov.notepen.resolveDocumentDisplayName

/**
 * ViewModel sub-экрана содержимого папки.
 *
 * Загружает файлы, привязанные к папке ([FolderRepository.getFilesInFolder]), сопоставляет
 * их с историей для отображения и генерирует миниатюры. Импорт нового файла, пока папка
 * открыта, авто-привязывает его к этой папке.
 *
 * @param lifecycle Decompose-лайфсайкл.
 * @param folderId UUID открытой папки.
 * @param folderName Имя папки (для заголовка).
 * @param onOpenEditor Колбэк навигации в редактор.
 * @param nowMillis Поставщик текущего времени; заменяется в тестах.
 */
class FolderContentsViewModel(
    lifecycle: Lifecycle,
    private val folderId: String,
    folderName: String,
    private val historyRepository: FileHistoryRepository,
    private val folderRepository: FolderRepository,
    private val addToHistory: AddToHistoryUseCase,
    private val thumbnailRepository: ThumbnailRepository,
    private val thumbnailGenerator: PdfThumbnailGenerator,
    private val onOpenEditor: (uri: String, lastPageIndex: Int) -> Unit,
    private val onOpenFolder: (folderId: String, folderName: String) -> Unit,
    private val nowMillis: () -> Long = { currentTimeMillis() },
) {
    private val logger = KotlinLogging.logger {}

    private val scope = CoroutineScope(Dispatchers.Main.immediate).withLifecycle(lifecycle)

    private val _state = MutableStateFlow(FolderContentsUiState(folderName = folderName))

    /** Поток состояния sub-экрана (read-only). */
    val state: StateFlow<FolderContentsUiState> = _state.asStateFlow()

    private val thumbnailSemaphore = Semaphore(4)

    private var isOpening = false

    /** Защита от двойного нажатия «Создать вложенную папку». */
    private var isMutatingFolder = false

    init {
        scope.launch { loadFiles() }
    }

    private suspend fun loadFiles() {
        _state.update { it.copy(isLoading = true) }
        try {
            val subfolders = folderRepository.getAll().filter { it.parentId == folderId }
            val uris = folderRepository.getFilesInFolder(folderId).toSet()
            val records = historyRepository.getAll().filter { it.uri in uris }
            _state.update {
                it.copy(
                    subfolders = subfolders.map { f -> f.toUiModel() },
                    files = records.map { r -> r.toUiModel() },
                    isLoading = false,
                )
            }
            launchThumbnailGeneration(records)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn { "loadFiles failed: ${e::class.simpleName}" }
            _state.update { it.copy(isLoading = false, errorMessage = "Не удалось загрузить папку") }
        }
    }

    /** Открыть вложенную папку (рекурсивная навигация). */
    fun openSubfolder(
        id: String,
        name: String,
    ) {
        onOpenFolder(id, name)
    }

    /** Открыть диалог создания вложенной папки. */
    fun openCreateFolderDialog() {
        _state.update { it.copy(createFolderDialog = CreateFolderDialogState("", false)) }
    }

    /** Закрыть диалог создания вложенной папки. */
    fun dismissCreateFolderDialog() {
        _state.update { it.copy(createFolderDialog = null) }
    }

    /** Обновить (с фильтрацией) имя в диалоге создания вложенной папки. */
    fun onCreateFolderNameChanged(name: String) {
        val filtered = name.replace(Regex("[^\\p{L}\\p{N}\\-_]"), "").take(255)
        _state.update { s ->
            s.copy(
                createFolderDialog =
                    s.createFolderDialog?.copy(
                        currentName = filtered,
                        isConfirmEnabled = filtered.isNotEmpty(),
                    ),
            )
        }
    }

    /** Создать вложенную папку внутри текущей. */
    fun createSubfolder(name: String) {
        if (isMutatingFolder) return
        isMutatingFolder = true
        _state.update { it.copy(createFolderDialog = null) }
        scope.launch {
            try {
                folderRepository.create(name, parentId = folderId)
                loadFiles()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn { "createSubfolder failed: ${e::class.simpleName}" }
                _state.update { it.copy(errorMessage = "Не удалось создать папку") }
            } finally {
                isMutatingFolder = false
            }
        }
    }

    /** Запросить подтверждение удаления вложенной папки. */
    fun requestDeleteSubfolder(id: String) {
        val name = _state.value.subfolders.firstOrNull { it.id == id }?.name ?: return
        _state.update { it.copy(deleteFolderDialog = DeleteFolderDialogState(id, name)) }
    }

    /** Закрыть диалог удаления вложенной папки. */
    fun dismissDeleteFolderDialog() {
        _state.update { it.copy(deleteFolderDialog = null) }
    }

    /** Удалить вложенную папку (каскадно, вместе со всем содержимым). */
    fun deleteSubfolder(id: String) {
        _state.update { it.copy(deleteFolderDialog = null) }
        scope.launch {
            try {
                folderRepository.delete(id)
                loadFiles()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn { "deleteSubfolder failed: ${e::class.simpleName}" }
                _state.update { it.copy(errorMessage = "Не удалось удалить папку") }
            }
        }
    }

    /** Открыть файл из папки в редакторе. */
    fun openFile(
        uri: String,
        lastPageIndex: Int,
    ) {
        if (isOpening) return
        isOpening = true
        onOpenEditor(uri, lastPageIndex)
        isOpening = false
    }

    /** Запросить системный диалог выбора файла для добавления в папку. */
    fun requestImport() {
        _state.update { it.copy(navigateToFilePicker = true) }
    }

    /** Открыть диалог добавления из недавних: загрузить файлы, которых ещё нет в папке. */
    fun requestAddExisting() {
        scope.launch {
            try {
                val inFolder = folderRepository.getFilesInFolder(folderId).toSet()
                val candidates =
                    historyRepository.getAll()
                        .filter { it.uri !in inFolder }
                        .map { it.toUiModel() }
                _state.update { it.copy(addExistingCandidates = candidates) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn { "requestAddExisting failed: ${e::class.simpleName}" }
                _state.update { it.copy(errorMessage = "Не удалось загрузить недавние") }
            }
        }
    }

    /** Закрыть диалог добавления из недавних. */
    fun dismissAddExisting() {
        _state.update { it.copy(addExistingCandidates = null) }
    }

    /** Привязать выбранные недавние файлы к папке (без открытия редактора). */
    fun addExistingFiles(uris: List<String>) {
        _state.update { it.copy(addExistingCandidates = null) }
        if (uris.isEmpty()) return
        scope.launch {
            uris.forEach { uri ->
                try {
                    folderRepository.addFile(folderId, uri)
                } catch (_: FileDuplicateInFolderException) {
                    // Уже в папке — пропускаем.
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.warn { "addExistingFiles addFile failed: ${e::class.simpleName}" }
                }
            }
            loadFiles()
        }
    }

    /** UI отметил, что диалог выбора файла открыт — сбросить запрос. */
    fun onFilePickerLaunched() {
        _state.update { it.copy(navigateToFilePicker = false) }
    }

    /**
     * Результат выбора файла: добавляет в историю, авто-привязывает к открытой папке и
     * открывает в редакторе. [uri] = null означает отмену.
     */
    fun onFilePicked(
        uri: String?,
        displayName: String,
    ) {
        if (uri == null) return
        scope.launch {
            val result =
                addToHistory.execute(
                    uri = uri,
                    displayName = displayName,
                    fileSize = null,
                    openedAt = nowMillis(),
                    lastPageIndex = 0,
                )
            if (result.isFailure) {
                _state.update { it.copy(errorMessage = "Не удалось добавить файл") }
                return@launch
            }
            try {
                folderRepository.addFile(folderId, uri)
            } catch (_: FileDuplicateInFolderException) {
                // Файл уже в папке — это нормально, продолжаем открытие.
            } catch (e: FileNotInHistoryException) {
                logger.warn { "addFile: file not in history — ${e::class.simpleName}" }
            } catch (e: FolderNotFoundException) {
                logger.warn { "addFile: folder not found — ${e::class.simpleName}" }
            } catch (e: Exception) {
                logger.warn { "addFile failed: ${e::class.simpleName}" }
            }
            loadFiles()
            onOpenEditor(uri, 0)
        }
    }

    /**
     * Внешний(ие) файл(ы) из ОС (Finder/проводник) брошены на открытую папку: добавить каждый
     * в историю и привязать к папке, БЕЗ открытия редактора (в отличие от [onFilePicked]).
     *
     * @param uris Канонические пути брошенных файлов.
     */
    fun addExternalFiles(uris: List<String>) {
        val sanitized = uris.filter { it.isNotBlank() }
        if (sanitized.isEmpty()) return
        scope.launch {
            sanitized.forEach { uri ->
                val displayName = resolveDocumentDisplayName(uri) ?: uri.substringAfterLast('/').ifBlank { uri }
                val result =
                    addToHistory.execute(
                        uri = uri,
                        displayName = displayName,
                        fileSize = null,
                        openedAt = nowMillis(),
                        lastPageIndex = 0,
                    )
                if (result.isFailure) {
                    _state.update { it.copy(errorMessage = "Не удалось добавить файл") }
                    return@forEach
                }
                try {
                    folderRepository.addFile(folderId, uri)
                } catch (_: FileDuplicateInFolderException) {
                    // Файл уже в папке — это нормально.
                } catch (e: FileNotInHistoryException) {
                    logger.warn { "addExternalFiles: file not in history — ${e::class.simpleName}" }
                } catch (e: FolderNotFoundException) {
                    logger.warn { "addExternalFiles: folder not found — ${e::class.simpleName}" }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.warn { "addExternalFiles addFile failed: ${e::class.simpleName}" }
                }
            }
            loadFiles()
        }
    }

    /** Удалить файл из папки (запись истории не удаляется). */
    fun removeFile(uri: String) {
        scope.launch {
            try {
                folderRepository.removeFile(folderId, uri)
            } catch (e: Exception) {
                logger.warn { "removeFile failed: ${e::class.simpleName}" }
            }
            loadFiles()
        }
    }

    /** Сбросить одноразовое сообщение об ошибке после показа в UI. */
    fun onErrorShown() {
        _state.update { it.copy(errorMessage = null) }
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
                            logger.warn(cause) { "Thumbnail generation failed for ${file.id}" }
                            updateThumbnail(file.id, ThumbnailState.Error)
                        },
                    )
                }
            }
        }
    }

    private fun updateThumbnail(
        id: String,
        thumbnailState: ThumbnailState,
    ) {
        _state.update { s ->
            s.copy(files = s.files.map { m -> if (m.id == id) m.copy(thumbnailState = thumbnailState) else m })
        }
    }
}

private fun Folder.toUiModel() =
    FolderUiModel(
        id = id,
        name = name,
        fileCount = 0,
        createdAt = createdAt,
        lastFileOpenedAt = null,
    )

private fun RecentFile.toUiModel() =
    RecentFileUiModel(
        id = id,
        uri = uri,
        displayName = displayName,
        openedAt = openedAt,
        availabilityStatus = availabilityStatus,
        thumbnailState = ThumbnailState.Loading,
        lastPageIndex = lastPageIndex,
    )
