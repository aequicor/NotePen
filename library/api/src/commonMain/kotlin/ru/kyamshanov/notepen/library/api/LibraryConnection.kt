package ru.kyamshanov.notepen.library.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The persisted specification of how to connect a single library backend.
 *
 * This is the durable connection spec stored across app restarts (see [LibraryRegistry.savedConnections]
 * and [LibraryConnectionStore]). Each variant is a plain `@Serializable` data class so it persists
 * as JSON; no platform or transport types leak into it.
 *
 * Persisted as a polymorphic sealed hierarchy: each variant carries a stable [SerialName]
 * discriminator (`local` / `peer_lan` / `github` / `cloud`). New variants must keep their existing
 * discriminator, and new properties must be added with defaults, to stay backward-compatible with
 * connection files written by older app versions.
 */
@Serializable
public sealed interface LibraryConnection {
    /**
     * A library rooted in a local filesystem folder.
     *
     * @property rootPath absolute path to the library root folder; also the library's stable identity
     *   (`local:<rootPath>`). The display name is NOT part of identity, so a folder can be renamed in
     *   place.
     * @property displayName user-chosen library name shown in the UI. Defaults to `""` so connection
     *   files written by older app versions (which stored only [rootPath]) still deserialize; a blank
     *   value falls back to the folder's basename when the library descriptor is built.
     */
    @Serializable
    @SerialName("local")
    public data class Local(
        public val rootPath: String,
        public val displayName: String = "",
    ) : LibraryConnection

    /**
     * A library shared by a peer over the local network.
     *
     * When connected via a QR code (see `ru.kyamshanov.notepen.qrconnect.domain.PairingUri`), the
     * spec captures everything needed to re-dial the host directly on the next launch — host, port
     * and the pairing code — so reconnection bypasses mDNS entirely (the discovery path that fails
     * under VPN / AP isolation). When discovered via mDNS instead, [port]/[pairingCode] are null and
     * [libraryId] is blank, reproducing the pre-QR behaviour.
     *
     * @property peerId stable identifier of the peer that hosts the library.
     * @property host last-known network host/address of the peer, or `null` to rediscover via mDNS.
     * @property port last-known port of the peer's sync server, or `null` when only mDNS rediscovery
     *   is possible. Together with [host] and [pairingCode] this enables a direct reconnect.
     * @property libraryId id of the specific named library on the host this connection targets, or
     *   blank to project the host's whole shared shelf (the mDNS / pre-feature behaviour). Matches
     *   `RemoteEntry.libraryId`; the library backend filters the peer's catalog to this id.
     * @property libraryName human-readable name of the targeted library, captured from the QR so the
     *   shelf tile is labelled correctly before the host's catalog arrives. Blank when [libraryId] is.
     * @property pairingCode the host pairing code captured from the QR, used to re-authenticate a
     *   direct reconnect without re-scanning. `null` for mDNS-discovered connections. Persisted in
     *   plaintext alongside the spec (same posture as the GitHub token above).
     */
    @Serializable
    @SerialName("peer_lan")
    public data class PeerLan(
        public val peerId: String,
        public val host: String? = null,
        public val port: Int? = null,
        public val libraryId: String = "",
        public val libraryName: String = "",
        public val pairingCode: String? = null,
    ) : LibraryConnection

    /**
     * A library backed by a GitHub repository.
     *
     * @property repo the `owner/name` repository slug.
     * @property token an OAuth/PAT token for authenticated (write-capable) access, or `null` for
     *   anonymous read-only access. Stored as part of the persisted spec.
     */
    @Serializable
    @SerialName("github")
    public data class GitHub(
        public val repo: String,
        public val token: String? = null,
    ) : LibraryConnection

    /**
     * A library backed by a generic cloud storage provider (first provider: Google Drive).
     *
     * @property providerId identifier of the cloud provider (e.g. `google_drive`).
     * @property accountId account or root identifier within the provider. For Google Drive this is
     *   the **folder id** of the shared folder used as the book shelf.
     * @property refreshToken an OAuth refresh token granting durable access, or `null` for stores
     *   that need no per-user auth. Persisted as part of the spec (plaintext, like the GitHub token).
     * @property scope the OAuth scope the [refreshToken] was granted (e.g. `drive.readonly` →
     *   Reader, a write scope → Librarian), or `null` when not applicable. Backends derive the role
     *   from it. New fields default so older connection files stay deserializable.
     */
    @Serializable
    @SerialName("cloud")
    public data class Cloud(
        public val providerId: String,
        public val accountId: String,
        public val refreshToken: String? = null,
        public val scope: String? = null,
    ) : LibraryConnection
}
