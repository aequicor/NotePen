package ru.kyamshanov.notepen.library.impl.google

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import ru.kyamshanov.notepen.library.api.CloudProviderIds
import ru.kyamshanov.notepen.library.api.DriveLikeStore
import ru.kyamshanov.notepen.library.api.Library
import ru.kyamshanov.notepen.library.api.LibraryBackend
import ru.kyamshanov.notepen.library.api.LibraryBackendKind
import ru.kyamshanov.notepen.library.api.LibraryConnection
import ru.kyamshanov.notepen.library.api.LibraryDescriptor

/**
 * Coordinates of a Google Drive shelf, parsed from a [LibraryConnection.Cloud].
 *
 * @property folderId the Drive folder id used as the book shelf ([LibraryConnection.Cloud.accountId]).
 * @property refreshToken the OAuth refresh token for this account, or empty when none was stored.
 * @property scope the OAuth scope granted (drives the [ru.kyamshanov.notepen.library.api.LibraryRole]).
 */
public data class GoogleDriveCoordinates(
    public val folderId: String,
    public val refreshToken: String,
    public val scope: String?,
)

/**
 * [LibraryBackend] for [LibraryConnection.Cloud] libraries whose
 * [LibraryConnection.Cloud.providerId] is [CloudProviderIds.GOOGLE_DRIVE] (a Drive folder read as a
 * book shelf). Works on both desktop and Android — a Drive library is a cloud client on either platform.
 *
 * This backend builds no networking of its own: it delegates to a [storeFactory] that produces the
 * [DriveLikeStore] (the `GoogleDriveStore`, configured with the DI site's Ktor engine + an
 * [AccessTokenSource]) for the given coordinates. The role is derived from the granted OAuth scope;
 * see [GoogleDriveLibrary].
 *
 * @param storeFactory builds a [DriveLikeStore] for the given coordinates; the DI site supplies the
 *   concrete `GoogleDriveStore` with its shared `HttpClient` and a refreshing token source.
 * @param cacheDir absolute directory under which downloaded books are cached.
 * @param ioDispatcher dispatcher for the (blocking) network + filesystem work.
 */
public class GoogleDriveLibraryBackend(
    private val storeFactory: (GoogleDriveCoordinates) -> DriveLikeStore,
    private val cacheDir: String,
    private val ioDispatcher: CoroutineDispatcher,
) : LibraryBackend {
    override val kind: LibraryBackendKind = LibraryBackendKind.Cloud

    override suspend fun connect(
        spec: LibraryConnection,
        scope: CoroutineScope,
    ): Result<Library> =
        runCatching {
            val coords = coordinatesOf(spec.requireGoogleDrive())
            GoogleDriveLibrary(
                descriptor = descriptorFor(coords),
                store = storeFactory(coords),
                folderId = coords.folderId,
                cacheDir = cacheDir,
                ioDispatcher = ioDispatcher,
                scope = scope,
            )
        }

    override suspend fun probe(spec: LibraryConnection): Result<LibraryDescriptor> =
        runCatching {
            val coords = coordinatesOf(spec.requireGoogleDrive())
            // Validate auth/folder by listing the shelf; a failure (bad token, missing folder)
            // surfaces as the runCatching failure.
            withContext(ioDispatcher) { storeFactory(coords).listChildren(coords.folderId) }
            descriptorFor(coords)
        }

    private fun descriptorFor(coords: GoogleDriveCoordinates): LibraryDescriptor =
        googleDriveDescriptor(
            folderId = coords.folderId,
            displayName = coords.folderId,
            role = GoogleOAuthConfig.roleForScope(coords.scope),
        )

    private fun coordinatesOf(cloud: LibraryConnection.Cloud): GoogleDriveCoordinates {
        val folderId = cloud.accountId.trim()
        require(folderId.isNotBlank()) { "Google Drive library requires a folder id (accountId), got '${cloud.accountId}'" }
        return GoogleDriveCoordinates(
            folderId = folderId,
            refreshToken = cloud.refreshToken.orEmpty(),
            scope = cloud.scope,
        )
    }

    private fun LibraryConnection.requireGoogleDrive(): LibraryConnection.Cloud {
        val cloud =
            this as? LibraryConnection.Cloud
                ?: error("GoogleDriveLibraryBackend only handles LibraryConnection.Cloud, got ${this::class.simpleName}")
        require(cloud.providerId == CloudProviderIds.GOOGLE_DRIVE) {
            "GoogleDriveLibraryBackend only handles providerId '${CloudProviderIds.GOOGLE_DRIVE}', got '${cloud.providerId}'"
        }
        return cloud
    }
}
