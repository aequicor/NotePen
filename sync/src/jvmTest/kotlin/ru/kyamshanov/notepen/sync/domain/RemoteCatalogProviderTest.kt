package ru.kyamshanov.notepen.sync.domain

import kotlinx.coroutines.runBlocking
import ru.kyamshanov.notepen.mainscreen.domain.model.Folder
import ru.kyamshanov.notepen.mainscreen.domain.port.FolderRepository
import ru.kyamshanov.notepen.sync.domain.model.BookId
import ru.kyamshanov.notepen.sync.domain.model.LibraryBook
import ru.kyamshanov.notepen.sync.domain.model.LibraryManifest
import ru.kyamshanov.notepen.sync.domain.port.LibraryManifestProvider
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The host serves a single per-peer catalog that is the **tagged union** of all shared libraries
 * (so a client connected to one library projects only its subset), and tolerates a document-id
 * collision between libraries by keeping the first deterministically.
 */
class RemoteCatalogProviderTest {
    @Test
    fun servesTaggedUnionOfNamedLibraries() =
        runBlocking {
            val math = source("local:/Math", "Math", book("a#1", "alg.pdf", "/Math/alg.pdf"))
            val phys = source("local:/Physics", "Physics", book("b#2", "mech.pdf", "/Physics/mech.pdf"))
            val provider =
                RemoteCatalogProvider(
                    hostName = "Desk",
                    sharedLibrariesProvider = { listOf(math, phys) },
                    folderRepository = EmptyFolders,
                )

            val catalog = provider.buildSnapshotFor("peer-1")

            assertEquals(
                mapOf("a#1" to "local:/Math", "b#2" to "local:/Physics"),
                catalog.recent.associate { it.documentId to it.libraryId },
                "each book is tagged with the library it came from",
            )
            assertEquals(
                listOf("local:/Math" to "Math", "local:/Physics" to "Physics"),
                catalog.libraries.map { it.libraryId to it.displayName },
                "both named libraries are advertised in the catalog",
            )
        }

    @Test
    fun collidingDocumentIdAcrossLibraries_keepsFirst() =
        runBlocking {
            // Two libraries holding a file at the same relative path collide on document id.
            val a = source("local:/A", "A", book("notes.pdf#x", "notes.pdf", "/A/notes.pdf"))
            val b = source("local:/B", "B", book("notes.pdf#x", "notes.pdf", "/B/notes.pdf"))
            val provider =
                RemoteCatalogProvider(
                    hostName = "Desk",
                    sharedLibrariesProvider = { listOf(a, b) },
                    folderRepository = EmptyFolders,
                )

            val catalog = provider.buildSnapshotFor("peer-1")

            assertEquals(1, catalog.recent.size, "the duplicate document id is dropped (first kept)")
            assertEquals("local:/A", catalog.recent.single().libraryId, "the first library's entry survives")
        }

    private fun book(
        id: String,
        name: String,
        uri: String,
    ) = FakeBook(BookId(id), name, uri)

    private fun source(
        libraryId: String,
        name: String,
        vararg books: FakeBook,
    ) = SharedLibrarySource(libraryId, name, FakeManifestProvider(books.toList()))
}

private class FakeBook(
    val id: BookId,
    val name: String,
    val uri: String,
)

private class FakeManifestProvider(
    private val books: List<FakeBook>,
) : LibraryManifestProvider {
    override suspend fun current(): LibraryManifest =
        LibraryManifest(books.map { LibraryBook(it.id, it.name, it.name, fileSize = 1L, modifiedAt = 0L) })

    override suspend fun resolveAbsolutePath(id: BookId): String? = books.firstOrNull { it.id == id }?.uri
}

private object EmptyFolders : FolderRepository {
    override suspend fun getAll(): List<Folder> = emptyList()

    override suspend fun getFilesInFolder(folderId: String): List<String> = emptyList()

    override suspend fun create(
        name: String,
        parentId: String?,
    ): Folder = error("unused")

    override suspend fun rename(
        id: String,
        newName: String,
    ) = Unit

    override suspend fun delete(id: String) = Unit

    override suspend fun addFile(
        folderId: String,
        uri: String,
    ) = Unit

    override suspend fun removeFile(
        folderId: String,
        uri: String,
    ) = Unit
}
