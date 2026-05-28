package ru.kyamshanov.notepen.reflow

import java.io.File

internal actual fun statReflowSource(path: String): SourceStat? {
    val file = File(path)
    if (!file.exists() || !file.isFile) return null
    return SourceStat(size = file.length(), mtime = file.lastModified())
}

internal actual fun reflowCacheDir(): String {
    val home =
        System.getProperty("user.home")
            ?: System.getProperty("java.io.tmpdir")
            ?: "."
    return File(home, ".notepen/reflow-cache").absolutePath
}
