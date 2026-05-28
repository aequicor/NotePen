package ru.kyamshanov.notepen.mainscreen.ui.library

import com.arkivanov.essenty.lifecycle.Lifecycle
import com.arkivanov.essenty.lifecycle.coroutines.withLifecycle
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.mainscreen.domain.port.LibraryFolder
import ru.kyamshanov.notepen.mainscreen.domain.port.LibraryFolderItem
import ru.kyamshanov.notepen.mainscreen.ui.model.LibraryShelfUiModel

/**
 * UI-состояние sub-экрана общей папки «Библиотека».
 *
 * @property items Книги, лежащие в библиотечной папке (обновляются реактивно).
 * @property isLoading Идёт первичная загрузка содержимого.
 */
data class LibraryFolderContentsUiState(
    val items: List<LibraryShelfUiModel> = emptyList(),
    val isLoading: Boolean = true,
)

/**
 * ViewModel sub-экрана содержимого общей папки «Библиотека».
 *
 * Подписывается на [LibraryFolder.items] и проксирует список в UI. Клик
 * по книге уходит в редактор через [onOpenEditor]. Перенос книг внутрь
 * библиотеки (drag/drop, AddToLibrary) делает главный экран — здесь
 * только просмотр и открытие.
 */
class LibraryFolderContentsViewModel(
    lifecycle: Lifecycle,
    private val libraryFolder: LibraryFolder,
    private val onOpenEditor: (uri: String, lastPageIndex: Int) -> Unit,
) {
    private val logger = KotlinLogging.logger {}

    private val scope = CoroutineScope(Dispatchers.Main.immediate).withLifecycle(lifecycle)

    private val _state = MutableStateFlow(LibraryFolderContentsUiState())
    val state: StateFlow<LibraryFolderContentsUiState> = _state.asStateFlow()

    private var isOpening = false

    init {
        scope.launch {
            libraryFolder.items.collect { items ->
                _state.update {
                    it.copy(items = items.map(LibraryFolderItem::toUiModel), isLoading = false)
                }
            }
        }
    }

    /** Открыть книгу из библиотеки в редакторе. */
    fun openItem(itemId: String) {
        if (isOpening) return
        val item = _state.value.items.firstOrNull { it.id == itemId } ?: return
        isOpening = true
        onOpenEditor(item.uri, 0)
        isOpening = false
    }
}

private fun LibraryFolderItem.toUiModel() =
    LibraryShelfUiModel(
        id = id,
        uri = uri,
        displayName = displayName,
        sizeBytes = sizeBytes,
        modifiedAt = modifiedAt,
    )
