package ru.kyamshanov.notepen.sync.domain

/**
 * Derives a stable, cross-device document identifier from a local file path.
 *
 * **Phase 1 implementation**: uses the file name only — both the host and the
 * tablet preserve `fileName` through [`WebSocketFileTransfer`][ru.kyamshanov.notepen.sync.infrastructure.WebSocketFileTransfer]
 * → [`FileTransferReceiver`][ru.kyamshanov.notepen.sync.infrastructure.FileTransferReceiver],
 * so the same logical document yields the same id on both sides.
 *
 * **Phase 2/3 will replace this with a SHA-256 prefix** computed from the
 * file bytes, plus a host-side `document_hash(uri, sha256, mtime)` cache.
 * The signature is kept narrow so call sites don't need to change later.
 */
fun documentIdFromFilePath(filePath: String): String =
    filePath.substringAfterLast('/').substringAfterLast('\\')
