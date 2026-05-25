package ru.kyamshanov.notepen.sync.infrastructure

import ru.kyamshanov.notepen.mainscreen.domain.port.FileHistoryRepository
import ru.kyamshanov.notepen.sync.domain.documentIdFromFilePath
import ru.kyamshanov.notepen.sync.domain.model.BookId
import ru.kyamshanov.notepen.sync.domain.model.LibraryBook
import ru.kyamshanov.notepen.sync.domain.model.LibraryManifest
import ru.kyamshanov.notepen.sync.domain.port.LibraryManifestProvider

/**
 * [LibraryManifestProvider] backed by the device's "recent files".
 *
 * Preserves the pre-library behaviour for client devices that have no
 * sandboxed library directory of their own: they keep publishing their recents
 * so a connected host can browse them. [BookId] reuses the legacy path-derived
 * document id, so ids on this side stay byte-for-byte identical to before.
 */
class RecentsLibraryManifestProvider(
    private val historyRepository: FileHistoryRepository,
) : LibraryManifestProvider {
    override suspend fun current(): LibraryManifest =
        LibraryManifest(
            books =
                historyRepository.getAll().map { file ->
                    LibraryBook(
                        id = BookId(documentIdFromFilePath(file.uri)),
                        relativePath = file.uri,
                        displayName = file.displayName,
                        fileSize = file.fileSize,
                        modifiedAt = file.openedAt,
                    )
                },
        )

    override suspend fun resolveAbsolutePath(id: BookId): String? =
        historyRepository.getAll()
            .firstOrNull { documentIdFromFilePath(it.uri) == id.value }
            ?.uri
}
