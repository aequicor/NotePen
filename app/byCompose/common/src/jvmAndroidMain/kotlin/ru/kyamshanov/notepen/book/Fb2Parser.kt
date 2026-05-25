package ru.kyamshanov.notepen.book

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.zip.ZipInputStream

/**
 * Разбирает FictionBook 2 (FB2) — XML-формат — в платформенно-нейтральный
 * [BookContent].
 *
 * Поддержано: метаданные (`title-info`), вложенные `section`/`title` →
 * заголовки с уровнем по глубине, `p`, `subtitle`, `cite`/`epigraph` → цитаты,
 * `poem`/`stanza`/`v` → строки, `table`, `image` + `binary` (base64) →
 * растровые картинки. Инлайн-разметка (`strong`/`emphasis`/`a`/…) собирается
 * общим [inlineSpansOf].
 *
 * FB2 часто кодируется в windows-1251 — кодировка читается из XML-пролога.
 * Допускается обёртка `.fb2.zip` (ZIP с одним `.fb2`).
 *
 * Чистая CPU-операция без собственной диспетчеризации — вызывающая сторона
 * обязана выполнять её на IO/Default диспетчере.
 */
object Fb2Parser {
    private const val MAX_HEADING_LEVEL = 6
    private const val PROLOG_SCAN_BYTES = 200

    /**
     * @param bytes содержимое `.fb2` или `.fb2.zip`
     * @return разобранная книга
     * @throws IllegalArgumentException если это не валидный FB2
     */
    fun parse(bytes: ByteArray): BookContent {
        val xml = unwrapZip(bytes)
        val doc = Jsoup.parse(String(xml, detectCharset(xml)), "", Parser.xmlParser())
        val binaries = readBinaries(doc)
        val body =
            doc.select("body").firstOrNull { it.attr("name") != "notes" }
                ?: doc.selectFirst("body")
                ?: throw IllegalArgumentException("Not a valid FB2: no <body>")

        val blocks = mutableListOf<ContentBlock>()
        for (child in body.children()) {
            when (child.normalName()) {
                "title" -> appendTitle(child, level = 1, out = blocks)
                "epigraph" -> appendQuote(child, blocks)
                "section" -> appendSection(child, depth = 1, binaries = binaries, out = blocks)
                "image" -> appendImage(child, binaries, blocks)
                else -> Unit
            }
        }
        return BookContent(metadata = readMetadata(doc), blocks = blocks)
    }

    private fun appendSection(
        section: Element,
        depth: Int,
        binaries: Map<String, ContentBlock.Image>,
        out: MutableList<ContentBlock>,
    ) {
        if (depth == 1 && out.isNotEmpty()) out.add(ContentBlock.PageBreak)
        for (child in section.children()) {
            when (child.normalName()) {
                "title" -> appendTitle(child, level = depth.coerceAtMost(MAX_HEADING_LEVEL), out = out)
                "subtitle" -> appendTitle(child, level = (depth + 1).coerceAtMost(MAX_HEADING_LEVEL), out = out)
                "section" -> appendSection(child, depth + 1, binaries, out)
                "p" -> inlineSpansOf(child).takeIf { it.isNotEmpty() }?.let { out.add(ContentBlock.Paragraph(it)) }
                "cite", "epigraph" -> appendQuote(child, out)
                "poem" -> appendPoem(child, out)
                "table" -> appendTable(child, out)
                "image" -> appendImage(child, binaries, out)
                "annotation" -> appendParagraphs(child, out)
                else -> Unit
            }
        }
    }

    /** `title`/`subtitle` могут содержать несколько `p` — склеиваем в один заголовок. */
    private fun appendTitle(
        title: Element,
        level: Int,
        out: MutableList<ContentBlock>,
    ) {
        val text =
            title.select("p").joinToString(separator = " ") { it.text().trim() }
                .ifBlank { title.text().trim() }
        if (text.isNotBlank()) out.add(ContentBlock.Heading(level = level, text = text))
    }

    private fun appendQuote(
        quote: Element,
        out: MutableList<ContentBlock>,
    ) {
        for (line in quote.children().filter { it.normalName() == "p" || it.normalName() == "v" }) {
            inlineSpansOf(line).takeIf { it.isNotEmpty() }?.let { out.add(ContentBlock.Blockquote(it)) }
        }
    }

    private fun appendPoem(
        poem: Element,
        out: MutableList<ContentBlock>,
    ) {
        poem.selectFirst("title")?.let { appendTitle(it, level = MAX_HEADING_LEVEL, out = out) }
        for (verse in poem.select("stanza > v")) {
            inlineSpansOf(verse).takeIf { it.isNotEmpty() }?.let { out.add(ContentBlock.Paragraph(it)) }
        }
    }

