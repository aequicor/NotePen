package ru.kyamshanov.notepen.book

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Разбирает контейнер EPUB (ZIP с XHTML/OPF) в платформенно-нейтральный
 * [BookContent]: читает `META-INF/container.xml` → OPF → порядок `spine`, после
 * чего превращает каждый XHTML-документ в поток [ContentBlock] в порядке чтения.
 *
 * Инлайн-разметка собирается общим [inlineSpansOf]. Разбор HTML устойчив к
 * нестрогой разметке (используется толерантный парсер jsoup). Изображения
 * извлекаются из контейнера по `href` манифеста.
 *
 * Чистая CPU-операция без собственной диспетчеризации — вызывающая сторона
 * обязана выполнять её на IO/Default диспетчере.
 */
object EpubParser {
    /**
     * @param epubBytes полное содержимое файла `.epub`
     * @return разобранная книга
     * @throws IllegalArgumentException если контейнер не является валидным EPUB
     */
    fun parse(epubBytes: ByteArray): BookContent {
        val entries = readZipEntries(epubBytes)
        val opfPath = locateOpfPath(entries)
        val opfBytes = requireNotNull(entries[opfPath]) { "EPUB OPF not found: $opfPath" }
        val opf = parseXml(opfBytes)
        val opfDir = parentDir(opfPath)

        val metadata = readMetadata(opf)
        val hrefById = readManifest(opf)
        val spinePaths = readSpine(opf, hrefById, opfDir)
        require(spinePaths.isNotEmpty()) { "EPUB spine is empty" }

        val blocks = buildBlocks(spinePaths, entries)
        val fonts = readFonts(opf, opfDir, entries)
        return BookContent(metadata = metadata, blocks = blocks, fonts = fonts)
    }

    private fun buildBlocks(
        spinePaths: List<String>,
        entries: Map<String, ByteArray>,
    ): List<ContentBlock> {
        val blocks = mutableListOf<ContentBlock>()
        for (path in spinePaths) {
            val bytes = entries[path] ?: continue
            if (blocks.isNotEmpty()) blocks.add(ContentBlock.PageBreak)
            val doc = Jsoup.parse(String(bytes, Charsets.UTF_8), path)
            val body = doc.body()
            val context = DocumentContext(baseDir = parentDir(path), entries = entries)
            appendBlocks(body, context, blocks)
        }
        return blocks
    }

    // --- ZIP ----------------------------------------------------------------

    private fun readZipEntries(bytes: ByteArray): Map<String, ByteArray> {
        val result = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    result[normalize(entry.name)] = zip.readBytes()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        require(result.isNotEmpty()) { "Not a valid EPUB: empty or unreadable ZIP" }
        return result
    }

    private fun locateOpfPath(entries: Map<String, ByteArray>): String {
        val containerBytes =
            entries["META-INF/container.xml"]
                ?: throw IllegalArgumentException("Not a valid EPUB: missing META-INF/container.xml")
        val container = parseXml(containerBytes)
        val fullPath = container.selectFirst("rootfile")?.attr("full-path").orEmpty()
        require(fullPath.isNotBlank()) { "Not a valid EPUB: container.xml has no rootfile" }
        return normalize(percentDecode(fullPath))
    }

    // --- OPF ----------------------------------------------------------------

    private fun readMetadata(opf: Document): BookMetadata =
        BookMetadata(
            title = firstTagText(opf, "dc:title", "title"),
            author = firstTagText(opf, "dc:creator", "creator"),
            language = firstTagText(opf, "dc:language", "language"),
            identifier = firstTagText(opf, "dc:identifier", "identifier"),
        )

    /** id → resolved-relative href (percent-decoded, not yet joined to opfDir). */
    private fun readManifest(opf: Document): Map<String, String> =
        opf
            .select("manifest > item")
            .mapNotNull { item ->
                val id = item.attr("id")
                val href = item.attr("href")
                if (id.isBlank() || href.isBlank()) null else id to percentDecode(href)
            }.toMap()

    private fun readSpine(
        opf: Document,
        hrefById: Map<String, String>,
        opfDir: String,
    ): List<String> =
        opf.select("spine > itemref").mapNotNull { ref ->
            val idref = ref.attr("idref")
            val href = hrefById[idref] ?: return@mapNotNull null
            resolvePath(opfDir, href)
        }

    private fun firstTagText(
        doc: Document,
        vararg tags: String,
    ): String? {
        for (tag in tags) {
            val text =
                doc
                    .getElementsByTag(tag)
                    .firstOrNull()
                    ?.text()
                    ?.trim()
            if (!text.isNullOrBlank()) return text
        }
        return null
    }

    // --- Fonts --------------------------------------------------------------

    private val FONT_MEDIA_TYPES =
        setOf(
            "application/vnd.ms-opentype",
            "application/font-sfnt",
            "application/x-font-ttf",
            "application/x-font-otf",
            "application/font-ttf",
            "application/font-otf",
            "font/ttf",
            "font/otf",
        )

