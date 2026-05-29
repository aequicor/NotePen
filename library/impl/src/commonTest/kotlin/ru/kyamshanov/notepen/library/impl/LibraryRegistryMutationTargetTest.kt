package ru.kyamshanov.notepen.library.impl

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import ru.kyamshanov.notepen.library.api.Library
import ru.kyamshanov.notepen.library.api.LibraryBackendKind
import ru.kyamshanov.notepen.library.api.LibraryBookId
import ru.kyamshanov.notepen.library.api.LibraryCapabilities
import ru.kyamshanov.notepen.library.api.LibraryConnection
import ru.kyamshanov.notepen.library.api.LibraryConnectionState
import ru.kyamshanov.notepen.library.api.LibraryDescriptor
import ru.kyamshanov.notepen.library.api.LibraryEntry
import ru.kyamshanov.notepen.library.api.LibraryId
import ru.kyamshanov.notepen.library.api.LibraryRegistry
import ru.kyamshanov.notepen.library.api.LibraryRole
import ru.kyamshanov.notepen.library.api.MergedLibraryEntry
import ru.kyamshanov.notepen.library.api.OpenableDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LibraryRegistryMutationTargetTest {
    @Test
    fun emptyTargetId_resolvesLocalLibrary_andDelegatesAdd() =
        runTest {
            val local = FakeLibrary(kind = LibraryBackendKind.Local, role = LibraryRole.Librarian)
            val target = LibraryRegistryMutationTarget { FakeRegistry(listOf(local)) }

            val result = target.addBook(targetLibraryId = "", localPath = "/tmp/x.pdf", displayName = "X")

            assertTrue(result.isSuccess)
            assertEquals("/tmp/x.pdf", local.addCalls.single())
            assertEquals("added", result.getOrThrow().newLibraryBookId)
        }

    @Test
    fun explicitTargetId_resolvesById() =
        runTest {
            val a = FakeLibrary(id = "lib-a", kind = LibraryBackendKind.PeerLan, role = LibraryRole.Librarian)
            val b = FakeLibrary(id = "lib-b", kind = LibraryBackendKind.Local, role = LibraryRole.Librarian)
            val target = LibraryRegistryMutationTarget { FakeRegistry(listOf(a, b)) }

            val result = target.addBook(targetLibraryId = "lib-a", localPath = "/tmp/y.pdf", displayName = "Y")

            assertTrue(result.isSuccess)
            assertEquals("/tmp/y.pdf", a.addCalls.single())
            assertTrue(b.addCalls.isEmpty())
        }

    @Test
    fun unknownTargetId_fails() =
        runTest {
            val local = FakeLibrary(kind = LibraryBackendKind.Local, role = LibraryRole.Librarian)
            val target = LibraryRegistryMutationTarget { FakeRegistry(listOf(local)) }

            val result = target.addBook(targetLibraryId = "missing", localPath = "/tmp/z.pdf", displayName = "Z")

            assertTrue(result.isFailure)
            assertTrue(local.addCalls.isEmpty())
        }

    @Test
    fun readOnlyLibrary_isRejected_withoutDelegating() =
        runTest {
            val reader = FakeLibrary(kind = LibraryBackendKind.Local, role = LibraryRole.Reader)
            val target = LibraryRegistryMutationTarget { FakeRegistry(listOf(reader)) }

            val result = target.addBook(targetLibraryId = "", localPath = "/tmp/x.pdf", displayName = "X")

            assertTrue(result.isFailure)
            assertTrue(reader.addCalls.isEmpty())
        }

    @Test
    fun nullRegistry_fails() =
        runTest {
            val target = LibraryRegistryMutationTarget { null }
            val result = target.addBook(targetLibraryId = "", localPath = "/tmp/x.pdf", displayName = "X")
            assertTrue(result.isFailure)
        }
}

private class FakeLibrary(
    id: String = "lib-local",
    kind: LibraryBackendKind = LibraryBackendKind.Local,
    role: LibraryRole = LibraryRole.Librarian,
) : Library {
    val addCalls = mutableListOf<String>()

    override val descriptor =
        LibraryDescriptor(id = LibraryId(id), displayName = id, kind = kind, role = role)
    override val capabilities = LibraryCapabilities.fromRole(role)
    override val books: StateFlow<List<LibraryEntry>> = MutableStateFlow(emptyList<LibraryEntry>()).asStateFlow()
    override val connectionState: StateFlow<LibraryConnectionState> =
        MutableStateFlow(LibraryConnectionState.Connected).asStateFlow()

    override suspend fun refresh() = Unit

    override suspend fun open(id: LibraryBookId): Result<OpenableDocument> = Result.failure(UnsupportedOperationException())

    override suspend fun addBook(src: String): Result<LibraryEntry> {
        addCalls += src
        return Result.success(LibraryEntry(libraryBookId = LibraryBookId("added"), displayName = "added"))
    }
}

private class FakeRegistry(
    libs: List<Library>,
) : LibraryRegistry {
    override val libraries: StateFlow<List<Library>> = MutableStateFlow(libs).asStateFlow()
    override val mergedBooks: StateFlow<List<MergedLibraryEntry>> =
        MutableStateFlow(emptyList<MergedLibraryEntry>()).asStateFlow()

    override suspend fun connect(spec: LibraryConnection): Result<Library> = Result.failure(UnsupportedOperationException())

    override suspend fun disconnect(id: LibraryId) = Unit

    override suspend fun savedConnections(): List<LibraryConnection> = emptyList()
}
