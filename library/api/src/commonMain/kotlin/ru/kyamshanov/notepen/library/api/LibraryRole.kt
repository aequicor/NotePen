package ru.kyamshanov.notepen.library.api

/**
 * The access level a user has within a given [Library].
 *
 * The role determines which mutating operations are permitted; see [LibraryCapabilities].
 */
public enum class LibraryRole {
    /** Read-only access: may browse and open books, but not modify the library. */
    Reader,

    /** Full access: may add, remove and replace books in addition to reading. */
    Librarian,
}

/**
 * Coarse-grained classification of the storage backend behind a [Library].
 */
public enum class LibraryBackendKind {
    /** A library rooted in a local filesystem folder (desktop only as a host). */
    Local,

    /** A remote library shared by a peer over the local network (LAN). */
    PeerLan,

    /** A library backed by a GitHub repository. */
    GitHub,

    /** A library backed by a generic cloud storage provider (Drive/Dropbox/…). */
    Cloud,
}

/**
 * The set of mutating operations a [Library] permits for the current [LibraryRole].
 *
 * @property canAdd whether [Library.addBook] is permitted.
 * @property canRemove whether [Library.removeBook] is permitted.
 * @property canReplace whether [Library.replaceBook] is permitted.
 */
public data class LibraryCapabilities(
    public val canAdd: Boolean,
    public val canRemove: Boolean,
    public val canReplace: Boolean,
) {
    public companion object {
        /**
         * Derives the capabilities implied by [role].
         *
         * A [LibraryRole.Reader] gets no mutating capabilities; a [LibraryRole.Librarian]
         * gets all of them.
         */
        public fun fromRole(role: LibraryRole): LibraryCapabilities =
            when (role) {
                LibraryRole.Reader -> LibraryCapabilities(canAdd = false, canRemove = false, canReplace = false)
                LibraryRole.Librarian -> LibraryCapabilities(canAdd = true, canRemove = true, canReplace = true)
            }
    }
}
