package ru.kyamshanov.notepen.library.api

/**
 * Immutable description of a connected (or probed) [Library].
 *
 * @property id stable registry identifier of the library.
 * @property displayName human-readable name shown in the UI.
 * @property kind the storage backend behind the library.
 * @property role the access level the current user has in this library.
 */
public data class LibraryDescriptor(
    public val id: LibraryId,
    public val displayName: String,
    public val kind: LibraryBackendKind,
    public val role: LibraryRole,
)

/**
 * The live connection status of a [Library].
 */
public sealed interface LibraryConnectionState {
    /** Not connected; no attempt is currently in progress. */
    public data object Disconnected : LibraryConnectionState

    /** A connection attempt is in progress. */
    public data object Connecting : LibraryConnectionState

    /** Connected and usable. */
    public data object Connected : LibraryConnectionState

    /**
     * The connection failed or was lost.
     *
     * @property message human-readable cause, suitable for display/diagnostics.
     */
    public data class Error(
        public val message: String,
    ) : LibraryConnectionState
}
