package ru.kyamshanov.notepen.sync.cloud.infrastructure

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import ru.kyamshanov.notepen.sync.cloud.domain.CloudFile
import ru.kyamshanov.notepen.sync.cloud.domain.CloudStorageProvider
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
internal data class PutContentResponse(val content: ContentEntryDto)

private val githubContentsJson = Json { ignoreUnknownKeys = true }

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
 * [CloudStorageProvider] backed by a private GitHub repository's Contents API.
 *
 * Layers are small JSON, so the 1 MB Contents-API limit is irrelevant: downloads
 * use the raw media type (no base64 round-trip), uploads send base64 content with
 * the current blob sha for optimistic concurrency. Because each device writes
 * only its own `layer-<deviceId>.json` path, concurrent uploads never collide.
 *
 * @param httpClient injected engine.
 * @param owner repo owner (user or org).
 * @param repo repository name.
 * @param branch target branch.
 * @param bearerToken access token from the device flow.
 */
class GitHubContentsCloudProvider(
    private val httpClient: HttpClient,
    private val owner: String,
    private val repo: String,
    private val branch: String,
    private val bearerToken: String,
) : CloudStorageProvider {
    private fun contentsUrl(path: String) = "https://api.github.com/repos/$owner/$repo/contents/$path"

    override suspend fun list(directoryPath: String): List<CloudFile> {
        val body =
            httpClient
                .get(contentsUrl(directoryPath)) {
                    header(HttpHeaders.Authorization, "Bearer $bearerToken")
                    header(HttpHeaders.Accept, "application/vnd.github+json")
                    parameter("ref", branch)
                }.bodyAsText()
        return parseDirectoryListing(body)
    }

    override suspend fun download(path: String): ByteArray {
        val text =
            httpClient
                .get(contentsUrl(path)) {
                    header(HttpHeaders.Authorization, "Bearer $bearerToken")
                    header(HttpHeaders.Accept, "application/vnd.github.raw")
                    parameter("ref", branch)
                }.bodyAsText()
        return text.encodeToByteArray()
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
                    header(HttpHeaders.Authorization, "Bearer $bearerToken")
                    header(HttpHeaders.Accept, "application/vnd.github+json")
                    setBody(TextContent(requestJson, ContentType.Application.Json))
                }.bodyAsText()
        return parsePutResponse(body)
    }
}
