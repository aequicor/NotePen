package ru.kyamshanov.notepen.library.impl

import kotlinx.coroutines.CoroutineScope
import ru.kyamshanov.notepen.library.api.Library
import ru.kyamshanov.notepen.library.api.LibraryBackend
import ru.kyamshanov.notepen.library.api.LibraryBackendKind
import ru.kyamshanov.notepen.library.api.LibraryConnection
import ru.kyamshanov.notepen.library.api.LibraryDescriptor
import ru.kyamshanov.notepen.mainscreen.domain.port.LibraryFolder

/**
 * Factory that builds a [LibraryFolder] over a filesystem root.
 *
 * The concrete implementation (`FileSystemLibraryFolder`) lives in the desktop/JVM app layer and
 * pulls in `java.io.File`, so it is injected here as a lambda to keep `:library:impl` platform
 * agnostic. On Android no factory is provided and the local-folder backend is simply not registered.
 *
 * @param rootPath absolute path to the library root folder.
 * @param scope long-lived scope the folder uses for its initial scan / refreshes.
 */
public fun interface LibraryFolderFactory {
    public fun create(
        rootPath: String,
        scope: CoroutineScope,
    ): LibraryFolder
}

/**
 * [LibraryBackend] for [LibraryConnection.Local] libraries (a local filesystem folder).
 *
 * Connecting wraps the [LibraryFolder] produced by [folderFactory] in a [LocalFolderLibrary]; the
 * user is always the librarian of their own folder. This backend is desktop-only: the JVM app wires
 * [folderFactory] to `FileSystemLibraryFolder`, while Android does not register it at all.
 *
 * @param folderFactory platform-specific factory that produces a [LibraryFolder] for a root path.
 */
public class LocalFolderLibraryBackend(
    private val folderFactory: LibraryFolderFactory,
) : LibraryBackend {
    override val kind: LibraryBackendKind = LibraryBackendKind.Local

    override suspend fun connect(
        spec: LibraryConnection,
        scope: CoroutineScope,
    ): Result<Library> =
        runCatching {
            val local = spec.requireLocal()
            val folder = folderFactory.create(local.rootPath, scope)
            LocalFolderLibrary(
                descriptor = localFolderDescriptor(local.rootPath),
                libraryFolder = folder,
                scope = scope,
            )
        }

    override suspend fun probe(spec: LibraryConnection): Result<LibraryDescriptor> =
        runCatching { localFolderDescriptor(spec.requireLocal().rootPath) }

    private fun LibraryConnection.requireLocal(): LibraryConnection.Local =
        this as? LibraryConnection.Local
            ?: error("LocalFolderLibraryBackend only handles LibraryConnection.Local, got ${this::class.simpleName}")
}
