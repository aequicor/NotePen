package ru.kyamshanov.notepen

import ru.kyamshanov.notepen.annotation.domain.port.AnnotationRepository
import java.io.File
import java.security.MessageDigest

/**
 * Android can't write a sibling file next to a `content://` document URI, so the
 * annotation sidecar is stored in app-private storage keyed by a stable hash of
 * the document path. Real `file://`-style paths hash the same way — the location
 * is private either way, which is the expected behaviour on Android.
 */
actual fun createAnnotationRepository(): AnnotationRepository =
    AnnotationRepositoryJvmAndroid { pdfPath ->
        val dir = File(AppContextHolder.context.filesDir, "annotations").apply { mkdirs() }
        File(dir, "${pdfPath.sha256Hex()}.json")
    }

private fun String.sha256Hex(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(encodeToByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }
