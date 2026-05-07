---
genre: how-to
title: "Stage 03: ViewModel + UiState + FilePicker expect (common)"
topic: main-screen
module: common
stage: 03
feature: main_screen_redesign
---

# Stage 03: ViewModel + UiState + FilePicker expect (`:common`)

**Модуль:** `:app:byCompose:common`  
**Sourceset:** `commonMain`  
**Статус:** TODO  
**Зависит от:** Stage 01, Stage 02

---

## Цель

Реализовать `MainScreenViewModel` с полным MVI-циклом (Intent → State → Effect) и объявить `FilePicker` expect. Никакой UI здесь нет — только логика перехода состояний.

---

## Пакетная структура

```
common/src/commonMain/kotlin/ru/kyamshanov/notepen/mainscreen/
  ui/
    model/
      MainScreenUiState.kt
      RecentFileUiModel.kt
      FolderUiModel.kt
      ThumbnailState.kt
      NavigationTarget.kt
      ErrorEvent.kt
      DialogState.kt
    viewmodel/
      MainScreenViewModel.kt
    MainScreenIntent.kt
  platform/
    FilePicker.kt   ← expect class
```

---

## Шаг 1: UiState и модели

### `NavigationTarget.kt`

```kotlin
package ru.kyamshanov.notepen.mainscreen.ui.model

sealed class NavigationTarget {
    data class Editor(val uri: String, val lastPageIndex: Int) : NavigationTarget()
    object FilePicker : NavigationTarget()
}
```

### `ThumbnailState.kt`

```kotlin
sealed class ThumbnailState {
    object Loading : ThumbnailState()
    data class Ready(val imageData: ByteArray) : ThumbnailState() {
        override fun equals(other: Any?): Boolean =
            other is Ready && imageData.contentEquals(other.imageData)
        override fun hashCode(): Int = imageData.contentHashCode()
    }
    object Error : ThumbnailState()
}
```

### `RecentFileUiModel.kt`

```kotlin
data class RecentFileUiModel(
    val id: String,
    val displayName: String,
    val openedAt: Long,
    val availabilityStatus: AvailabilityStatus,
    val thumbnailState: ThumbnailState,
    val lastPageIndex: Int,
)
```

### `FolderUiModel.kt`

```kotlin
data class FolderUiModel(
    val id: String,
    val name: String,
    val fileCount: Int,
    val createdAt: Long,
    val lastFileOpenedAt: Long?,
)
```

### `ErrorEvent.kt`

```kotlin
sealed class ErrorEvent {
    object FileNotFound : ErrorEvent()
    object FileError : ErrorEvent()
    object HistoryFlushFailed : ErrorEvent()
    object ThumbnailGenerationFailed : ErrorEvent()
    object FolderLimitExceeded : ErrorEvent()
    object FolderNameCharsInvalid : ErrorEvent()
    object FolderOperationFailed : ErrorEvent()
}
```

### `DialogState.kt`

```kotlin
data class SafMergeDialogState(
    val existingRecord: RecentFileUiModel,
    val newUri: String,
)

data class CreateFolderDialogState(
    val currentName: String,
    val isConfirmEnabled: Boolean,
)

data class DeleteFolderDialogState(
    val folderId: String,
    val folderName: String,
)
```

### `MainScreenUiState.kt`

```kotlin
data class MainScreenUiState(
    val recentFiles: List<RecentFileUiModel> = emptyList(),
    val folders: List<FolderUiModel> = emptyList(),
    val isLoading: Boolean = true,
    val navigationTarget: NavigationTarget? = null,
    val safMergeDialog: SafMergeDialogState? = null,
    val createFolderDialog: CreateFolderDialogState? = null,
    val deleteFolderDialog: DeleteFolderDialogState? = null,
    val errorEvent: ErrorEvent? = null,
)
```

---

## Шаг 2: Intent

### `MainScreenIntent.kt`

```kotlin
sealed class MainScreenIntent {
    object OpenFilePicker : MainScreenIntent()
    data class OpenRecentFile(val id: String) : MainScreenIntent()
    object ScreenVisible : MainScreenIntent()
    object CancelNavigation : MainScreenIntent()
    data class MergeSafRecords(val keepId: String, val discardId: String, val newUri: String) : MainScreenIntent()
    data class RejectSafMerge(val existingId: String, val newUri: String) : MainScreenIntent()
    data class CreateFolder(val name: String) : MainScreenIntent()
    data class DeleteFolder(val id: String) : MainScreenIntent()
    data class RequestDeleteFolder(val id: String) : MainScreenIntent()
    data class AddFileToFolder(val folderId: String, val fileUri: String) : MainScreenIntent()
    data class RemoveFileFromFolder(val folderId: String, val uri: String) : MainScreenIntent()
    data class RenameFolder(val id: String, val newName: String) : MainScreenIntent()
    object DismissCreateFolderDialog : MainScreenIntent()
    object DismissDeleteFolderDialog : MainScreenIntent()
    data class FolderDialogNameChanged(val name: String) : MainScreenIntent()
}
```

