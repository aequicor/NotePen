package ru.kyamshanov.notepen.sync.domain

import ru.kyamshanov.notepen.sync.domain.port.LibraryManifestProvider

/**
 * One named library the host publishes over LAN.
 *
 * A host may share several libraries at once; [RemoteCatalogProvider] builds a single per-peer
 * [ru.kyamshanov.notepen.sync.domain.model.RemoteCatalog] that is the union of all shared sources,
 * tagging each book with the [libraryId] it came from. A client that connected to a specific library
 * (via a per-library QR) keeps only the entries whose `libraryId` matches; a client with no library
 * scope keeps them all (the mDNS / pre-feature behaviour).
 *
 * **Security / scope (v1, deliberate):** the per-library QR selects which library the *client*
 * foregrounds, but the host serves this UNION to every paired peer — so pairing exposes (and
 * authorises streaming of) **every** library the operator has shared over LAN, not only the one whose
 * QR was scanned. The per-library tag is therefore an advisory display filter on the client, **not**
 * a host-side access boundary. True per-peer scoping would require keying the catalog cache by
 * `(peer, library)` and is deferred. Consequence: only share libraries you are willing to expose to
 * any peer you approve. Pairing still requires the host's code **and** an explicit operator approval,
 * so this is exposure-to-approved-peers, not an open endpoint.
 *
 * @property libraryId stable, opaque id of this library on the host (e.g. the host's
 *   `local:<rootPath>` library id). Blank denotes a single anonymous shelf — then no named library is
 *   advertised and every entry is left untagged, exactly reproducing the pre-feature wire shape.
 * @property displayName human-readable library name, advertised so a client can label its shelf tile.
 * @property manifestProvider source of this library's books and the id→path resolution used when
 *   streaming a document. Each shared library has its own provider scoped to its own root.
 */
class SharedLibrarySource(
    val libraryId: String,
    val displayName: String,
    val manifestProvider: LibraryManifestProvider,
)
