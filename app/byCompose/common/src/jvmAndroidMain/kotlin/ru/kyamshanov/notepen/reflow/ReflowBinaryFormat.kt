package ru.kyamshanov.notepen.reflow

import ru.kyamshanov.notepen.reflow.api.PdfContentKind
import ru.kyamshanov.notepen.reflow.api.ReflowBlock
import ru.kyamshanov.notepen.reflow.api.ReflowDocument
import ru.kyamshanov.notepen.reflow.api.ReflowRect
import ru.kyamshanov.notepen.reflow.api.SourceSpan
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Плоский бинарный формат сериализации [ReflowDocument] для дискового кэша.
 *
 * Цель — быстрый decode без оверхеда JSON/CBOR: каждое поле — натуральный
 * примитив `DataInput*` (big-endian), строки — `i32 length + UTF-8`. На
 * 238-стр. документе (~7000 блоков) decode укладывается в десятки
 * миллисекунд против ~секунд у JSON-сайдкара.
 *
 * Версионирование: первые 4 байта — magic `NPRF`, дальше u16 версии. При
 * несовпадении версии чтение бросает исключение; кэш-слой ловит и переписывает.
 */
internal object ReflowBinaryFormat {
    private const val MAGIC = 0x4E505246 // "NPRF"

    /**
     * v1: исходный плоский формат.
     * v2: `ReflowBlock.Figure` сериализует `aspectRatio` отдельным `f32`.
     * v3: `Table.confidence` (f32) и `Figure.wasTableFallback` (bool) сериализуются;
     *   в `SourceSpan.flags` добавлен FLAG_ITALIC (бит 0x04). Старые кэши (v1/v2)
     *   бракуются по `version` и удаляются вызывающим — однократный re-extract.
     */
    private const val VERSION: Int = 3
    private const val BUFFER_SIZE = 64 * 1024

    private const val TAG_HEADING: Byte = 0
    private const val TAG_PARAGRAPH: Byte = 1
    private const val TAG_LIST_ITEM: Byte = 2
    private const val TAG_BLOCKQUOTE: Byte = 3
    private const val TAG_TABLE: Byte = 4
    private const val TAG_FIGURE: Byte = 5
    private const val TAG_DIVIDER: Byte = 6

    private const val FLAG_BOLD: Int = 0x01
    private const val FLAG_MONOSPACE: Int = 0x02
    private const val FLAG_ITALIC: Int = 0x04

    data class CachedDocument(
        val document: ReflowDocument,
        val sourceSize: Long,
        val sourceMtime: Long,
    )

    fun write(
        document: ReflowDocument,
        sourceSize: Long,
        sourceMtime: Long,
        out: OutputStream,
    ) {
        val dos = DataOutputStream(BufferedOutputStream(out, BUFFER_SIZE))
        dos.writeInt(MAGIC)
        dos.writeShort(VERSION)
        dos.writeShort(0) // flags
        dos.writeInt(document.blocks.size)
        dos.writeInt(document.kind.ordinal)
        dos.writeLong(sourceSize)
        dos.writeLong(sourceMtime)
        dos.writeLong(0L) // reserved
        document.blocks.forEach { writeBlock(dos, it) }
        dos.flush()
    }

    fun read(input: InputStream): CachedDocument {
        val dis = DataInputStream(BufferedInputStream(input, BUFFER_SIZE))
        val magic = dis.readInt()
        require(magic == MAGIC) { "ReflowBinary: bad magic 0x${magic.toString(16)}" }
        val version = dis.readUnsignedShort()
        require(version == VERSION) { "ReflowBinary: unsupported version $version" }
        dis.readUnsignedShort() // flags
        val blockCount = dis.readInt()
        require(blockCount >= 0) { "ReflowBinary: negative blockCount $blockCount" }
        val kindOrdinal = dis.readInt()
        val sourceSize = dis.readLong()
        val sourceMtime = dis.readLong()
        dis.readLong() // reserved
        val kind =
            PdfContentKind.entries.getOrNull(kindOrdinal)
                ?: throw IllegalArgumentException("ReflowBinary: bad kind ordinal $kindOrdinal")
        val blocks = ArrayList<ReflowBlock>(blockCount)
        repeat(blockCount) { blocks.add(readBlock(dis)) }
        return CachedDocument(ReflowDocument(kind, blocks), sourceSize, sourceMtime)
    }

    private fun writeBlock(
        out: DataOutputStream,
        block: ReflowBlock,
    ) {
        when (block) {
            is ReflowBlock.Heading -> {
                out.writeByte(TAG_HEADING.toInt())
                writeString(out, block.text)
                out.writeInt(block.level)
                writeSpans(out, block.source)
            }
            is ReflowBlock.Paragraph -> {
                out.writeByte(TAG_PARAGRAPH.toInt())
                writeString(out, block.text)
                writeSpans(out, block.source)
            }
            is ReflowBlock.ListItem -> {
                out.writeByte(TAG_LIST_ITEM.toInt())
                writeString(out, block.text)
                writeSpans(out, block.source)
            }
            is ReflowBlock.Blockquote -> {
                out.writeByte(TAG_BLOCKQUOTE.toInt())
                writeString(out, block.text)
                writeSpans(out, block.source)
            }
            is ReflowBlock.Table -> {
                out.writeByte(TAG_TABLE.toInt())
                out.writeInt(block.rows.size)
                block.rows.forEach { row ->
                    out.writeInt(row.cells.size)
                    row.cells.forEach { cell ->
                        writeString(out, cell.text)
                        writeSpans(out, cell.source)
                    }
                }
                // v3: confidence — нужен для Lattice-рефайнера и tightening:
                // знание исходного Stream-сигнала помогает решать, нужно ли пробовать
                // Lattice (или иные post-passes) при следующем чтении из кэша.
                out.writeFloat(block.confidence)
            }
            is ReflowBlock.Figure -> {
                out.writeByte(TAG_FIGURE.toInt())
                out.writeInt(block.pageIndex)
                writeRect(out, block.bounds)
                out.writeFloat(block.aspectRatio)
                // v3: wasTableFallback — отметка для Lattice-рефайнера. Без round-trip
                // refiner на cache-hit пути не пробовал бы восстановить таблицу.
                out.writeBoolean(block.wasTableFallback)
            }
            ReflowBlock.Divider -> out.writeByte(TAG_DIVIDER.toInt())
        }
    }

