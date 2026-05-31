package ru.kyamshanov.notepen.mainscreen.ui.library

import com.arkivanov.decompose.ComponentContext
import ru.kyamshanov.notepen.LibraryFolderComponent
import ru.kyamshanov.notepen.library.api.LibraryRegistry

/**
 * Decompose-компонент sub-экрана содержимого конкретной библиотеки.
 *
 * @param componentContext Контекст Decompose.
 * @param libraryId Идентификатор библиотеки, чьё содержимое показать.
 * @param libraryRegistry Реестр библиотек — источник книг и операция открытия ([LibraryRegistry]).
 * @param onBack Возврат на главный экран.
 * @param onOpenEditor Колбэк перехода в редактор для выбранной книги.
 */
class LibraryFolderContentsComponentImpl(
    componentContext: ComponentContext,
    libraryId: String,
    libraryRegistry: LibraryRegistry,
    val onBack: () -> Unit,
    onOpenEditor: (uri: String, lastPageIndex: Int) -> Unit,
) : LibraryFolderComponent,
    ComponentContext by componentContext {
    val viewModel =
        LibraryFolderContentsViewModel(
            lifecycle = lifecycle,
            libraryId = libraryId,
            libraryRegistry = libraryRegistry,
            onOpenEditor = onOpenEditor,
        )
}
