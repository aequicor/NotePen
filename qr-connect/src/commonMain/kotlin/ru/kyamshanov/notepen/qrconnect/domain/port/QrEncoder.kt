package ru.kyamshanov.notepen.qrconnect.domain.port

/**
 * Encodes a string payload into a square monochrome QR matrix.
 *
 * Platform implementations decide their bitmap representation; the matrix
 * abstraction keeps the domain free of `BufferedImage` / `Bitmap` types.
 */
fun interface QrEncoder {
    fun encode(
        text: String,
        size: Int,
    ): QrMatrix
}

/**
 * Square matrix where `true` means a dark module (drawn pixel) and `false`
 * means light (background). Side length equals [size].
 */
class QrMatrix(
    val size: Int,
    private val data: BooleanArray,
) {
    init {
        require(data.size == size * size) { "data length must equal size*size" }
    }

    operator fun get(
        x: Int,
        y: Int,
    ): Boolean = data[y * size + x]
}
