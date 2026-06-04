package ru.kyamshanov.notepen.book

import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/**
 * Жёсткие лимиты на разбор недоверенных книжных контейнеров (EPUB/CBZ/FB2.zip).
 *
 * Архивы приходят из произвольных файлов пользователя, поэтому zip-бомба
 * (огромный коэффициент сжатия) или архив с гигантским/бесконечным числом
 * записей не должны приводить к OOM. Вместо этого распаковка прерывается
 * понятным [DocumentTooLargeException].
 *
 * Лимиты подобраны с большим запасом над реальными книгами: типичный EPUB —
 * единицы МБ, отдельная глава/изображение — сотни КБ.
 */
internal object BookLimits {
    /** Максимальный размер одной распакованной записи архива. */
    const val MAX_ENTRY_BYTES: Long = 64L * 1024 * 1024

    /** Максимальный суммарный размер всех распакованных записей одного архива. */
    const val MAX_TOTAL_BYTES: Long = 256L * 1024 * 1024

    /** Максимальное число записей в архиве (защита от «zip с миллионом файлов»). */
    const val MAX_ENTRY_COUNT: Int = 10_000

    /** Максимальное число страниц-изображений в комиксе (CBZ/CBR). */
    const val MAX_COMIC_IMAGES: Int = 5_000

    /** Максимальный размер недоверенного FB2-документа (после распаковки .fb2.zip). */
    const val MAX_FB2_BYTES: Long = 256L * 1024 * 1024
}

/**
 * Документ превышает допустимые лимиты разбора ([BookLimits]) — отвергаем его,
 * не доводя до OOM. Подтип [IllegalArgumentException]: вызывающая сторона уже
 * трактует невалидные книги как `IllegalArgumentException`.
 */
internal class DocumentTooLargeException(
    message: String,
) : IllegalArgumentException(message)

/**
 * Читает текущую запись [ZipInputStream] в память с ограничением [maxEntryBytes]:
 * как только распакованный размер превышает лимит, чтение прерывается
 * [DocumentTooLargeException] — то есть распаковка zip-бомбы останавливается
 * на пороге, а не материализует весь поток.
 *
 * @param entryName имя записи (для понятного сообщения об ошибке)
 */
internal fun ZipInputStream.readEntryBounded(
    entryName: String,
    maxEntryBytes: Long = BookLimits.MAX_ENTRY_BYTES,
): ByteArray {
    val buffer = ByteArray(DEFAULT_CHUNK_BYTES)
    val out = ByteArrayOutputStream()
    var total = 0L
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        if (total > maxEntryBytes) {
            throw DocumentTooLargeException(
                "Zip entry '$entryName' exceeds $maxEntryBytes bytes when inflated",
            )
        }
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}

private const val DEFAULT_CHUNK_BYTES = 64 * 1024