---

## Шаг 3: `MainScreenViewModel`

Файл: `common/src/commonMain/kotlin/ru/kyamshanov/notepen/mainscreen/ui/viewmodel/MainScreenViewModel.kt`

```kotlin
package ru.kyamshanov.notepen.mainscreen.ui.viewmodel

import com.arkivanov.essenty.lifecycle.Lifecycle
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import ru.kyamshanov.notepen.mainscreen.domain.exception.*
import ru.kyamshanov.notepen.mainscreen.domain.model.*
import ru.kyamshanov.notepen.mainscreen.domain.port.*
import ru.kyamshanov.notepen.mainscreen.domain.usecase.*
import ru.kyamshanov.notepen.mainscreen.ui.model.*

class MainScreenViewModel(
    lifecycle: Lifecycle,
    private val historyRepository: FileHistoryRepository,
    private val folderRepository: FolderRepository,
    private val addToHistory: AddToHistoryUseCase,
    private val checkAvailability: CheckAvailabilityUseCase,
    private val openRecentFile: OpenRecentFileUseCase,
    private val thumbnailRepository: ThumbnailRepository,
    private val thumbnailGenerator: PdfThumbnailGenerator,
) {
    private val scope = lifecycle.coroutineScope(Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(MainScreenUiState())
    val state: StateFlow<MainScreenUiState> = _state.asStateFlow()

    // Защита от double-tap (CC-7)
    private var isNavigating = false

    // Semaphore для ограничения параллельной генерации миниатюр (CC-13)
    private val thumbnailSemaphore = Semaphore(4)

    fun onIntent(intent: MainScreenIntent) {
        scope.launch { handleIntent(intent) }
    }

    private suspend fun handleIntent(intent: MainScreenIntent) {
        when (intent) {
            is MainScreenIntent.ScreenVisible -> loadInitialData()
            is MainScreenIntent.OpenFilePicker -> openFilePicker()
            is MainScreenIntent.OpenRecentFile -> openRecentFile(intent.id)
            is MainScreenIntent.CancelNavigation -> cancelNavigation()
            is MainScreenIntent.MergeSafRecords -> mergeSafRecords(intent)
            is MainScreenIntent.RejectSafMerge -> rejectSafMerge(intent)
            is MainScreenIntent.CreateFolder -> createFolder(intent.name)
            is MainScreenIntent.DeleteFolder -> deleteFolder(intent.id)
            is MainScreenIntent.RequestDeleteFolder -> requestDeleteFolder(intent.id)
            is MainScreenIntent.AddFileToFolder -> addFileToFolder(intent.folderId, intent.fileUri)
            is MainScreenIntent.RemoveFileFromFolder -> removeFileFromFolder(intent.folderId, intent.uri)
            is MainScreenIntent.RenameFolder -> renameFolder(intent.id, intent.newName)
            is MainScreenIntent.DismissCreateFolderDialog -> _state.update { it.copy(createFolderDialog = null) }
            is MainScreenIntent.DismissDeleteFolderDialog -> _state.update { it.copy(deleteFolderDialog = null) }
            is MainScreenIntent.FolderDialogNameChanged -> updateFolderDialogName(intent.name)
        }
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
        } catch (_: Exception) {
            _state.update { it.copy(isLoading = false) }
        }
    }

    private fun launchAvailabilityCheck(files: List<RecentFile>) {
        scope.launch {
            checkAvailability.execute(files).collect { update ->
                _state.update { state ->
                    state.copy(recentFiles = state.recentFiles.map { model ->
                        if (model.id == update.id) model.copy(availabilityStatus = update.status)
                        else model
                    })
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
                        onFailure = { updateThumbnail(file.id, ThumbnailState.Error) },
                    )
                }
            }
        }
    }

    private fun updateThumbnail(id: String, state: ThumbnailState) {
        _state.update { s ->
            s.copy(recentFiles = s.recentFiles.map { m ->
                if (m.id == id) m.copy(thumbnailState = state) else m
            })
        }
    }

    private suspend fun openFilePicker() {
        _state.update { it.copy(navigationTarget = NavigationTarget.FilePicker) }
    }

    private suspend fun openRecentFile(id: String) {
        if (isNavigating) return  // debounce CC-7
        val record = _state.value.recentFiles.firstOrNull { it.id == id } ?: return
        isNavigating = true
        val uri = historyRepository.getAll().firstOrNull { it.id == id }?.uri ?: run {
            isNavigating = false; return
        }
        when (val result = openRecentFile.execute(uri)) {
            is OpenFileResult.Success -> {
                try {
                    val fullRecord = historyRepository.getAll().first { it.uri == uri }
                    val upsertResult = addToHistory.execute(
                        uri = uri,
                        displayName = fullRecord.displayName,
                        fileSize = fullRecord.fileSize,
                        openedAt = System.currentTimeMillis(),
                        lastPageIndex = fullRecord.lastPageIndex,
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
                            if (m.id == id) m.copy(availabilityStatus = result.status) else m
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
            try { historyRepository.rollbackUpsert(target.uri) } catch (_: Exception) {}
        }
        _state.update { it.copy(navigationTarget = null) }
        isNavigating = false
    }

    private suspend fun mergeSafRecords(intent: MainScreenIntent.MergeSafRecords) {
        // AC-5b merge: обновить URI существующей записи, удалить старую
        // Полная реализация в @CodeWriter
        _state.update { it.copy(safMergeDialog = null) }
    }

    private suspend fun rejectSafMerge(intent: MainScreenIntent.RejectSafMerge) {
        // CC-1: старая запись помечается FILE_ERROR, новая добавляется отдельно
        _state.update { it.copy(safMergeDialog = null) }
    }

    private suspend fun createFolder(name: String) {
        _state.update { it.copy(createFolderDialog = null) }
        try {
            val folder = folderRepository.create(name)
            _state.update { s ->
                s.copy(folders = (s.folders + folder.toUiModel(0)).sortedByDescending { it.lastFileOpenedAt ?: 0 })
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

    private suspend fun addFileToFolder(folderId: String, fileUri: String) {
        try {
            folderRepository.addFile(folderId, fileUri)
            refreshFolderFileCount(folderId)
        } catch (_: Exception) {
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
                s.copy(folders = s.folders.map { f -> if (f.id == id) f.copy(name = newName) else f })
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
            s.copy(createFolderDialog = s.createFolderDialog?.copy(
                currentName = filtered,
                isConfirmEnabled = filtered.isNotEmpty(),
            ))
        }
    }

    private suspend fun refreshFolderFileCount(folderId: String) {
        val count = try { folderRepository.getFilesInFolder(folderId).size } catch (_: Exception) { return }
        _state.update { s ->
            s.copy(folders = s.folders.map { f -> if (f.id == folderId) f.copy(fileCount = count) else f })
        }
    }

    private suspend fun getFolderFileCount(folderId: String): Int =
        try { folderRepository.getFilesInFolder(folderId).size } catch (_: Exception) { 0 }

    fun onNavigationHandled() {
        _state.update { it.copy(navigationTarget = null) }
        isNavigating = false
    }

    fun onErrorEventHandled() {
        _state.update { it.copy(errorEvent = null) }
    }

    fun openCreateFolderDialog() {
        _state.update { it.copy(createFolderDialog = CreateFolderDialogState("", false)) }
    }
}

// Mapper extensions
private fun RecentFile.toUiModel() = RecentFileUiModel(
    id = id, displayName = displayName, openedAt = openedAt,
    availabilityStatus = availabilityStatus,
    thumbnailState = ThumbnailState.Loading,
    lastPageIndex = lastPageIndex,
)

private fun Folder.toUiModel(fileCount: Int) = FolderUiModel(
    id = id, name = name, fileCount = fileCount,
    createdAt = createdAt, lastFileOpenedAt = null,
)
```

