package ru.kyamshanov.notepen.library.impl

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.kyamshanov.notepen.library.api.Library
import ru.kyamshanov.notepen.library.api.LibraryConnection
import ru.kyamshanov.notepen.library.api.LibraryId
import ru.kyamshanov.notepen.library.api.LibraryRegistry
import ru.kyamshanov.notepen.library.api.MergedLibraryEntry

/**
 * Placeholder [LibraryRegistry] implementation for the M0 module skeleton.
 *
 * It wires no backends yet: [libraries] and [mergedBooks] stay empty, [connect] always fails, and
 * there are no saved connections. Backend wiring and connection persistence land in M1+.
 */
internal class DefaultLibraryRegistry : LibraryRegistry {
    private val librariesState = MutableStateFlow<List<Library>>(emptyList())
    private val mergedBooksState = MutableStateFlow<List<MergedLibraryEntry>>(emptyList())

    override val libraries: StateFlow<List<Library>> = librariesState.asStateFlow()
    override val mergedBooks: StateFlow<List<MergedLibraryEntry>> = mergedBooksState.asStateFlow()

    override suspend fun connect(spec: LibraryConnection): Result<Library> =
        Result.failure(NotImplementedError("Library backends are not wired yet (M0 skeleton)."))

    override suspend fun disconnect(id: LibraryId) {
        // No-op: no libraries are connected in the M0 skeleton.
    }

    override suspend fun savedConnections(): List<LibraryConnection> = emptyList()
}
