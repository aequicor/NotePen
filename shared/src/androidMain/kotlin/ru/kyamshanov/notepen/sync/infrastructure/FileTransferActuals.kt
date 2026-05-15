package ru.kyamshanov.notepen.sync.infrastructure

import android.util.Base64
import java.io.File

actual fun okio_readBytes(path: String): ByteArray = File(path).readBytes()

actual fun encodeBase64(bytes: ByteArray): String =
    Base64.encodeToString(bytes, Base64.NO_WRAP)

actual fun decodeBase64(encoded: String): ByteArray =
    Base64.decode(encoded, Base64.NO_WRAP)
