package ru.kyamshanov.notepen.sync.infrastructure

import java.io.File
import java.util.Base64

actual fun okio_readBytes(path: String): ByteArray = File(path).readBytes()

actual fun okio_writeBytes(path: String, bytes: ByteArray) {
    val f = File(path)
    f.parentFile?.mkdirs()
    f.writeBytes(bytes)
}

actual fun encodeBase64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

actual fun decodeBase64(encoded: String): ByteArray = Base64.getDecoder().decode(encoded)
