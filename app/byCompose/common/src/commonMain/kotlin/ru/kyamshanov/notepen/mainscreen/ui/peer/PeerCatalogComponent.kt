package ru.kyamshanov.notepen.mainscreen.ui.peer

import com.arkivanov.decompose.ComponentContext
import kotlinx.coroutines.flow.Flow
import ru.kyamshanov.notepen.PeerCatalogComponent
import ru.kyamshanov.notepen.sync.domain.RemoteDocumentOpener
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.RemoteCatalog

/**
 * Decompose-компонент sub-экрана каталога одного пира.
 *
 * @param componentContext Контекст Decompose.
 * @param peerId Стабильный id пира.
 * @param displayName Имя пира (используется как fallback заголовка до получения каталога).
 * @param catalogsFlow Общая карта «пир → каталог».
 * @param remoteDocumentOpener Опеннер документов (или null на host-стороне).
 * @param onBack Колбэк возврата на главный экран.
 * @param onOpenEditor Колбэк перехода в редактор после успешной загрузки документа.
 */
class PeerCatalogComponentImpl(
    componentContext: ComponentContext,
    peerId: String,
    displayName: String,
    catalogsFlow: Flow<Map<DeviceInfo, RemoteCatalog>>,
    onlinePeerIdsFlow: Flow<Set<String>>?,
    remoteDocumentOpener: RemoteDocumentOpener?,
    receivedPdfDir: String?,
    val onBack: () -> Unit,
    onOpenEditor: (uri: String, lastPageIndex: Int) -> Unit,
) : PeerCatalogComponent, ComponentContext by componentContext {

    val viewModel = PeerCatalogViewModel(
        lifecycle = lifecycle,
        peerId = peerId,
        fallbackName = displayName,
        catalogsFlow = catalogsFlow,
        onlinePeerIdsFlow = onlinePeerIdsFlow,
        remoteDocumentOpener = remoteDocumentOpener,
        receivedPdfDir = receivedPdfDir,
        onDocumentReady = onOpenEditor,
    )
}