    private fun readBlock(input: DataInputStream): ReflowBlock =
        when (val tag = input.readByte()) {
            TAG_HEADING -> {
                val text = readString(input)
                val level = input.readInt()
                val spans = readSpans(input)
                ReflowBlock.Heading(text, level, spans)
            }
            TAG_PARAGRAPH -> ReflowBlock.Paragraph(readString(input), readSpans(input))
            TAG_LIST_ITEM -> ReflowBlock.ListItem(readString(input), readSpans(input))
            TAG_BLOCKQUOTE -> ReflowBlock.Blockquote(readString(input), readSpans(input))
            TAG_TABLE -> {
                val rowCount = input.readInt()
                require(rowCount >= 0) { "ReflowBinary: negative rowCount $rowCount" }
                val rows = ArrayList<ReflowBlock.TableRow>(rowCount)
                repeat(rowCount) {
                    val cellCount = input.readInt()
                    require(cellCount >= 0) { "ReflowBinary: negative cellCount $cellCount" }
                    val cells = ArrayList<ReflowBlock.TableCell>(cellCount)
                    repeat(cellCount) {
                        val cellText = readString(input)
                        val cellSpans = readSpans(input)
                        cells.add(ReflowBlock.TableCell(cellText, cellSpans))
                    }
                    rows.add(ReflowBlock.TableRow(cells))
                }
                val confidence = input.readFloat()
                ReflowBlock.Table(rows = rows, confidence = confidence)
            }
            TAG_FIGURE -> {
                val pageIndex = input.readInt()
                val bounds = readRect(input)
                val aspect = input.readFloat()
                val wasTableFallback = input.readBoolean()
                ReflowBlock.Figure(
                    pageIndex = pageIndex,
                    bounds = bounds,
                    aspectRatio = aspect,
                    wasTableFallback = wasTableFallback,
                )
            }
            TAG_DIVIDER -> ReflowBlock.Divider
            else -> throw IllegalArgumentException("ReflowBinary: unknown tag $tag")
        }

    private fun writeString(
        out: DataOutputStream,
        value: String,
    ) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        out.writeInt(bytes.size)
        out.write(bytes)
    }

    private fun readString(input: DataInputStream): String {
        val len = input.readInt()
        require(len >= 0) { "ReflowBinary: negative string length $len" }
        val bytes = ByteArray(len)
        input.readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun writeRect(
        out: DataOutputStream,
        rect: ReflowRect,
    ) {
        out.writeFloat(rect.left)
        out.writeFloat(rect.top)
        out.writeFloat(rect.right)
        out.writeFloat(rect.bottom)
    }

    private fun readRect(input: DataInputStream): ReflowRect =
        ReflowRect(input.readFloat(), input.readFloat(), input.readFloat(), input.readFloat())

    private fun writeSpans(
        out: DataOutputStream,
        spans: List<SourceSpan>,
    ) {
        out.writeInt(spans.size)
        spans.forEach { writeSpan(out, it) }
    }

    private fun readSpans(input: DataInputStream): List<SourceSpan> {
        val count = input.readInt()
        require(count >= 0) { "ReflowBinary: negative spans count $count" }
        if (count == 0) return emptyList()
        val list = ArrayList<SourceSpan>(count)
        repeat(count) { list.add(readSpan(input)) }
        return list
    }

    private fun writeSpan(
        out: DataOutputStream,
        span: SourceSpan,
    ) {
        out.writeInt(span.pageIndex)
        out.writeInt(span.charStart)
        out.writeInt(span.charEnd)
        writeRect(out, span.bounds)
        var flags = 0
        if (span.bold) flags = flags or FLAG_BOLD
        if (span.monospace) flags = flags or FLAG_MONOSPACE
        if (span.italic) flags = flags or FLAG_ITALIC
        out.writeByte(flags)
    }

    private fun readSpan(input: DataInputStream): SourceSpan {
        val pageIndex = input.readInt()
        val charStart = input.readInt()
        val charEnd = input.readInt()
        val bounds = readRect(input)
        val flags = input.readByte().toInt()
        return SourceSpan(
            pageIndex = pageIndex,
            charStart = charStart,
            charEnd = charEnd,
            bounds = bounds,
            bold = (flags and FLAG_BOLD) != 0,
            monospace = (flags and FLAG_MONOSPACE) != 0,
            italic = (flags and FLAG_ITALIC) != 0,
        )
    }
}
