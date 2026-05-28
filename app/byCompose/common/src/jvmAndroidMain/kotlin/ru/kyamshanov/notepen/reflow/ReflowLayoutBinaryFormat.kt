package ru.kyamshanov.notepen.reflow

import ru.kyamshanov.notepen.reflow.ui.CachedLayout
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Сериализация [CachedLayout] для дискового кэша раскладки.
 *
 * Формат прямой (BE через `DataInput/OutputStream`): magic+version, секция
 * `heights` (count + пары (blockIndex, heightPx)), секция `lineBottoms` (count +
 * для каждой записи: blockIndex, lineCount, Float[lineCount]).
 *
 * Версионируется: при `MAGIC` или `VERSION` несовпадении вызывающая сторона
 * удаляет файл и пересчитывает раскладку.
 */
internal object ReflowLayoutBinaryFormat {
    private const val MAGIC = 0x4E50524C // "NPRL"

    /**
     * v1: только `textHeights` + `textLineBottoms`.
     * v2: добавлена `figureHeights` секция (Map<Int, Int>) — её отсутствие
     *   в v1 вызывало transient repaginate, см. `CachedLayout.figureHeights`.
     */
    private const val VERSION: Int = 2
    private const val BUFFER_SIZE = 64 * 1024

    fun write(
        layout: CachedLayout,
        out: OutputStream,
    ) {
        val dos = DataOutputStream(BufferedOutputStream(out, BUFFER_SIZE))
        dos.writeInt(MAGIC)
        dos.writeShort(VERSION)
        dos.writeShort(0) // flags
        // Heights
        dos.writeInt(layout.textHeights.size)
        for ((index, height) in layout.textHeights) {
            dos.writeInt(index)
            dos.writeInt(height)
        }
        // LineBottoms
        dos.writeInt(layout.textLineBottoms.size)
        for ((index, bottoms) in layout.textLineBottoms) {
            dos.writeInt(index)
            dos.writeInt(bottoms.size)
            for (b in bottoms) dos.writeFloat(b)
        }
        // FigureHeights (v2)
        dos.writeInt(layout.figureHeights.size)
        for ((index, h) in layout.figureHeights) {
            dos.writeInt(index)
            dos.writeInt(h)
        }
        dos.flush()
    }

    fun read(input: InputStream): CachedLayout {
        val dis = DataInputStream(BufferedInputStream(input, BUFFER_SIZE))
        val magic = dis.readInt()
        require(magic == MAGIC) { "ReflowLayoutBinary: bad magic 0x${magic.toString(16)}" }
        val version = dis.readUnsignedShort()
        require(version == VERSION) { "ReflowLayoutBinary: unsupported version $version" }
        dis.readUnsignedShort() // flags
        val heightsCount = dis.readInt()
        require(heightsCount >= 0) { "ReflowLayoutBinary: negative heightsCount $heightsCount" }
        val heights = HashMap<Int, Int>(heightsCount.coerceAtLeast(16))
        repeat(heightsCount) {
            val idx = dis.readInt()
            val h = dis.readInt()
            heights[idx] = h
        }
        val lineBottomsCount = dis.readInt()
        require(lineBottomsCount >= 0) { "ReflowLayoutBinary: negative lineBottomsCount $lineBottomsCount" }
        val lineBottoms = HashMap<Int, List<Float>>(lineBottomsCount.coerceAtLeast(16))
        repeat(lineBottomsCount) {
            val idx = dis.readInt()
            val len = dis.readInt()
            require(len >= 0) { "ReflowLayoutBinary: negative lineCount $len" }
            val arr = ArrayList<Float>(len)
            repeat(len) { arr.add(dis.readFloat()) }
            lineBottoms[idx] = arr
        }
        val figureHeightsCount = dis.readInt()
        require(figureHeightsCount >= 0) {
            "ReflowLayoutBinary: negative figureHeightsCount $figureHeightsCount"
        }
        val figureHeights = HashMap<Int, Int>(figureHeightsCount.coerceAtLeast(8))
        repeat(figureHeightsCount) {
            val idx = dis.readInt()
            val h = dis.readInt()
            figureHeights[idx] = h
        }
        return CachedLayout(
            textHeights = heights,
            textLineBottoms = lineBottoms,
            figureHeights = figureHeights,
        )
    }
}
