package ru.kyamshanov.notepen.library.api

import kotlinx.coroutines.CoroutineScope

/**
 * A service-provider interface (SPI) for one kind of library storage backend.
 *
 * Each backend knows how to connect to and probe libraries of a single [LibraryBackendKind].
 * Implementations live in `:library:impl` (and platform source sets for filesystem-rooted backends).
 */
public interface LibraryBackend {
    /** The backend kind this provider handles. */
    public val kind: LibraryBackendKind

    /**
     * Connects to the library described by [spec], using [scope] for the library's long-lived work
     * (listing refreshes, connection monitoring).
     *
     * @return a success with the connected [Library], or a failure if the connection could not be
     *   established.
     */
    public suspend fun connect(
        spec: LibraryConnection,
        scope: CoroutineScope,
    ): Result<Library>

    /**
     * Probes the library described by [spec] without fully connecting, e.g. to preview its
     * descriptor before adding it.
     *
     * @return a success with the probed [LibraryDescriptor], or a failure if it is unreachable.
     */
    public suspend fun probe(spec: LibraryConnection): Result<LibraryDescriptor>
}
