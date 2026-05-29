package ru.kyamshanov.notepen.sync.cloud.infrastructure

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import ru.kyamshanov.notepen.sync.cloud.domain.CloudFile
import ru.kyamshanov.notepen.sync.cloud.domain.CloudStorageProvider
import ru.kyamshanov.notepen.sync.infrastructure.decodeBase64
import ru.kyamshanov.notepen.sync.infrastructure.encodeBase64

@Serializable
internal data class ContentEntryDto(
    val name: String,
    val path: String,
    val sha: String,
    val size: Long,
    val type: String,
)

@Serializable
internal data class PutContentRequest(
    val message: String,
    val content: String,
    val branch: String,
    val sha: String? = null,
)

@Serializable
internal data class PutContentResponse(
    val content: ContentEntryDto,
)

@Serializable
internal data class BlobDto(
    val content: String,
    val encoding: String,
)

private val githubContentsJson = Json { ignoreUnknownKeys = true }

/**
 * Pure mapping of a `/git/blobs/{sha}` response to raw bytes. GitHub returns the blob
 * base64-encoded (with embedded newlines GitHub inserts every 60 chars, which the standard
 * base64 decoder rejects), so the content is stripped of whitespace before decoding. A
 * non-base64 encoding (GitHub only ever returns `base64` here) fails fast.
 */
internal fun decodeBlobBytes(body: String): ByteArray {
    val dto = githubContentsJson.decodeFromString<BlobDto>(body)
    require(dto.encoding == "base64") { "Unexpected blob encoding '${dto.encoding}'" }
    return decodeBase64(dto.content.replace("\n", "").replace("\r", ""))
}

/** Pure mapping of a Contents API directory listing to files only. */
internal fun parseDirectoryListing(body: String): List<CloudFile> =
    githubContentsJson
        .decodeFromString<List<ContentEntryDto>>(body)
        .filter { it.type == "file" }
        .map { CloudFile(path = it.path, sha = it.sha, sizeBytes = it.size) }

/** Pure mapping of a create/update-file response to the new [CloudFile]. */
internal fun parsePutResponse(body: String): CloudFile =
    githubContentsJson.decodeFromString<PutContentResponse>(body).content.let {
        CloudFile(path = it.path, sha = it.sha, sizeBytes = it.size)
    }

/**
 * [CloudStorageProvider] backed by a GitHub repository's Contents + Git Data APIs.
 *
 * Sync layers are small JSON, but the same provider also fronts the GitHub *library*
 * backend (`:library`), whose books can exceed the 1 MB Contents-API ceiling. Downloads
 * therefore come in two flavours: [download] streams the raw media type (binary-safe via
 * [bodyAsBytes], no base64 round-trip) and is correct for files up to that ceiling, while
 * [downloadBySha] uses the Git blob endpoint (`/git/blobs/{sha}`, ~100 MB) for larger files.
 * Uploads send base64 content with the current blob sha for optimistic concurrency.
 *
 * An empty [bearerToken] addresses a public repo anonymously (read-only): the
 * `Authorization` header is then omitted so GitHub does not reject the request.
 *
 * @param httpClient injected engine.
 * @param owner repo owner (user or org).
 * @param repo repository name.
 * @param branch target branch.
 * @param bearerToken access token (device-flow or PAT), or empty for anonymous read-only access.
 */
class GitHubContentsCloudProvider(
    private val httpClient: HttpClient,
    private val owner: String,
    private val repo: String,
    private val branch: String,
    private val bearerToken: String,
) : CloudStorageProvider {
    private fun contentsUrl(path: String) = "https://api.github.com/repos/$owner/$repo/contents/$path"

    private fun blobUrl(sha: String) = "https://api.github.com/repos/$owner/$repo/git/blobs/$sha"

    private fun io.ktor.client.request.HttpRequestBuilder.authorize() {
        if (bearerToken.isNotEmpty()) {
            header(HttpHeaders.Authorization, "Bearer $bearerToken")
        }
    }

    override suspend fun list(directoryPath: String): List<CloudFile> {
        val body =
            httpClient
                .get(contentsUrl(directoryPath)) {
                    authorize()
                    header(HttpHeaders.Accept, "application/vnd.github+json")
                    parameter("ref", branch)
                }.bodyAsText()
        return parseDirectoryListing(body)
    }

    override suspend fun download(path: String): ByteArray =
        httpClient
            .get(contentsUrl(path)) {
                authorize()
                header(HttpHeaders.Accept, "application/vnd.github.raw")
                parameter("ref", branch)
            }.bodyAsBytes()

    override suspend fun downloadBySha(
        sha: String,
        fallbackPath: String,
    ): ByteArray {
        val body =
            httpClient
                .get(blobUrl(sha)) {
                    authorize()
                    header(HttpHeaders.Accept, "application/vnd.github+json")
                }.bodyAsText()
        return decodeBlobBytes(body)
    }

    override suspend fun upload(
        path: String,
        bytes: ByteArray,
        previousSha: String?,
    ): CloudFile {
        val requestJson =
            githubContentsJson.encodeToString(
                PutContentRequest.serializer(),
                PutContentRequest(
                    message = "notepen: update $path",
                    content = encodeBase64(bytes),
                    branch = branch,
                    sha = previousSha,
                ),
            )
        val body =
            httpClient
                .put(contentsUrl(path)) {
                    authorize()
                    header(HttpHeaders.Accept, "application/vnd.github+json")
                    setBody(TextContent(requestJson, ContentType.Application.Json))
                }.bodyAsText()
        return parsePutResponse(body)
    }
}
