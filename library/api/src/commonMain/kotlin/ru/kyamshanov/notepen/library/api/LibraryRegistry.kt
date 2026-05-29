package ru.kyamshanov.notepen.library.api

import kotlinx.coroutines.flow.StateFlow

/**
 * The central registry of all connected libraries.
 *
 * Aggregates multiple [Library] instances behind one reactive view, dispatching connection specs to
 * the appropriate [LibraryBackend] and exposing a merged, deduplicated book listing across them.
 */
public interface LibraryRegistry {
    /** The currently connected libraries, updated reactively. */
    public val libraries: StateFlow<List<Library>>

    /** All books across every connected library, deduplicated by canonical identity. */
    public val mergedBooks: StateFlow<List<MergedLibraryEntry>>

    /**
     * Connects the library described by [spec] (routing to the matching [LibraryBackend]) and adds
     * it to the registry.
     *
     * @return a success with the connected [Library], or a failure if no backend matches or the
     *   connection failed.
     */
    public suspend fun connect(spec: LibraryConnection): Result<Library>

    /** Disconnects and removes the library identified by [id] from the registry. */
    public suspend fun disconnect(id: LibraryId)

    /**
     * Returns the persisted connection specs, e.g. to auto-reconnect at startup.
     */
    public suspend fun savedConnections(): List<LibraryConnection>
}
