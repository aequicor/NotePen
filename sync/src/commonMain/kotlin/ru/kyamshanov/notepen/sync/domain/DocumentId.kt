package ru.kyamshanov.notepen.sync.domain

/**
 * Stable cross-device document identifier derived from a local file path.
 *
 * Wire form: `<basename>#<8-hex-FNV1a-of-full-path>`. The hash suffix
 * disambiguates files with the same basename living in different folders
 * (например, `~/docs/book.pdf` и `~/downloads/book.pdf`) — без него host
 * не мог адресовать оба файла одновременно, и UI на клиенте падал из-за
 * коллизии ключей в LazyGrid.
 *
 * Note: hash зависит от полного пути, поэтому host и tablet, у которых файл
 * лежит по разным путям, получат разные documentId, если вычислять локально.
 * Поэтому **на tablet'е для remote-кешированных файлов нужно использовать
 * documentId, полученный с хоста** (через `LocalDocumentIdRegistry`), а не
 * вычислять заново из локального пути.
 */
fun documentIdFromFilePath(filePath: String): String {
    val basename = filePath.substringAfterLast('/').substringAfterLast('\\')
    val hash = fnv1a32Hex(filePath)
    return "$basename#$hash"
}

/**
 * Sanitised cache-filename form of [documentId] — заменяет `#` и сепараторы
 * пути на `_`, чтобы получился безопасный для FS компонент. Используется
 * `RemoteDocumentOpener` / `FileTransferReceiver` для уникального имени
 * кеш-копии при одинаковом `displayName`.
 */
fun documentIdToCacheFileName(
    documentId: String,
    displayName: String,
): String {
    val sanitisedDocId =
        documentId
            .replace('#', '_')
            .replace('/', '_')
            .replace('\\', '_')
    return "${sanitisedDocId}__$displayName"
}

/**
 * FNV-1a 32-bit hash, 8 hex chars. Без зависимости от платформенного MessageDigest
 * — работает на любом KMP-target.
 */
private fun fnv1a32Hex(input: String): String {
    var hash = 0x811C9DC5u
    val bytes = input.encodeToByteArray()
    for (b in bytes) {
        hash = hash xor (b.toInt() and 0xFF).toUInt()
        hash *= 0x01000193u
    }
    return hash.toString(16).padStart(8, '0')
}
