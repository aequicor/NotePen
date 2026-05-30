package ru.kyamshanov.notepen.library.impl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import ru.kyamshanov.notepen.document.domain.model.CanonicalBookId
import ru.kyamshanov.notepen.document.domain.model.DocumentIdentity
import ru.kyamshanov.notepen.document.domain.model.basenameOf
import ru.kyamshanov.notepen.document.domain.model.wireIdOf
import ru.kyamshanov.notepen.document.domain.port.DocumentIdentityProvider
import ru.kyamshanov.notepen.library.api.LibraryConnection
import ru.kyamshanov.notepen.mainscreen.domain.port.LibraryFolder
import ru.kyamshanov.notepen.mainscreen.domain.port.LibraryFolderItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LocalFolderLibraryIdentityTest {
    private val rootPath = "/tmp/notepen-id-test"

    private fun item(
        id: String,
        name: String,
    ) = LibraryFolderItem(id = id, uri = "$rootPath/$id", displayName = name, sizeBytes = 1L, modifiedAt = 0L)

    private class FakeFolder(
        initial: List<LibraryFolderItem>,
    ) : LibraryFolder {
        private val mutable = MutableStateFlow(initial)
        override val items: StateFlow<List<LibraryFolderItem>> = mutable.asStateFlow()

        fun emit(value: List<LibraryFolderItem>) {
            mutable.value = value
        }

        override suspend fun addCopy(sourceUri: String): Result<LibraryFolderItem> = Result.failure(UnsupportedOperationException())

        override suspend fun refresh() = Unit
    }

    /** Identity provider that maps each path to a deterministic fake sha; records what it hashed. */
    private class FakeIdentityProvider(
        private val hexByPath: Map<String, String>,
    ) : DocumentIdentityProvider {
        val hashedPaths = mutableListOf<String>()

        override suspend fun identityForPath(filePath: String): DocumentIdentity {
            hashedPaths += filePath
            val hex = hexByPath[filePath] ?: error("no fake hash for $filePath")
            return DocumentIdentity(CanonicalBookId(hex), wireIdOf(basenameOf(filePath), hex))
        }

        override suspend fun identityForBytes(
            basename: String,
            bytes: ByteArray,
        ): DocumentIdentity = error("unused")

        override fun cachedWireIdForPath(filePath: String): String? = null
    }

    @Test
    fun books_populateCanonicalIdentityAsynchronously_fromProvider() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val hexA = "a".repeat(64)
            val folder = FakeFolder(listOf(item("a.pdf", "A")))
            val provider = FakeIdentityProvider(mapOf("$rootPath/a.pdf" to hexA))
            val backend = LocalFolderLibraryBackend(identityProvider = provider) { _, _ -> folder }

            val library = backend.connect(LibraryConnection.Local(rootPath), scope).getOrThrow()

            // Under the unconfined dispatcher the async resolution runs to completion eagerly.
            assertEquals(
                CanonicalBookId(hexA),
                library.books.value.single().identity,
                "the local entry gets its content-addressed identity from the provider",
            )
            assertEquals(listOf("$rootPath/a.pdf"), provider.hashedPaths)
            scope.cancel()
        }

    @Test
    fun books_withoutProvider_keepIdentityNull() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val folder = FakeFolder(listOf(item("a.pdf", "A")))
            val backend = LocalFolderLibraryBackend(identityProvider = null) { _, _ -> folder }

            val library = backend.connect(LibraryConnection.Local(rootPath), scope).getOrThrow()

            assertNull(library.books.value.single().identity, "no provider → identity stays null")
            scope.cancel()
        }

    @Test
    fun rescan_reusesCachedIdentity_doesNotRehashUnchangedFile() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val hexA = "a".repeat(64)
            val hexB = "b".repeat(64)
            val folder = FakeFolder(listOf(item("a.pdf", "A")))
            val provider =
                FakeIdentityProvider(mapOf("$rootPath/a.pdf" to hexA, "$rootPath/b.pdf" to hexB))
            val backend = LocalFolderLibraryBackend(identityProvider = provider) { _, _ -> folder }
            val library = backend.connect(LibraryConnection.Local(rootPath), scope).getOrThrow()
            assertEquals(CanonicalBookId(hexA), library.books.value.single().identity)

            // A re-scan that adds b.pdf but keeps a.pdf must only hash the new file.
            folder.emit(listOf(item("a.pdf", "A"), item("b.pdf", "B")))

            val ids = library.books.value.associate { it.libraryBookId.value to it.identity }
            assertEquals(CanonicalBookId(hexA), ids["a.pdf"])
            assertEquals(CanonicalBookId(hexB), ids["b.pdf"])
            assertEquals(1, provider.hashedPaths.count { it == "$rootPath/a.pdf" }, "a.pdf hashed once, cached on re-scan")
            assertTrue(provider.hashedPaths.contains("$rootPath/b.pdf"), "the new file is hashed")
            scope.cancel()
        }
}
