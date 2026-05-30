package ru.kyamshanov.notepen.library.api

/**
 * One file in an id-addressed remote object store (e.g. Google Drive), identified by its opaque,
 * stable [id] rather than by a path.
 *
 * Unlike the path-addressed `CloudStorageProvider` (GitHub Contents API) where the path doubles as
 * both identity and human-readable name, an id-addressed store keeps them separate: [id] is the
 * stable handle used for every operation, while [name] is the mutable, human-readable title shown in
 * the UI.
 *
 * @property id opaque, stable file id (e.g. a Drive `fileId`). Used to address every operation.
 * @property name human-readable file name/title, shown in the UI. May change without changing [id].
 * @property sizeBytes file size in bytes, or `null` when the store does not report it.
 * @property version content/revision token (e.g. a Drive ETag or `headRevisionId`) used to detect
 *   upstream changes and as the optimistic-concurrency token for updates. This is a **version**, not
 *   a content hash — do not treat equal versions as proof of equal bytes across different files.
 * @property modifiedAt last-modified time in epoch milliseconds, or `null` when unknown.
 */
public data class RemoteFile(
    public val id: String,
    public val name: String,
    public val sizeBytes: Long? = null,
    public val version: String? = null,
    public val modifiedAt: Long? = null,
)

/**
 * Minimal **id-addressed** remote object store backing a cloud [Library] (first implementation:
 * Google Drive). Distinct from the path-addressed `CloudStorageProvider` in `:sync`, which cannot
 * express Drive's model: a Drive `create` returns a *new* opaque id only known after the call, an
 * `update` requires the existing id, and listing is a parent-folder query — none of which a
 * path-keyed `upload(path, …)` / `list(directoryPath)` contract can represent.
 *
 * Implementations live in `:library:impl` (and platform source sets if a store needs platform code).
 * All blocking network work is expected to run on an IO dispatcher chosen by the caller.
 */
public interface DriveLikeStore {
    /**
     * Lists the files (not sub-folders) directly under the folder identified by [folderId].
     *
     * @return the folder's files; empty if the folder is empty.
     */
    public suspend fun listChildren(folderId: String): List<RemoteFile>

    /** Downloads the raw bytes of the file identified by [fileId]. */
    public suspend fun download(fileId: String): ByteArray

    /**
     * Creates a new file named [name] inside the folder [parentFolderId] with the given [bytes].
     *
     * @return the created file, including the store-assigned [RemoteFile.id] (unknown before the call).
     */
    public suspend fun create(
        parentFolderId: String,
        name: String,
        bytes: ByteArray,
    ): RemoteFile

    /**
     * Replaces the content of the existing file [fileId] with [bytes].
     *
     * @param previousVersion the file's current [RemoteFile.version] for optimistic concurrency; a
     *   stale version MUST fail rather than overwrite a newer revision.
     * @return the updated file with its new [RemoteFile.version].
     */
    public suspend fun update(
        fileId: String,
        bytes: ByteArray,
        previousVersion: String?,
    ): RemoteFile

    /**
     * Permanently removes (or trashes) the file [fileId]. Optional capability — the default throws,
     * so stores that do not support deletion (or milestones that do not wire it) need not implement
     * it.
     */
    public suspend fun delete(fileId: String): Unit = throw UnsupportedOperationException("delete is not supported by this store")
}
