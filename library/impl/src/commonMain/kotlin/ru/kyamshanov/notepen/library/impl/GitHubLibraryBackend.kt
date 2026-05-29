package ru.kyamshanov.notepen.library.impl

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import ru.kyamshanov.notepen.library.api.Library
import ru.kyamshanov.notepen.library.api.LibraryBackend
import ru.kyamshanov.notepen.library.api.LibraryBackendKind
import ru.kyamshanov.notepen.library.api.LibraryConnection
import ru.kyamshanov.notepen.library.api.LibraryDescriptor
import ru.kyamshanov.notepen.sync.cloud.domain.CloudStorageProvider

/**
 * Coordinates of a GitHub repository, parsed from a [LibraryConnection.GitHub.repo] `owner/name`
 * slug plus an optional write token.
 *
 * @property owner repo owner (user or org).
 * @property name repository name.
 * @property branch target branch (defaults to `main`).
 * @property token write-capable token, or empty for anonymous read-only access.
 */
public data class GitHubRepoCoordinates(
    public val owner: String,
    public val name: String,
    public val branch: String,
    public val token: String,
)

/**
 * [LibraryBackend] for [LibraryConnection.GitHub] libraries (a repo read as a book shelf).
 *
 * This backend builds **no** networking of its own: it delegates to a [providerFactory] that
 * produces the **existing** [CloudStorageProvider] (the `GitHubContentsCloudProvider`, configured
 * with the DI site's Ktor engine) for a given repo + token. Works on both desktop and Android — a
 * GitHub library is a cloud client on either platform.
 *
 * The role is derived from the token: a non-empty token → Librarian (upload), empty → Reader. See
 * [GitHubLibrary] for the read/cache/upload behaviour and the blob-sha-as-change-detection note.
 *
 * @param providerFactory builds a [CloudStorageProvider] for the given repo coordinates; the DI
 *   site supplies the concrete `GitHubContentsCloudProvider` with its shared [HttpClient].
 * @param cacheDir absolute directory under which downloaded books are cached.
 * @param ioDispatcher dispatcher for the (blocking) network + filesystem work.
 * @param defaultBranch branch used when a connection does not pin one (GitHub's default is `main`).
 */
public class GitHubLibraryBackend(
    private val providerFactory: (GitHubRepoCoordinates) -> CloudStorageProvider,
    private val cacheDir: String,
    private val ioDispatcher: CoroutineDispatcher,
    private val defaultBranch: String = "main",
) : LibraryBackend {
    override val kind: LibraryBackendKind = LibraryBackendKind.GitHub

    override suspend fun connect(
        spec: LibraryConnection,
        scope: CoroutineScope,
    ): Result<Library> =
        runCatching {
            val github = spec.requireGitHub()
            val coords = coordinatesOf(github)
            GitHubLibrary(
                descriptor = gitHubDescriptor(repo = github.repo, hasWriteToken = coords.token.isNotEmpty()),
                provider = providerFactory(coords),
                cacheDir = cacheDir,
                ioDispatcher = ioDispatcher,
                scope = scope,
            )
        }

    override suspend fun probe(spec: LibraryConnection): Result<LibraryDescriptor> =
        runCatching {
            val github = spec.requireGitHub()
            val coords = coordinatesOf(github)
            // Validate auth/repo by hitting the books listing; a failure (bad token, missing repo)
            // surfaces as the runCatching failure.
            withContext(ioDispatcher) { providerFactory(coords).list(GITHUB_LIBRARY_BOOKS_PATH) }
            gitHubDescriptor(repo = github.repo, hasWriteToken = coords.token.isNotEmpty())
        }

    private fun coordinatesOf(github: LibraryConnection.GitHub): GitHubRepoCoordinates {
        val slug = github.repo.trim().trim('/')
        val owner = slug.substringBefore('/', missingDelimiterValue = "")
        val name = slug.substringAfter('/', missingDelimiterValue = "")
        require(owner.isNotBlank() && name.isNotBlank()) {
            "GitHub repo must be an 'owner/name' slug, got '${github.repo}'"
        }
        return GitHubRepoCoordinates(
            owner = owner,
            name = name,
            branch = defaultBranch,
            token = github.token.orEmpty(),
        )
    }

    private fun LibraryConnection.requireGitHub(): LibraryConnection.GitHub =
        this as? LibraryConnection.GitHub
            ?: error("GitHubLibraryBackend only handles LibraryConnection.GitHub, got ${this::class.simpleName}")
}
