package ru.kyamshanov.notepen.sync.infrastructure

import ru.kyamshanov.notepen.document.domain.port.DocumentIdentityProvider
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
 * so a connected host can browse them.
 *
 * The advertised [BookId] is the **content-addressed sync wire id** computed by
 * [identityProvider] (`<basename>#<sha256-prefix>`). This is what a peer uses to
 * address the document, so it MUST match the id both ends compute for the same
 * bytes — hence content-addressing rather than the legacy path hash. When the
 * id can't be computed (e.g. the recent file is no longer readable) the entry
 * falls back to the legacy path-derived id so the file still appears.
 */
class RecentsLibraryManifestProvider(
    private val historyRepository: FileHistoryRepository,
    private val identityProvider: DocumentIdentityProvider,
) : LibraryManifestProvider {
    override suspend fun current(): LibraryManifest =
        LibraryManifest(
            books =
                historyRepository.getAll().map { file ->
                    LibraryBook(
                        id = BookId(wireIdFor(file.uri)),
                        relativePath = file.uri,
                        displayName = file.displayName,
                        fileSize = file.fileSize,
                        modifiedAt = file.openedAt,
                    )
                },
        )

    override suspend fun resolveAbsolutePath(id: BookId): String? =
        historyRepository
            .getAll()
            .firstOrNull { wireIdFor(it.uri) == id.value }
            ?.uri

    private suspend fun wireIdFor(uri: String): String =
        runCatching { identityProvider.identityForPath(uri).wireId }
            .getOrElse { documentIdFromFilePath(uri) }
}