    private const val IDPF_ALGORITHM = "http://www.idpf.org/2008/embedding"
    private const val ADOBE_ALGORITHM = "http://ns.adobe.com/pdf/enc#RC"
    private const val IDPF_PREFIX = 1040
    private const val ADOBE_PREFIX = 1024
    private const val UUID_HEX_LENGTH = 32

    /**
     * Извлекает встроенные шрифты (TTF/OTF) из манифеста и снимает обфускацию
     * (IDPF/Adobe) по `META-INF/encryption.xml`. WOFF/WOFF2 и шрифты с неизвестным
     * алгоритмом шифрования пропускаются как непригодные к прямой загрузке.
     */
    private fun readFonts(
        opf: Document,
        opfDir: String,
        entries: Map<String, ByteArray>,
    ): List<ByteArray> {
        val obfuscation = readObfuscation(entries)
        val idpf by lazy { idpfKey(opf) }
        val adobe by lazy { adobeKey(opf) }
        return opf.select("manifest > item").mapNotNull { item ->
            if (item.attr("media-type").lowercase() !in FONT_MEDIA_TYPES) return@mapNotNull null
            val href = item.attr("href").ifBlank { return@mapNotNull null }
            val path = resolvePath(opfDir, percentDecode(href))
            val raw = entries[path] ?: return@mapNotNull null
            when (obfuscation[path]) {
                IDPF_ALGORITHM -> idpf?.let { deobfuscate(raw, it, IDPF_PREFIX) } ?: raw
                ADOBE_ALGORITHM -> adobe?.let { deobfuscate(raw, it, ADOBE_PREFIX) } ?: raw
                else -> raw
            }
        }
    }

    /** normalized path → algorithm URI из `META-INF/encryption.xml`. */
    private fun readObfuscation(entries: Map<String, ByteArray>): Map<String, String> {
        val bytes = entries["META-INF/encryption.xml"] ?: return emptyMap()
        val doc = parseXml(bytes)
        val result = mutableMapOf<String, String>()
        for (data in doc.byLocalName("EncryptedData")) {
            val algorithm = data.firstByLocalName("EncryptionMethod")?.attr("Algorithm").orEmpty()
            val uri = data.firstByLocalName("CipherReference")?.attr("URI").orEmpty()
            if (algorithm.isNotBlank() && uri.isNotBlank()) {
                result[normalize(percentDecode(uri))] = algorithm
            }
        }
        return result
    }

    /** Уникальный идентификатор книги (на него ссылается `package@unique-identifier`). */
    private fun uniqueIdentifier(opf: Document): String? {
        val idRef = opf.selectFirst("package")?.attr("unique-identifier").orEmpty()
        val identifiers = opf.byLocalName("identifier")
        val match =
            identifiers.firstOrNull { idRef.isNotBlank() && it.attr("id") == idRef }
                ?: identifiers.firstOrNull()
        return match?.text()?.trim()?.ifBlank { null }
    }

    /** Ключ IDPF: SHA-1 от unique-identifier без пробелов. */
    private fun idpfKey(opf: Document): ByteArray? {
        val uid = uniqueIdentifier(opf) ?: return null
        val normalized = uid.filterNot { it.isWhitespace() }
        return MessageDigest.getInstance("SHA-1").digest(normalized.toByteArray(Charsets.UTF_8))
    }

    /** Ключ Adobe: 16 байт из UUID unique-identifier. */
    private fun adobeKey(opf: Document): ByteArray? {
        val uid = uniqueIdentifier(opf) ?: return null
        val hex = uid.substringAfterLast(':').replace("-", "").trim()
        if (hex.length < UUID_HEX_LENGTH) return null
        return runCatching {
            ByteArray(16) { i -> hex.substring(i * 2, i * 2 + 2).toInt(radix = 16).toByte() }
        }.getOrNull()
    }

