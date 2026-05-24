package ru.kyamshanov.notepen.epub

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * Разбирает контейнер EPUB (ZIP с XHTML/OPF) в платформенно-нейтральный
 * [EpubBook]: читает `META-INF/container.xml` → OPF → порядок `spine`, после
 * чего превращает каждый XHTML-документ в поток [EpubBlock] в порядке чтения.
 *
 * Разбор HTML устойчив к нестрогой разметке (используется толерантный парсер
 * jsoup). Изображения извлекаются из контейнера по `href` манифеста.
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
    fun parse(epubBytes: ByteArray): EpubBook {
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
        return EpubBook(metadata = metadata, blocks = blocks)
    }

    private fun buildBlocks(
        spinePaths: List<String>,
        entries: Map<String, ByteArray>,
    ): List<EpubBlock> {
        val blocks = mutableListOf<EpubBlock>()
        for (path in spinePaths) {
            val bytes = entries[path] ?: continue
            if (blocks.isNotEmpty()) blocks.add(EpubBlock.PageBreak)
            val doc = Jsoup.parse(String(bytes, Charsets.UTF_8), path)
            val body = doc.body() ?: continue
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
        val containerBytes = entries["META-INF/container.xml"]
            ?: throw IllegalArgumentException("Not a valid EPUB: missing META-INF/container.xml")
        val container = parseXml(containerBytes)
        val fullPath = container.selectFirst("rootfile")?.attr("full-path").orEmpty()
        require(fullPath.isNotBlank()) { "Not a valid EPUB: container.xml has no rootfile" }
        return normalize(percentDecode(fullPath))
    }

    // --- OPF ----------------------------------------------------------------

    private fun readMetadata(opf: Document): EpubMetadata = EpubMetadata(
        title = firstTagText(opf, "dc:title", "title"),
        author = firstTagText(opf, "dc:creator", "creator"),
        language = firstTagText(opf, "dc:language", "language"),
        identifier = firstTagText(opf, "dc:identifier", "identifier"),
    )

    /** id → resolved-relative href (percent-decoded, not yet joined to opfDir). */
    private fun readManifest(opf: Document): Map<String, String> =
        opf.select("manifest > item").mapNotNull { item ->
            val id = item.attr("id")
            val href = item.attr("href")
            if (id.isBlank() || href.isBlank()) null else id to percentDecode(href)
        }.toMap()

    private fun readSpine(
        opf: Document,
        hrefById: Map<String, String>,
        opfDir: String,
    ): List<String> = opf.select("spine > itemref").mapNotNull { ref ->
        val idref = ref.attr("idref")
        val href = hrefById[idref] ?: return@mapNotNull null
        resolvePath(opfDir, href)
    }

    private fun firstTagText(doc: Document, vararg tags: String): String? {
        for (tag in tags) {
            val text = doc.getElementsByTag(tag).firstOrNull()?.text()?.trim()
            if (!text.isNullOrBlank()) return text
        }
        return null
    }

    // --- XHTML → blocks -----------------------------------------------------

    private class DocumentContext(
        val baseDir: String,
        val entries: Map<String, ByteArray>,
    )

    private val HEADINGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")
    private val CONTAINERS = setOf("div", "section", "article", "main", "header", "footer", "figure", "aside")

    private fun appendBlocks(parent: Element, ctx: DocumentContext, out: MutableList<EpubBlock>) {
        for (child in parent.children()) {
            when (val tag = child.normalName()) {
                in HEADINGS -> child.text().trim().takeIf { it.isNotBlank() }
                    ?.let { out.add(EpubBlock.Heading(level = tag.substring(1).toInt(), text = it)) }

                "p", "pre" -> appendTextAndImages(child, ctx, out)
                "blockquote" -> {
                    child.text().trim().takeIf { it.isNotBlank() }?.let { out.add(EpubBlock.Blockquote(it)) }
                    appendImages(child, ctx, out)
                }

                "ul", "ol" -> appendListItems(child, ordered = tag == "ol", level = 0, ctx = ctx, out = out)
                "table" -> appendTable(child, out)
                "hr" -> out.add(EpubBlock.HorizontalRule)
                "img", "image" -> appendImageElement(child, ctx, out)
                in CONTAINERS, "body" -> appendBlocks(child, ctx, out)
                else -> if (child.children().isEmpty()) {
                    child.text().trim().takeIf { it.isNotBlank() }?.let { out.add(EpubBlock.Paragraph(it)) }
                } else {
                    appendBlocks(child, ctx, out)
                }
            }
        }
    }

    private fun appendTextAndImages(element: Element, ctx: DocumentContext, out: MutableList<EpubBlock>) {
        element.text().trim().takeIf { it.isNotBlank() }?.let { out.add(EpubBlock.Paragraph(it)) }
        appendImages(element, ctx, out)
    }

    private fun appendImages(element: Element, ctx: DocumentContext, out: MutableList<EpubBlock>) {
        for (img in element.select("img, image")) appendImageElement(img, ctx, out)
    }

    private fun appendListItems(
        list: Element,
        ordered: Boolean,
        level: Int,
        ctx: DocumentContext,
        out: MutableList<EpubBlock>,
    ) {
        var ordinal = 1
        for (li in list.children().filter { it.normalName() == "li" }) {
            val nested = li.children().filter { it.normalName() == "ul" || it.normalName() == "ol" }
            val ownText = li.clone().also { clone ->
                clone.select("ul, ol").remove()
            }.text().trim()
            if (ownText.isNotBlank()) {
                out.add(EpubBlock.ListItem(text = ownText, ordered = ordered, ordinal = ordinal, level = level))
            }
            ordinal++
            for (sub in nested) {
                appendListItems(sub, ordered = sub.normalName() == "ol", level = level + 1, ctx = ctx, out = out)
            }
        }
    }

    private fun appendTable(table: Element, out: MutableList<EpubBlock>) {
        val rows = table.select("tr").map { tr ->
            tr.select("th, td").map { it.text().trim() }
        }.filter { it.isNotEmpty() }
        if (rows.isNotEmpty()) out.add(EpubBlock.Table(rows))
    }

    private fun appendImageElement(element: Element, ctx: DocumentContext, out: MutableList<EpubBlock>) {
        val rawSrc = element.attr("src").ifBlank {
            element.attr("xlink:href").ifBlank { element.attr("href") }
        }
        if (rawSrc.isBlank()) return
        val path = resolvePath(ctx.baseDir, percentDecode(rawSrc.substringBefore('#').substringBefore('?')))
        val data = ctx.entries[path] ?: return
        out.add(
            EpubBlock.Image(
                data = data,
                mimeType = guessImageMime(path),
                alt = element.attr("alt").trim().ifBlank { null },
            ),
        )
    }

    // --- Path & misc helpers ------------------------------------------------

    private fun parseXml(bytes: ByteArray): Document =
        Jsoup.parse(String(bytes, Charsets.UTF_8), "", Parser.xmlParser())

    private fun normalize(name: String): String =
        name.replace('\\', '/').removePrefix("./").trimStart('/')

    private fun parentDir(path: String): String = path.substringBeforeLast('/', missingDelimiterValue = "")

    /** Joins [baseDir] and a relative [href], collapsing `.`/`..` segments. */
    private fun resolvePath(baseDir: String, href: String): String {
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

    private fun guessImageMime(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
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
