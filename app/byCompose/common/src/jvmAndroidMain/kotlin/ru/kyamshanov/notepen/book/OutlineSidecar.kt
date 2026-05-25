package ru.kyamshanov.notepen.book

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Хранит оглавление книги в JSON-файле рядом с кешированным PDF (`<pdf>.toc.json`).
 * Оглавление собирается один раз при конвертации и читается при открытии.
 * Ошибки чтения/записи проглатываются — оглавление необязательно.
 */
internal object OutlineSidecar {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(TocEntry.serializer())

    fun pathFor(pdfPath: String): File = File("$pdfPath.toc.json")

    fun write(
        pdfPath: String,
        entries: List<TocEntry>,
    ) {
        runCatching { pathFor(pdfPath).writeText(json.encodeToString(serializer, entries)) }
    }

    fun read(pdfPath: String): List<TocEntry> =
        runCatching {
            val file = pathFor(pdfPath)
            if (file.exists()) json.decodeFromString(serializer, file.readText()) else emptyList()
        }.getOrDefault(emptyList())
}
