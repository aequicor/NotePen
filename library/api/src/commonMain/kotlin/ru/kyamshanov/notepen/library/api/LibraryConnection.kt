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
     * @property rootPath absolute path to the library root folder.
     */
    @Serializable
    @SerialName("local")
    public data class Local(
        public val rootPath: String,
    ) : LibraryConnection

    /**
     * A library shared by a peer over the local network.
     *
     * @property peerId stable identifier of the peer that hosts the library.
     * @property host last-known network host/address of the peer, or `null` to rediscover via mDNS.
     */
    @Serializable
    @SerialName("peer_lan")
    public data class PeerLan(
        public val peerId: String,
        public val host: String? = null,
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
     * A library backed by a generic cloud storage provider.
     *
     * @property providerId identifier of the cloud provider (Drive/Dropbox/…).
     * @property accountId account or root identifier within the provider.
     */
    @Serializable
    @SerialName("cloud")
    public data class Cloud(
        public val providerId: String,
        public val accountId: String,
    ) : LibraryConnection
}
