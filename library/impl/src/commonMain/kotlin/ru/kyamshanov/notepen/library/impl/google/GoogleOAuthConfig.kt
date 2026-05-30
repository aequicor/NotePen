package ru.kyamshanov.notepen.library.impl.google

import ru.kyamshanov.notepen.library.api.LibraryRole

/**
 * OAuth client configuration for the Google Drive library backend.
 *
 * The [clientId] / [clientSecret] identify a Google OAuth "TV and Limited Input device" client,
 * provisioned in the Google Cloud Console by the app owner. For an installed/limited-input client
 * Google treats the secret as **non-confidential** (it is embedded in the distributed app); it is
 * still required at the token-exchange step, unlike GitHub's device flow.
 *
 * Empty [clientId] disables sign-in (the device flow cannot start) — the app builds and runs, but a
 * user cannot connect a Drive library until real credentials are supplied via config/env.
 *
 * @property clientId the OAuth client id (empty when unconfigured).
 * @property clientSecret the OAuth client secret for the installed-app client (empty when unconfigured).
 */
public data class GoogleOAuthConfig(
    public val clientId: String,
    public val clientSecret: String,
) {
    public companion object {
        /** Read-only access to files the user can see — the Reader scope. */
        public const val SCOPE_DRIVE_READONLY: String = "https://www.googleapis.com/auth/drive.readonly"

        /** Per-file access to files the app created or the user opened — the Librarian (write) scope. */
        public const val SCOPE_DRIVE_FILE: String = "https://www.googleapis.com/auth/drive.file"

        /**
         * Derives the [LibraryRole] implied by an OAuth [scope] string.
         *
         * A read-only scope (or an absent scope) is a [LibraryRole.Reader]; any write-capable scope
         * is a [LibraryRole.Librarian].
         */
        public fun roleForScope(scope: String?): LibraryRole =
            if (scope == null || scope.contains("readonly")) LibraryRole.Reader else LibraryRole.Librarian
    }
}