    private fun deobfuscate(
        data: ByteArray,
        key: ByteArray,
        prefixLength: Int,
    ): ByteArray {
        if (key.isEmpty()) return data
        val out = data.copyOf()
        val limit = minOf(prefixLength, out.size)
        for (i in 0 until limit) {
            out[i] = (out[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
        return out
    }

    private fun Document.byLocalName(local: String): List<Element> = allElements.filter { it.tagName().substringAfterLast(':') == local }

    private fun Element.firstByLocalName(local: String): Element? =
        allElements.firstOrNull { it !== this && it.tagName().substringAfterLast(':') == local }

    // --- XHTML → blocks -----------------------------------------------------

    private class DocumentContext(
        val baseDir: String,
        val entries: Map<String, ByteArray>,
    )

    private val HEADINGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")
    private val CONTAINERS = setOf("div", "section", "article", "main", "header", "footer", "figure", "aside")

    private fun appendBlocks(
        parent: Element,
        ctx: DocumentContext,
        out: MutableList<ContentBlock>,
    ) {
        for (child in parent.children()) {
            when (val tag = child.normalName()) {
                in HEADINGS ->
                    child
                        .text()
                        .trim()
                        .takeIf { it.isNotBlank() }
                        ?.let { out.add(ContentBlock.Heading(level = tag.substring(1).toInt(), text = it)) }

                "p", "pre" -> appendTextAndImages(child, ctx, out)
                "blockquote" -> {
                    inlineSpansOf(child).takeIf { it.isNotEmpty() }?.let { out.add(ContentBlock.Blockquote(it)) }
                    appendImages(child, ctx, out)
                }

                "ul", "ol" -> appendListItems(child, ordered = tag == "ol", level = 0, ctx = ctx, out = out)
                "table" -> appendTable(child, out)
                "hr" -> out.add(ContentBlock.HorizontalRule)
                "img", "image" -> appendImageElement(child, ctx, out)
                in CONTAINERS, "body" -> appendBlocks(child, ctx, out)
                else ->
                    if (child.children().isEmpty()) {
                        inlineSpansOf(child).takeIf { it.isNotEmpty() }?.let { out.add(ContentBlock.Paragraph(it)) }
                    } else {
                        appendBlocks(child, ctx, out)
                    }
            }
        }
    }

    private fun appendTextAndImages(
        element: Element,
        ctx: DocumentContext,
        out: MutableList<ContentBlock>,
    ) {
        inlineSpansOf(element).takeIf { it.isNotEmpty() }?.let { out.add(ContentBlock.Paragraph(it)) }
        appendImages(element, ctx, out)
    }

    private fun appendImages(
        element: Element,
        ctx: DocumentContext,
        out: MutableList<ContentBlock>,
    ) {
        for (img in element.select("img, image")) appendImageElement(img, ctx, out)
    }

    private fun appendListItems(
        list: Element,
        ordered: Boolean,
        level: Int,
        ctx: DocumentContext,
        out: MutableList<ContentBlock>,
    ) {
        var ordinal = 1
        for (li in list.children().filter { it.normalName() == "li" }) {
            val nested = li.children().filter { it.normalName() == "ul" || it.normalName() == "ol" }
            val ownText =
                inlineSpansOf(
                    li.clone().also { clone -> clone.select("ul, ol").remove() },
                )
            if (ownText.isNotEmpty()) {
                out.add(ContentBlock.ListItem(text = ownText, ordered = ordered, ordinal = ordinal, level = level))
            }
            ordinal++
            for (sub in nested) {
                appendListItems(sub, ordered = sub.normalName() == "ol", level = level + 1, ctx = ctx, out = out)
            }
        }
    }

    private fun appendTable(
        table: Element,
        out: MutableList<ContentBlock>,
    ) {
        val rows =
            table
                .select("tr")
                .map { tr ->
                    tr.select("th, td").map { it.text().trim() }
                }.filter { it.isNotEmpty() }
        if (rows.isNotEmpty()) out.add(ContentBlock.Table(rows))
    }

    private fun appendImageElement(
        element: Element,
        ctx: DocumentContext,
        out: MutableList<ContentBlock>,
    ) {
        val rawSrc =
            element.attr("src").ifBlank {
                element.attr("xlink:href").ifBlank { element.attr("href") }
            }
        if (rawSrc.isBlank()) return
        val path = resolvePath(ctx.baseDir, percentDecode(rawSrc.substringBefore('#').substringBefore('?')))
        val data = ctx.entries[path] ?: return
        out.add(
            ContentBlock.Image(
                data = data,
                mimeType = guessImageMime(path),
                alt = element.attr("alt").trim().ifBlank { null },
            ),
        )
    }

    // --- Path & misc helpers ------------------------------------------------

    private fun parseXml(bytes: ByteArray): Document = Jsoup.parse(String(bytes, Charsets.UTF_8), "", Parser.xmlParser())

    private fun normalize(name: String): String = name.replace('\\', '/').removePrefix("./").trimStart('/')

    private fun parentDir(path: String): String = path.substringBeforeLast('/', missingDelimiterValue = "")

    /** Joins [baseDir] and a relative [href], collapsing `.`/`..` segments. */
    private fun resolvePath(
        baseDir: String,
        href: String,
    ): String {
        if (href.startsWith('/')) return normalize(href)
        val segments = ArrayDeque<String>()
        baseDir.split('/').filter { it.isNotBlank() }.forEach { segments.addLast(it) }
        for (segment in href.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (segments.isNotEmpty()) segments.removeLast()
                else -> segments.addLast(segment)
            }
        }
        return segments.joinToString("/")
    }

    private fun guessImageMime(path: String): String =
        when (path.substringAfterLast('.', "").lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            "bmp" -> "image/bmp"
            else -> "application/octet-stream"
        }

    private fun percentDecode(value: String): String {
        if (!value.contains('%')) return value
        val out = java.io.ByteArrayOutputStream(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            val hex = if (c == '%' && i + 2 < value.length) value.substring(i + 1, i + 3).toIntOrNull(radix = 16) else null
            if (hex != null) {
                out.write(hex)
                i += 3
            } else {
                out.write(c.code) // path chars outside %XX are ASCII in practice
                i++
            }
        }
        return out.toString(Charsets.UTF_8.name())
    }
}
