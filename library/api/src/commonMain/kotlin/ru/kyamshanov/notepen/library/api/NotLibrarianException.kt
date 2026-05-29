package ru.kyamshanov.notepen.library.api

/**
 * Thrown when a mutating library operation (add/remove/replace) is attempted without the
 * [LibraryRole.Librarian] role.
 *
 * The default implementations of [Library.addBook], [Library.removeBook] and [Library.replaceBook]
 * throw this; Reader-role libraries therefore reject mutations out of the box.
 */
public class NotLibrarianException(
    message: String = "This operation requires the Librarian role.",
) : Exception(message)
