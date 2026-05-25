package ru.kyamshanov.notepen.book

import com.github.junrar.Archive
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/**
 * Извлекает изображения страниц из архива-комикса (CBZ — ZIP, CBR — RAR) в
 * порядке чтения (естественная сортировка имён). Каталоги и не-изображения
 * пропускаются.
 *
 * CBR обрабатывается через junrar (поддерживает RAR4); RAR5-архивы могут не
 * читаться — тогда вернётся пустой список, и конвертер отдаст пустой PDF.
 *
 * Чистая CPU/IO-операция без собственной диспетчеризации — вызывающая сторона
 * выполняет её на IO-диспетчере.
 */
internal object ComicArchive {
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

    fun extract(
        bytes: ByteArray,
        format: BookFormat,
    ): List<ByteArray> =
        when (format) {
            BookFormat.CBZ -> extractZip(bytes)
            BookFormat.CBR -> extractRar(bytes)
            else -> emptyList()
        }

    private fun extractZip(bytes: ByteArray): List<ByteArray> {
        val pages = mutableListOf<Pair<String, ByteArray>>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && isImage(entry.name)) pages.add(entry.name to zip.readBytes())
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return pages.sortedByPageName()
    }

    private fun extractRar(bytes: ByteArray): List<ByteArray> {
        val pages = mutableListOf<Pair<String, ByteArray>>()
        Archive(ByteArrayInputStream(bytes)).use { archive ->
            while (true) {
                val header = archive.nextFileHeader() ?: break
                if (header.isDirectory) continue
                val name = header.fileName
                if (!isImage(name)) continue
                val out = ByteArrayOutputStream()
                archive.extractFile(header, out)
                pages.add(name to out.toByteArray())
            }
        }
        return pages.sortedByPageName()
    }

    private fun List<Pair<String, ByteArray>>.sortedByPageName(): List<ByteArray> =
        sortedWith(compareBy(NATURAL_ORDER) { it.first }).map { it.second }

    private fun isImage(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS
}

/** Натуральная сортировка имён: `page2` идёт перед `page10` (числа сравниваются как числа). */
private val NATURAL_ORDER: Comparator<String> = Comparator { a, b -> naturalCompare(a, b) }

private fun naturalCompare(
    a: String,
    b: String,
): Int {
    var i = 0
    var j = 0
    while (i < a.length && j < b.length) {
        val ca = a[i]
        val cb = b[j]
        if (ca.isDigit() && cb.isDigit()) {
            val startA = i
            val startB = j
            while (i < a.length && a[i].isDigit()) i++
            while (j < b.length && b[j].isDigit()) j++
            val numA = a.substring(startA, i).trimStart('0').ifEmpty { "0" }
            val numB = b.substring(startB, j).trimStart('0').ifEmpty { "0" }
            if (numA.length != numB.length) return numA.length - numB.length
            val cmp = numA.compareTo(numB)
            if (cmp != 0) return cmp
        } else {
            val cmp = ca.lowercaseChar().compareTo(cb.lowercaseChar())
            if (cmp != 0) return cmp
            i++
            j++
        }
    }
    return (a.length - i) - (b.length - j)
}
