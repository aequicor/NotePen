package ru.kyamshanov.notepen.library.api

/**
 * Stable [LibraryConnection.Cloud.providerId] discriminators for the generic-cloud backend.
 *
 * Lives in `:library:api` so both the UI (`:common`, which builds the connection spec) and the
 * backend (`:library:impl`, which routes on it) share one source of truth — the string is a
 * persisted discriminator and must not drift between the two.
 */
public object CloudProviderIds {
    /** Google Drive (a shared Drive folder read as a book shelf). */
    public const val GOOGLE_DRIVE: String = "google_drive"
}