    private fun appendParagraphs(
        element: Element,
        out: MutableList<ContentBlock>,
    ) {
        for (p in element.select("p")) {
            inlineSpansOf(p).takeIf { it.isNotEmpty() }?.let { out.add(ContentBlock.Paragraph(it)) }
        }
    }

    private fun appendTable(
        table: Element,
        out: MutableList<ContentBlock>,
    ) {
        val rows =
            table.select("tr").map { tr ->
                tr.children().filter { it.normalName() == "th" || it.normalName() == "td" }.map { it.text().trim() }
            }.filter { it.isNotEmpty() }
        if (rows.isNotEmpty()) out.add(ContentBlock.Table(rows))
    }

    private fun appendImage(
        image: Element,
        binaries: Map<String, ContentBlock.Image>,
        out: MutableList<ContentBlock>,
    ) {
        val href = imageHref(image) ?: return
        binaries[href.removePrefix("#")]?.let { out.add(it) }
    }

    private fun imageHref(image: Element): String? =
        image.attr("l:href").ifBlank { image.attr("xlink:href").ifBlank { image.attr("href") } }
            .takeIf { it.isNotBlank() }

    // --- Metadata & binaries ------------------------------------------------

    private fun readMetadata(doc: Document): BookMetadata {
        val titleInfo = doc.selectFirst("title-info")
        return BookMetadata(
            title = titleInfo?.selectFirst("book-title")?.text()?.trim()?.ifBlank { null },
            author = titleInfo?.selectFirst("author")?.let(::authorName),
            language = titleInfo?.selectFirst("lang")?.text()?.trim()?.ifBlank { null },
            identifier = doc.selectFirst("document-info > id")?.text()?.trim()?.ifBlank { null },
        )
    }

    private fun authorName(author: Element): String? {
        val parts =
            listOf("first-name", "middle-name", "last-name")
                .mapNotNull { author.selectFirst(it)?.text()?.trim()?.ifBlank { null } }
        return parts.joinToString(separator = " ")
            .ifBlank { author.selectFirst("nickname")?.text()?.trim()?.ifBlank { null } }
    }

    private fun readBinaries(doc: Document): Map<String, ContentBlock.Image> =
        doc.select("binary").mapNotNull { binary ->
            val id = binary.attr("id").ifBlank { return@mapNotNull null }
            val mime = binary.attr("content-type").ifBlank { "image/jpeg" }
            val data = decodeBase64(binary.wholeText()).takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            id to ContentBlock.Image(data = data, mimeType = mime)
        }.toMap()

    // --- Bytes, charset, base64 ---------------------------------------------

    private fun unwrapZip(bytes: ByteArray): ByteArray {
        if (bytes.size < 4 || bytes[0] != 'P'.code.toByte() || bytes[1] != 'K'.code.toByte()) return bytes
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var firstEntry: ByteArray? = null
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val content = zip.readBytes()
                    if (entry.name.endsWith(".fb2", ignoreCase = true)) return content
                    if (firstEntry == null) firstEntry = content
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
            return firstEntry ?: throw IllegalArgumentException("Not a valid FB2: empty ZIP")
        }
    }

    private val ENCODING_REGEX = Regex("""encoding=["']([^"']+)["']""", RegexOption.IGNORE_CASE)

    private fun detectCharset(bytes: ByteArray): Charset {
        val prolog = String(bytes, 0, minOf(bytes.size, PROLOG_SCAN_BYTES), Charsets.ISO_8859_1)
        val name = ENCODING_REGEX.find(prolog)?.groupValues?.getOrNull(1)
        return runCatching { name?.let(Charset::forName) }.getOrNull() ?: Charsets.UTF_8
    }

    /** Минимальный base64-декодер: игнорирует пробелы/переводы строк, без внешних зависимостей. */
    private fun decodeBase64(input: String): ByteArray {
        val out = ByteArrayOutputStream(input.length * 3 / 4)
        var buffer = 0
        var bits = 0
        for (ch in input) {
            if (ch == '=') break
            val value = if (ch.code < BASE64_INVERSE.size) BASE64_INVERSE[ch.code] else -1
            if (value < 0) continue
            buffer = (buffer shl 6) or value
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.write((buffer shr bits) and 0xFF)
            }
        }
        return out.toByteArray()
    }
}

private const val BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

private val BASE64_INVERSE: IntArray =
    IntArray(128) { -1 }.also { table ->
        BASE64_ALPHABET.forEachIndexed { index, ch -> table[ch.code] = index }
    }
