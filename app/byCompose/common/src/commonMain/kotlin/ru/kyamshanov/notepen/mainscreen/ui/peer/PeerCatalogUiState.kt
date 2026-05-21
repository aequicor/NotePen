package ru.kyamshanov.notepen.mainscreen.ui.peer

import ru.kyamshanov.notepen.mainscreen.ui.model.RemoteEntryUiModel
import ru.kyamshanov.notepen.mainscreen.ui.model.RemoteFolderUiModel

/**
 * State sub-экрана каталога одного пира.
 *
 * @property peerName Заголовок (имя пира; берётся из hostName каталога, fallback на DeviceInfo.name).
 * @property entries Recent-файлы пира.
 * @property folders Папки пира.
 * @property isDisconnected true, если пир пропал из подключённых — каталог последний известный.
 * @property errorMessage Одноразовое сообщение об ошибке открытия документа.
 * @property currentFolderId id папки, внутрь которой пользователь вошёл, или null —
 *           тогда показывается корень каталога (недавние + список папок).
 * @property currentFolderName имя текущей папки для подзаголовка, когда [currentFolderId] != null.
 */
data class PeerCatalogUiState(
    val peerName: String = "",
    val entries: List<RemoteEntryUiModel> = emptyList(),
    val folders: List<RemoteFolderUiModel> = emptyList(),
    val isDisconnected: Boolean = false,
    val errorMessage: String? = null,
    val currentFolderId: String? = null,
    val currentFolderName: String? = null,
)
