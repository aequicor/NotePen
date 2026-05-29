package ru.kyamshanov.notepen.mainscreen.ui.screen

import com.arkivanov.decompose.ComponentContext
import kotlinx.coroutines.flow.Flow
import ru.kyamshanov.notepen.MainComponent
import ru.kyamshanov.notepen.library.api.LibraryRegistry
import ru.kyamshanov.notepen.mainscreen.domain.port.FileHistoryRepository
import ru.kyamshanov.notepen.mainscreen.domain.port.FolderRepository
import ru.kyamshanov.notepen.mainscreen.domain.port.PdfThumbnailGenerator
import ru.kyamshanov.notepen.mainscreen.domain.port.ThumbnailRepository
import ru.kyamshanov.notepen.mainscreen.domain.usecase.AddToHistoryUseCase
import ru.kyamshanov.notepen.mainscreen.domain.usecase.CheckAvailabilityUseCase
import ru.kyamshanov.notepen.mainscreen.domain.usecase.OpenRecentFileUseCase
import ru.kyamshanov.notepen.mainscreen.ui.viewmodel.MainScreenViewModel
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.RemoteCatalog

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
 * @param onOpenPeerCatalog Колбэк навигации в каталог выбранного пира (peerId, displayName).
 * @param remoteCatalogsFlow Поток карты «пир → его каталог». На клиенте это хосты,
 *        на хосте — клиенты. Null отключает секцию «Подключённые устройства».
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
    val onOpenPeerCatalog: (peerId: String, displayName: String) -> Unit,
    val onOpenFolder: (folderId: String, folderName: String) -> Unit,
    /** Колбэк навигации на экран глобальных настроек приложения. */
    val onOpenSettings: () -> Unit,
    /**
     * Колбэк навигации на sub-экран общей папки «Библиотека». `null` —
     * фича не подключена (Android), карточка «Библиотека» в этом случае не показывается.
     */
    val onOpenLibraryFolder: (() -> Unit)? = null,
    private val remoteCatalogsFlow: Flow<Map<DeviceInfo, RemoteCatalog>>? = null,
    /** Поток `peerId`-ов, считающихся «в сети» — см. [MainScreenViewModel.onlinePeerIdsFlow]. */
    private val onlinePeerIdsFlow: Flow<Set<String>>? = null,
    /**
     * Реестр библиотек, питающий секцию «Библиотека» (`mergedBooks`).
     * `null` — на платформе нет локальной библиотеки (Android), секция скрыта.
     */
    private val libraryRegistry: LibraryRegistry? = null,
) : MainComponent,
    ComponentContext by componentContext {
    /** ViewModel главного экрана, привязанная к жизненному циклу компонента. */
    val viewModel =
        MainScreenViewModel(
            lifecycle = lifecycle,
            historyRepository = historyRepository,
            folderRepository = folderRepository,
            addToHistory = addToHistory,
            checkAvailability = checkAvailability,
            openRecentFile = openRecentFileUseCase,
            thumbnailRepository = thumbnailRepository,
            thumbnailGenerator = thumbnailGenerator,
            remoteCatalogsFlow = remoteCatalogsFlow,
            onlinePeerIdsFlow = onlinePeerIdsFlow,
            libraryRegistry = libraryRegistry,
        )
}
