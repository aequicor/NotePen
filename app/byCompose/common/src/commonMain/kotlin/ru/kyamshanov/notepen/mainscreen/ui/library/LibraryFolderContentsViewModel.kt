package ru.kyamshanov.notepen.mainscreen.ui.library

import com.arkivanov.essenty.lifecycle.Lifecycle
import com.arkivanov.essenty.lifecycle.coroutines.withLifecycle
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.library.api.Library
import ru.kyamshanov.notepen.library.api.LibraryBookId
import ru.kyamshanov.notepen.library.api.LibraryEntry
import ru.kyamshanov.notepen.library.api.LibraryRegistry
import ru.kyamshanov.notepen.mainscreen.ui.model.LibraryContentItemUiModel

/**
 * UI-состояние sub-экрана содержимого конкретной библиотеки.
 *
 * @property title Имя библиотеки (заголовок экрана).
 * @property items Книги библиотеки (обновляются реактивно).
 * @property isLoading Библиотека ещё не разрешена в реестре (например, подключается на старте).
 */
data class LibraryFolderContentsUiState(
    val title: String = "Библиотека",
    val items: List<LibraryContentItemUiModel> = emptyList(),
    val isLoading: Boolean = true,
)

/**
 * ViewModel sub-экрана содержимого конкретной библиотеки.
 *
 * Резолвит [Library] из [LibraryRegistry.libraries] по [libraryId] реактивно (сохранённые
 * локальные библиотеки подключаются асинхронно на старте — уже после навигации), проксирует её
 * `books` в UI и открывает книгу через [Library.open]. Пустой [libraryId] (восстановление
 * устаревшего back-stack после смерти процесса) трактуется как «нечего показывать» — без
 * бесконечной загрузки.
 */
class LibraryFolderContentsViewModel(
    lifecycle: Lifecycle,
    private val libraryId: String,
    private val libraryRegistry: LibraryRegistry,
    private val onOpenEditor: (uri: String, lastPageIndex: Int) -> Unit,
) {
    private val logger = KotlinLogging.logger {}

    private val scope = CoroutineScope(Dispatchers.Main.immediate).withLifecycle(lifecycle)

    private val _state = MutableStateFlow(LibraryFolderContentsUiState())
    val state: StateFlow<LibraryFolderContentsUiState> = _state.asStateFlow()

    private var isOpening = false

    init {
        @OptIn(ExperimentalCoroutinesApi::class)
        scope.launch {
            libraryRegistry.libraries
                .flatMapLatest { libs ->
                    val library = libs.firstOrNull { it.descriptor.id.value == libraryId }
                    if (library != null) {
                        library.books.map { books -> resolvedState(library, books) }
                    } else {
                        // Не разрешено: пустой id (устаревший стек) → терминальное пустое
                        // состояние; непустой id → ждём, пока библиотека подключится (loading).
                        flowOf(LibraryFolderContentsUiState(isLoading = libraryId.isNotBlank()))
                    }
                }
                .collect { newState -> _state.value = newState }
        }
    }

    private fun resolvedState(
        library: Library,
        books: List<LibraryEntry>,
    ): LibraryFolderContentsUiState =
        LibraryFolderContentsUiState(
            title = library.descriptor.displayName,
            items = books.map(::toUiModel),
            isLoading = false,
        )

    /** Открыть книгу библиотеки в редакторе: материализует локально через [Library.open]. */
    fun openItem(itemId: String) {
        if (isOpening) return
        val library =
            libraryRegistry.libraries.value.firstOrNull { it.descriptor.id.value == libraryId } ?: return
        isOpening = true
        scope.launch {
            library.open(LibraryBookId(itemId)).fold(
                onSuccess = { doc ->
                    onOpenEditor(doc.localPath, 0)
                    isOpening = false
                },
                onFailure = { e ->
                    logger.warn(e) { "openItem failed: ${e::class.simpleName}" }
                    isOpening = false
                },
            )
        }
    }
}

private fun toUiModel(entry: LibraryEntry) =
    LibraryContentItemUiModel(
        id = entry.libraryBookId.value,
        displayName = entry.displayName,
        sizeBytes = entry.sizeBytes,
        modifiedAt = entry.modifiedAt,
    )
