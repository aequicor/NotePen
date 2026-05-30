package ru.kyamshanov.notepen.library.impl.google

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.kyamshanov.notepen.library.api.DriveLikeStore
import ru.kyamshanov.notepen.library.api.RemoteFile

private const val DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files"
private const val DRIVE_UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
private const val FILE_FIELDS = "id,name,size,version,md5Checksum,modifiedTime"
private const val LIST_PAGE_SIZE = "1000"

private val driveJson = Json { ignoreUnknownKeys = true }

@Serializable
internal data class DriveFileDto(
    val id: String,
    val name: String = "",
    val size: String? = null,
    val version: String? = null,
    val md5Checksum: String? = null,
)

@Serializable
internal data class DriveFileListDto(
    val files: List<DriveFileDto> = emptyList(),
)

@Serializable
internal data class DriveCreateMetadata(
    val name: String,
    val parents: List<String>,
)

private fun DriveFileDto.toRemoteFile(): RemoteFile =
    RemoteFile(
        id = id,
        name = name,
        sizeBytes = size?.toLongOrNull(),
        // `version` is Drive's monotonic per-file revision counter — a change-detection token, not
        // a content hash. Fall back to md5 only when version is absent.
        version = version ?: md5Checksum,
        modifiedAt = null,
    )

/** Pure mapping of a Drive `files.list` response to the listed files. */
internal fun parseDriveFileList(body: String): List<RemoteFile> =
    driveJson
        .decodeFromString<DriveFileListDto>(body)
        .files
        .map { it.toRemoteFile() }

/** Pure mapping of a Drive single-file (`files.create` / `files.update`) response to a [RemoteFile]. */
internal fun parseDriveFile(body: String): RemoteFile = driveJson.decodeFromString<DriveFileDto>(body).toRemoteFile()

/**
 * Builds the `multipart/related` body for a Drive resumable-free media upload: the JSON metadata
 * part followed by the raw media part, separated by [boundary]. Returned as bytes so the binary
 * media survives untouched (no base64).
 */
internal fun buildRelatedUploadBody(
    boundary: String,
    metadataJson: String,
    media: ByteArray,
): ByteArray {
    val prefix =
        buildString {
            append("--").append(boundary).append("\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadataJson).append("\r\n")
            append("--").append(boundary).append("\r\n")
            append("Content-Type: application/octet-stream\r\n\r\n")
        }.encodeToByteArray()
    val suffix = "\r\n--$boundary--".encodeToByteArray()
    return prefix + media + suffix
}

/**
 * [DriveLikeStore] backed by the **Google Drive v3 REST API**. A shared Drive folder is the book
 * shelf; each non-folder child is a book addressed by its opaque Drive `fileId`.
 *
 * Auth is a bearer token obtained from [tokenSource]; on a `401` the token is refreshed once and the
 * request retried, so a Librarian session survives access-token expiry (~1h) without reconnecting.
 *
 * Limitations (tracked for later hardening): listing is single-page (`pageSize=1000`, no
 * pagination); [update] does not yet enforce optimistic concurrency from [RemoteFile.version]
 * (Drive's media-update endpoint takes no usable precondition for the monotonic version counter), so
 * concurrent replaces are last-writer-wins.
 *
 * @param httpClient injected engine (CIO in the app); not configured with `expectSuccess`, so this
 *   class inspects [HttpResponse.status] itself for the 401-retry.
 * @param tokenSource supplies (and refreshes) the OAuth bearer token.
 */
public class GoogleDriveStore(
    private val httpClient: HttpClient,
    private val tokenSource: AccessTokenSource,
) : DriveLikeStore {
    override suspend fun listChildren(folderId: String): List<RemoteFile> {
        val query = "'$folderId' in parents and trashed = false and mimeType != 'application/vnd.google-apps.folder'"
        val response =
            withAuthRetry { bearer ->
                httpClient.get(DRIVE_FILES_URL) {
                    bearerAuth(bearer)
                    parameter("q", query)
                    parameter("fields", "files($FILE_FIELDS)")
                    parameter("pageSize", LIST_PAGE_SIZE)
                    parameter("orderBy", "name")
                }
            }
        return parseDriveFileList(response.bodyAsText())
    }

    override suspend fun download(fileId: String): ByteArray =
        withAuthRetry { bearer ->
            httpClient.get("$DRIVE_FILES_URL/$fileId") {
                bearerAuth(bearer)
                parameter("alt", "media")
            }
        }.bodyAsBytes()

    override suspend fun create(
        parentFolderId: String,
        name: String,
        bytes: ByteArray,
    ): RemoteFile {
        val boundary = "notepen-${bytes.size}-${name.hashCode()}"
        val metadata =
            driveJson.encodeToString(
                DriveCreateMetadata.serializer(),
                DriveCreateMetadata(name = name, parents = listOf(parentFolderId)),
            )
        val body = buildRelatedUploadBody(boundary, metadata, bytes)
        val response =
            withAuthRetry { bearer ->
                httpClient.post(DRIVE_UPLOAD_URL) {
                    bearerAuth(bearer)
                    parameter("uploadType", "multipart")
                    parameter("fields", FILE_FIELDS)
                    contentType(ContentType.parse("multipart/related; boundary=$boundary"))
                    setBody(body)
                }
            }
        return parseDriveFile(response.bodyAsText())
    }

    override suspend fun update(
        fileId: String,
        bytes: ByteArray,
        previousVersion: String?,
    ): RemoteFile {
        // previousVersion is the change-detection token; Drive's media-update endpoint has no
        // usable precondition for the monotonic version counter, so it is not sent (last-writer-wins
        // until a precondition-capable path is added — see class KDoc).
        val response =
            withAuthRetry { bearer ->
                httpClient.patch("$DRIVE_UPLOAD_URL/$fileId") {
                    bearerAuth(bearer)
                    parameter("uploadType", "media")
                    parameter("fields", FILE_FIELDS)
                    contentType(ContentType.Application.OctetStream)
                    setBody(bytes)
                }
            }
        return parseDriveFile(response.bodyAsText())
    }

    /**
     * Runs [call] with a current bearer token; on a `401 Unauthorized` refreshes the token once and
     * retries exactly once, so a single mid-session expiry is recovered transparently.
     */
    private suspend fun withAuthRetry(call: suspend (bearer: String) -> HttpResponse): HttpResponse {
        val first = call(tokenSource.bearer())
        return if (first.status == HttpStatusCode.Unauthorized) {
            call(tokenSource.bearer(forceRefresh = true))
        } else {
            first
        }
    }
}
