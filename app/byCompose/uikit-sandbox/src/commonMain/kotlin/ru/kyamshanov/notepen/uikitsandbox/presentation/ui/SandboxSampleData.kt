package ru.kyamshanov.notepen.uikitsandbox.presentation.ui

import ru.kyamshanov.notepen.mainscreen.domain.model.AvailabilityStatus
import ru.kyamshanov.notepen.mainscreen.ui.model.FolderUiModel
import ru.kyamshanov.notepen.mainscreen.ui.model.LibraryShelfUiModel
import ru.kyamshanov.notepen.mainscreen.ui.model.PeerSummaryUiModel
import ru.kyamshanov.notepen.mainscreen.ui.model.RecentFileUiModel
import ru.kyamshanov.notepen.mainscreen.ui.model.RemoteEntryUiModel
import ru.kyamshanov.notepen.mainscreen.ui.model.RemoteFolderUiModel
import ru.kyamshanov.notepen.mainscreen.ui.model.ThumbnailState

/**
 * Создаёт пример recent-файла для карточки главного экрана.
 *
 * @param strings локализованные demo-строки.
 * @return UI-модель recent-файла.
 */
internal fun sampleRecentFile(strings: SandboxStrings): RecentFileUiModel =
    RecentFileUiModel(
        id = "recent-1",
        uri = "file:///sandbox/${strings.fileName}",
        displayName = strings.fileName,
        fileSize = 2_540_000,
        openedAt = 0L,
        availabilityStatus = AvailabilityStatus.NOT_FOUND,
        thumbnailState = ThumbnailState.Error,
        lastPageIndex = 12,
    )

/**
 * Создаёт пример локальной папки.
 *
 * @param strings локализованные demo-строки.
 * @return UI-модель папки.
 */
internal fun sampleFolder(strings: SandboxStrings): FolderUiModel =
    FolderUiModel(
        id = "folder-1",
        name = strings.folderName,
        fileCount = 7,
        createdAt = 0L,
        lastFileOpenedAt = 0L,
    )

/**
 * Создаёт пример локального sync-пира.
 *
 * @param strings локализованные demo-строки.
 * @return UI-модель пира.
 */
internal fun samplePeer(strings: SandboxStrings): PeerSummaryUiModel =
    PeerSummaryUiModel(
        peerId = "peer-1",
        displayName = strings.peerName,
        itemCount = 18,
        isOnline = true,
    )

/**
 * Создаёт пример удалённого документа.
 *
 * @param strings локализованные demo-строки.
 * @return UI-модель удалённой записи.
 */
internal fun sampleRemoteEntry(strings: SandboxStrings): RemoteEntryUiModel =
    RemoteEntryUiModel(
        documentId = "remote-1",
        displayName = strings.remoteFileName,
        fileSize = 6_200_000,
        lastOpenedAt = 0L,
        pendingCount = 3,
    )

/**
 * Создаёт пример удалённой папки.
 *
 * @param strings локализованные demo-строки.
 * @return UI-модель удалённой папки.
 */
internal fun sampleRemoteFolder(strings: SandboxStrings): RemoteFolderUiModel =
    RemoteFolderUiModel(
        folderId = "remote-folder-1",
        name = strings.remoteFolderName,
        fileCount = 11,
    )

/**
 * Создаёт пример элементов library shelf.
 *
 * @param strings локализованные demo-строки.
 * @return список UI-моделей полки библиотеки.
 */
internal fun sampleShelf(strings: SandboxStrings): List<LibraryShelfUiModel> =
    listOf(
        LibraryShelfUiModel(
            id = "library-1",
            uri = "file:///sandbox/${strings.fileName}",
            displayName = strings.fileName,
            sizeBytes = 2_540_000,
            modifiedAt = 0L,
        ),
        LibraryShelfUiModel(
            id = "library-2",
            uri = "file:///sandbox/${strings.remoteFileName}",
            displayName = strings.remoteFileName,
            sizeBytes = 6_200_000,
            modifiedAt = 0L,
        ),
    )
