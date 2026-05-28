package ru.kyamshanov.notepen.mainscreen.ui.library

import com.arkivanov.decompose.ComponentContext
import ru.kyamshanov.notepen.LibraryFolderComponent
import ru.kyamshanov.notepen.mainscreen.domain.port.LibraryFolder

/**
 * Decompose-компонент sub-экрана общей папки «Библиотека».
 *
 * @param componentContext Контекст Decompose.
 * @param libraryFolder Источник списка книг.
 * @param onBack Возврат на главный экран.
 * @param onOpenEditor Колбэк перехода в редактор для выбранной книги.
 */
class LibraryFolderContentsComponentImpl(
    componentContext: ComponentContext,
    libraryFolder: LibraryFolder,
    val onBack: () -> Unit,
    onOpenEditor: (uri: String, lastPageIndex: Int) -> Unit,
) : LibraryFolderComponent,
    ComponentContext by componentContext {
    val viewModel =
        LibraryFolderContentsViewModel(
            lifecycle = lifecycle,
            libraryFolder = libraryFolder,
            onOpenEditor = onOpenEditor,
        )
}
