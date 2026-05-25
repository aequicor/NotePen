package ru.kyamshanov.notepen.book

/**
 * Форматы электронных книг, которые приложение умеет открывать, единожды
 * конвертируя в PDF. Перечисление — точка роста: новый формат добавляется
 * значением сюда, записью в таблицы [EXTENSION_FORMATS]/[MIME_FORMATS] и веткой
 * в [parseBook] (или в конвертере для image-only форматов).
 */
enum class BookFormat {
    /** EPUB — ZIP-контейнер с XHTML/OPF. */
    EPUB,

    /** FB2 (FictionBook 2) — XML; допускается обёртка `.fb2.zip`. */
    FB2,

    /** CBZ — ZIP-архив изображений (комикс). */
    CBZ,

    /** CBR — RAR-архив изображений (комикс). */
    CBR,
}

/** Расширение файла (без точки, в нижнем регистре) → формат. */
private val EXTENSION_FORMATS: Map<String, BookFormat> = mapOf(
    "epub" to BookFormat.EPUB,
    "fb2" to BookFormat.FB2,
    "cbz" to BookFormat.CBZ,
    "cbr" to BookFormat.CBR,
)

/** MIME-тип (в нижнем регистре) → формат. */
private val MIME_FORMATS: Map<String, BookFormat> = mapOf(
    "application/epub+zip" to BookFormat.EPUB,
    "application/epub" to BookFormat.EPUB,
    "application/x-fictionbook+xml" to BookFormat.FB2,
    "application/x-fictionbook" to BookFormat.FB2,
    "application/fb2+zip" to BookFormat.FB2,
    "application/vnd.comicbook+zip" to BookFormat.CBZ,
    "application/vnd.comicbook-rar" to BookFormat.CBR,
    "application/x-cbz" to BookFormat.CBZ,
    "application/x-cbr" to BookFormat.CBR,
)

/**
 * Определяет формат книги по пути/URI и (опционально) MIME-типу, без чтения
 * содержимого — годится как быстрая проверка на обеих платформах.
 *
 * Если [mimeType] передан и распознан, он имеет приоритет; иначе (в т.ч. при
 * generic-MIME вроде `application/octet-stream`) решение принимается по
 * расширению пути.
 *
 * @param path путь к файлу или URI
 * @param mimeType MIME-тип источника, если известен (Android `content://`)
 * @return формат книги или `null`, если источник не является поддерживаемой книгой
 */
fun detectBookFormat(path: String, mimeType: String? = null): BookFormat? {
    mimeType?.lowercase()?.let { mime -> MIME_FORMATS[mime]?.let { return it } }
    val clean = path.substringBefore('?').substringBefore('#').lowercase()
    if (clean.endsWith(".fb2.zip")) return BookFormat.FB2
    return EXTENSION_FORMATS[clean.substringAfterLast('.', "")]
}
