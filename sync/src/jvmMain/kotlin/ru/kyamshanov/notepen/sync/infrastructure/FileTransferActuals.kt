package ru.kyamshanov.notepen.sync.infrastructure

import java.io.File
import java.util.Base64

actual fun okio_readBytes(path: String): ByteArray = File(path).readBytes()

actual fun okio_writeBytes(
    path: String,
    bytes: ByteArray,
) {
    val f = File(path)
    f.parentFile?.mkdirs()
    f.writeBytes(bytes)
}

actual fun okio_exists(path: String): Boolean = File(path).isFile

actual fun okio_delete(path: String): Boolean {
    val f = File(path)
    return !f.exists() || f.delete()
}

actual fun okio_tempFilePath(suffix: String): String {
    val f = File.createTempFile("notepen-upload-", suffix)
    // We only need the path; the receiver rewrites the file. Delete the empty
    // placeholder so okio_writeBytes recreates it cleanly.
    f.delete()
    return f.absolutePath
}

actual fun encodeBase64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

actual fun decodeBase64(encoded: String): ByteArray = Base64.getDecoder().decode(encoded)
