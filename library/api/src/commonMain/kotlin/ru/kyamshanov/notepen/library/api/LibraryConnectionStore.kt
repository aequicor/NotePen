package ru.kyamshanov.notepen.library.api

/**
 * Durable store of the user's saved [LibraryConnection]s.
 *
 * Implementations persist the connection list to disk (a single JSON file under the app data dir),
 * surviving app restarts so the registry can auto-reconnect the user's libraries at startup
 * (see [LibraryRegistry.savedConnections] and the `openLibraryAtStartup` app setting). Platform
 * implementations live in the application layer where the data directory is known
 * (desktop: `~/.notepen`; Android: `context.filesDir`).
 *
 * Implementations must be thread-safe: the store may be read at startup while the user mutates it
 * concurrently, and writes must be atomic (temp file + rename) so an interrupted save never leaves a
 * truncated/corrupt file. A read of an absent or corrupt file yields an empty list rather than
 * throwing.
 */
public interface LibraryConnectionStore {
    /**
     * Loads the persisted connections.
     *
     * @return the saved connections in their stored order, or an empty list when nothing has been
     *   saved yet or the backing file is missing/unreadable.
     */
    public suspend fun load(): List<LibraryConnection>

    /**
     * Overwrites the persisted connections with [connections] (atomic write).
     */
    public suspend fun save(connections: List<LibraryConnection>)

    /**
     * Adds [connection] to the persisted set, replacing any existing entry equal to it (idempotent).
     *
     * @return the resulting persisted list.
     */
    public suspend fun add(connection: LibraryConnection): List<LibraryConnection>

    /**
     * Removes every persisted connection equal to [connection] (idempotent — a no-op if absent).
     *
     * @return the resulting persisted list.
     */
    public suspend fun remove(connection: LibraryConnection): List<LibraryConnection>
}
