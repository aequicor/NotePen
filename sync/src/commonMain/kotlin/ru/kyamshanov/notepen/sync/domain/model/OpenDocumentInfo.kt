package ru.kyamshanov.notepen.sync.domain.model

/**
 * A document currently open in an active editor tab on this device, advertised
 * to peers (via [RemoteCatalog.openDocuments]) so they can open the same
 * document remotely.
 *
 * @param documentId stable sync wire id — the same id used for stroke sync, so
 *   a peer that opens this document lands on the same [SyncEngine] document.
 * @param displayName tab title shown to the peer.
 * @param absolutePath host-local path used to stream the file when the peer
 *   sends a `DocumentOpenRequest`. Host-side only — never serialized to the wire.
 * @param fileSize size in bytes, if known.
 */
data class OpenDocumentInfo(
    val documentId: String,
    val displayName: String,
    val absolutePath: String,
    val fileSize: Long? = null,
)
