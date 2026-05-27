package ru.kyamshanov.notepen.mainscreen.ui.folder

import com.arkivanov.decompose.ComponentContext
import ru.kyamshanov.notepen.FolderComponent
import ru.kyamshanov.notepen.mainscreen.domain.port.FileHistoryRepository
import ru.kyamshanov.notepen.mainscreen.domain.port.FolderRepository
import ru.kyamshanov.notepen.mainscreen.domain.port.PdfThumbnailGenerator
import ru.kyamshanov.notepen.mainscreen.domain.port.ThumbnailRepository
import ru.kyamshanov.notepen.mainscreen.domain.usecase.AddToHistoryUseCase

/**
 * Decompose-компонент sub-экрана содержимого папки.
 *
 * @param componentContext Контекст Decompose.
 * @param folderId UUID открытой папки.
 * @param folderName Имя папки (для заголовка).
 * @param onBack Колбэк возврата на главный экран.
 * @param onOpenEditor Колбэк перехода в редактор.
 * @param onOpenFolder Колбэк навигации во вложенную папку (sub-экран её содержимого).
 * @param onOpenFilePicker Суспендирующий колбэк открытия системного диалога выбора файла.
 */
class FolderContentsComponentImpl(
    componentContext: ComponentContext,
    folderId: String,
    folderName: String,
    historyRepository: FileHistoryRepository,
    folderRepository: FolderRepository,
    addToHistory: AddToHistoryUseCase,
    thumbnailRepository: ThumbnailRepository,
    thumbnailGenerator: PdfThumbnailGenerator,
    val onBack: () -> Unit,
    val onOpenFilePicker: suspend () -> String?,
    onOpenEditor: (uri: String, lastPageIndex: Int) -> Unit,
    onOpenFolder: (folderId: String, folderName: String) -> Unit,
) : FolderComponent,
    ComponentContext by componentContext {
    val viewModel =
        FolderContentsViewModel(
            lifecycle = lifecycle,
            folderId = folderId,
            folderName = folderName,
            historyRepository = historyRepository,
            folderRepository = folderRepository,
            addToHistory = addToHistory,
            thumbnailRepository = thumbnailRepository,
            thumbnailGenerator = thumbnailGenerator,
            onOpenEditor = onOpenEditor,
            onOpenFolder = onOpenFolder,
        )
}
