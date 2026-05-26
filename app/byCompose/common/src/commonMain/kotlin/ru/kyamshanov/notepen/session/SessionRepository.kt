package ru.kyamshanov.notepen.session

/**
 * Persistence port for the Sessions feature: a single rolling autosave plus a
 * list of user-named sessions.
 *
 * The autosave holds at most one [SessionData] (the last workspace state); the
 * named list is an upsert-by-id collection of [NamedSession].
 *
 * All mutating operations are atomic; the repository never generates time —
 * [NamedSession.savedAtEpochMs] is supplied by the caller.
 */
interface SessionRepository {
    /** Returns the autosaved workspace, or `null` when none has been saved (or it is unreadable). */
    suspend fun loadAutosave(): SessionData?

    /** Replaces the autosaved workspace with [data]. */
    suspend fun saveAutosave(data: SessionData)

    /** Removes the autosaved workspace, after which [loadAutosave] returns `null`. */
    suspend fun clearAutosave()

    /** Returns all named sessions; empty when none exist (or the store is unreadable). */
    suspend fun listNamed(): List<NamedSession>

    /** Inserts [session], or replaces the existing one with the same [NamedSession.id]. */
    suspend fun saveNamed(session: NamedSession)

    /** Removes the named session with the given [id]; no-op when absent. */
    suspend fun deleteNamed(id: String)

    /**
     * Stores [data] as the single pending restore — a session the library asked
     * the editor to open. Overwrites any previously pending session.
     *
     * This is a one-shot hand-off slot (separate from the autosave): the library
     * screen has no live workspace to restore into, so it persists the chosen
     * session here and opens the editor, which picks it up via [consumePendingRestore].
     */
    suspend fun savePendingRestore(data: SessionData)

    /**
     * Atomically reads **and clears** the pending restore, returning it (or `null`
     * when none is pending / it is unreadable). Reading consumes it, so a normal
     * editor open that follows finds nothing pending and behaves as before.
     */
    suspend fun consumePendingRestore(): SessionData?
}

/** Creates the platform [SessionRepository] backing the Sessions store. */
expect fun createSessionRepository(): SessionRepository
