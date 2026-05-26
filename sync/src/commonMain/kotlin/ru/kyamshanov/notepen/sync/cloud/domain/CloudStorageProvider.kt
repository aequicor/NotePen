package ru.kyamshanov.notepen.sync.cloud.domain

/**
 * One file in remote cloud storage, identified by its store-relative [path] and a
 * content revision [sha] used for change detection and optimistic updates.
 */
data class CloudFile(
    val path: String,
    val sha: String,
    val sizeBytes: Long,
)

/**
 * Minimal remote object store the cloud-sync engine needs, independent of any
 * provider's API shape. The first implementation is GitHub (a private repo via
 * the Contents API); Yandex Disk can follow behind the same port.
 *
 * All paths are store-relative, e.g. `"book-42/layer-deviceA.json"`.
 */
interface CloudStorageProvider {
    /** Lists files directly under [directoryPath] (non-recursive). */
    suspend fun list(directoryPath: String): List<CloudFile>

    /** Downloads the raw bytes of the file at [path]. */
    suspend fun download(path: String): ByteArray

    /**
     * Creates or updates the file at [path]. When updating an existing file,
     * [previousSha] MUST be its current [CloudFile.sha]; a stale or null sha for
     * an existing file fails (optimistic concurrency). Returns the new revision.
     */
    suspend fun upload(
        path: String,
        bytes: ByteArray,
        previousSha: String?,
    ): CloudFile
}
