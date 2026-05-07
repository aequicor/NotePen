package ru.kyamshanov.notepen.mainscreen.ui.screen

import com.arkivanov.decompose.ComponentContext
import ru.kyamshanov.notepen.MainComponent
import ru.kyamshanov.notepen.mainscreen.domain.port.FileHistoryRepository
import ru.kyamshanov.notepen.mainscreen.domain.port.FolderRepository
import ru.kyamshanov.notepen.mainscreen.domain.port.PdfThumbnailGenerator
import ru.kyamshanov.notepen.mainscreen.domain.port.ThumbnailRepository
import ru.kyamshanov.notepen.mainscreen.domain.usecase.AddToHistoryUseCase
import ru.kyamshanov.notepen.mainscreen.domain.usecase.CheckAvailabilityUseCase
import ru.kyamshanov.notepen.mainscreen.domain.usecase.OpenRecentFileUseCase
import ru.kyamshanov.notepen.mainscreen.ui.viewmodel.MainScreenViewModel

/**
 * Decompose-компонент главного экрана.
 *
 * Хостирует [MainScreenViewModel] и связывает его с жизненным циклом Decompose.
 *
 * @param componentContext Контекст компонента Decompose.
 * @param historyRepository Порт для чтения/записи истории файлов.
 * @param folderRepository Порт для управления папками.
 * @param addToHistory UseCase добавления/обновления записи истории.
 * @param checkAvailability UseCase параллельной проверки доступности файлов.
 * @param openRecentFileUseCase UseCase синхронной проверки и открытия файла.
 * @param thumbnailRepository Порт кеша миниатюр.
 * @param thumbnailGenerator Порт генерации миниатюр.
 * @param onOpenEditor Обратный вызов при открытии редактора (URI файла, индекс страницы).
 * @param onOpenFilePicker Обратный вызов для открытия системного файлового диалога.
 */
class MainScreenComponent(
    componentContext: ComponentContext,
    private val historyRepository: FileHistoryRepository,
    private val folderRepository: FolderRepository,
    private val addToHistory: AddToHistoryUseCase,
    private val checkAvailability: CheckAvailabilityUseCase,
    private val openRecentFileUseCase: OpenRecentFileUseCase,
    private val thumbnailRepository: ThumbnailRepository,
    private val thumbnailGenerator: PdfThumbnailGenerator,
    val onOpenEditor: (uri: String, lastPageIndex: Int) -> Unit,
    val onOpenFilePicker: () -> Unit,
) : MainComponent, ComponentContext by componentContext {

    /** ViewModel главного экрана, привязанная к жизненному циклу компонента. */
    val viewModel = MainScreenViewModel(
        lifecycle = lifecycle,
        historyRepository = historyRepository,
        folderRepository = folderRepository,
        addToHistory = addToHistory,
        checkAvailability = checkAvailability,
        openRecentFile = openRecentFileUseCase,
        thumbnailRepository = thumbnailRepository,
        thumbnailGenerator = thumbnailGenerator,
    )
}