---

## Шаг 4: FilePicker expect

Файл: `common/src/commonMain/kotlin/ru/kyamshanov/notepen/mainscreen/platform/FilePicker.kt`

```kotlin
package ru.kyamshanov.notepen.mainscreen.platform

expect class FilePicker {
    suspend fun pickPdfFile(): String?
}
```

---

## Тесты Stage 03

Файл: `common/src/commonTest/kotlin/ru/kyamshanov/notepen/mainscreen/`

| Тест | CC / AC |
|------|---------|
| `ScreenVisible` → `isLoading = false` после загрузки | AC-1 |
| `OpenRecentFile` дважды быстро → второй игнорируется | **CC-7 High** |
| `OpenRecentFile` → SUCCESS → `navigationTarget = Editor(uri, lastPageIndex)` | AC-10, AC-57 |
| `OpenRecentFile` → NOT_FOUND → `errorEvent = FileNotFound`, `navigationTarget = null` | AC-11 |
| `CancelNavigation` → `rollbackUpsert` вызван, `navigationTarget = null` | **CC-6 High** |
| `FolderDialogNameChanged` с недопустимыми символами → фильтруются | AC-48 |
| `FolderDialogNameChanged` только пробелы → `isConfirmEnabled = false` | AC-32 |
| `CreateFolder` → `FolderLimitExceededException` → `errorEvent = FolderLimitExceeded` | AC-41 |
| `errorEvent` сбрасывается через `onErrorEventHandled()` | — |

**ACT-NOW — риск R6:** настроить `Dispatchers.setMain(UnconfinedTestDispatcher())` в `beforeTest`.

---

## Acceptance Criteria, закрываемые этим этапом

AC-1, AC-10, AC-11, AC-14, AC-18, AC-32, AC-35, AC-36, AC-41, AC-48, AC-57, AC-58, CC-6, CC-7.

---

## Контрольные точки Stage 03

- [ ] `./gradlew :app:byCompose:common:compileKotlin` — зелёный
- [ ] `./gradlew :app:byCompose:common:test` — все тесты Stage 03 пройдены
- [ ] `./gradlew detekt ktlintCheck` — без ошибок
