package ru.kyamshanov.notepen.library.ui

import ru.kyamshanov.notepen.sync.cloud.domain.DeviceAuthorization

/**
 * The OAuth credentials obtained for a Google Drive library after a successful device-flow sign-in.
 *
 * @property refreshToken the durable OAuth refresh token to persist in the connection spec.
 * @property scope the granted OAuth scope (drives the Reader/Librarian role).
 */
data class GoogleDriveAuthorization(
    val refreshToken: String,
    val scope: String,
)

/**
 * Drives a Google sign-in for the LibrarySources screen, abstracting the device-flow plumbing that
 * lives in `:library:impl` + the app DI (which own the OAuth client + Ktor engine). Keeping this a
 * thin port lets `:common` stay free of `:library:impl` and Google-specific networking.
 *
 * The implementation runs the full device flow: it calls [onCode] once the user code + verification
 * URL are available (so the screen can display them), then polls until the user authorizes.
 */
fun interface GoogleDriveAuthorizer {
    /**
     * Runs the device flow, invoking [onCode] with the user-facing code as soon as it is issued.
     *
     * @return the granted [GoogleDriveAuthorization] on success, or a failure if sign-in was denied,
     *   expired, or errored.
     */
    suspend fun authorize(onCode: (DeviceAuthorization) -> Unit): Result<GoogleDriveAuthorization>
}
