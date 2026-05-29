package ru.kyamshanov.notepen.document.domain

import java.security.MessageDigest

actual fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return buildString(digest.size * 2) {
        for (b in digest) {
            val v = b.toInt() and 0xFF
            append(HEX_CHARS[v ushr 4])
            append(HEX_CHARS[v and 0x0F])
        }
    }
}

private const val HEX_CHARS = "0123456789abcdef"
