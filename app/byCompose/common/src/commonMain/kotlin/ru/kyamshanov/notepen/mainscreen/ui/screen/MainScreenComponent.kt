package ru.kyamshanov.notepen.mainscreen.ui.screen

import com.arkivanov.decompose.ComponentContext
import kotlinx.coroutines.flow.Flow
import ru.kyamshanov.notepen.MainComponent
import ru.kyamshanov.notepen.mainscreen.domain.port.FileHistoryRepository
import ru.kyamshanov.notepen.mainscreen.domain.port.FolderRepository
import ru.kyamshanov.notepen.mainscreen.domain.port.PdfThumbnailGenerator
import ru.kyamshanov.notepen.mainscreen.domain.port.ThumbnailRepository
import ru.kyamshanov.notepen.mainscreen.domain.usecase.AddToHistoryUseCase
import ru.kyamshanov.notepen.mainscreen.domain.usecase.CheckAvailabilityUseCase
import ru.kyamshanov.notepen.mainscreen.domain.usecase.OpenRecentFileUseCase
import ru.kyamshanov.notepen.mainscreen.ui.viewmodel.MainScreenViewModel
import ru.kyamshanov.notepen.sync.domain.RemoteDocumentOpener
import ru.kyamshanov.notepen.sync.domain.model.RemoteCatalog
import ru.kyamshanov.notepen.sync.domain.model.SyncStatus

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
 * @param onOpenFilePicker Суспендирующий обратный вызов для открытия системного файлового диалога.
 *        Возвращает нормализованный путь к выбранному файлу или null при отмене.
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
    val onOpenFilePicker: suspend () -> String?,
    /** Optional tablet-side stream of the host's current catalog; null on the host. */
    private val remoteCatalogFlow: Flow<RemoteCatalog?>? = null,
    /** Optional tablet-side opener for documents from the host's library; null on the host. */
    private val remoteDocumentOpener: RemoteDocumentOpener? = null,
    /**
     * Optional stream of `documentId → pendingCount` from the offline buffer.
     * Drives the "не синхронизировано (N)" badge on Remote tiles.
     */
    private val pendingDeltaCounts: Flow<Map<String, Int>>? = null,
    /**
     * Optional stream of `documentId → SyncStatus`. Drives the
     * "удалён на хосте" badge.
     */
    private val remoteDocumentStatuses: Flow<Map<String, SyncStatus>>? = null,
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
        remoteCatalogFlow = remoteCatalogFlow,
        remoteDocumentOpener = remoteDocumentOpener,
        pendingDeltaCounts = pendingDeltaCounts,
        remoteDocumentStatuses = remoteDocumentStatuses,
    )
}
