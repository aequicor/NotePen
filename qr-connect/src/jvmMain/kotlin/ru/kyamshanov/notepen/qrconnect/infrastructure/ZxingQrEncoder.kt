package ru.kyamshanov.notepen.qrconnect.infrastructure

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import ru.kyamshanov.notepen.qrconnect.domain.port.QrEncoder
import ru.kyamshanov.notepen.qrconnect.domain.port.QrMatrix

/**
 * ZXing-based [QrEncoder]. Encodes with ECC level M and the default margin so
 * the result remains scannable when printed at small sizes or rendered on a
 * dark theme background.
 */
class ZxingQrEncoder : QrEncoder {
    private val writer = QRCodeWriter()
    private val hints =
        mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 1,
        )

    override fun encode(
        text: String,
        size: Int,
    ): QrMatrix {
        val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val side = minOf(width, height)
        val data = BooleanArray(side * side)
        for (y in 0 until side) {
            for (x in 0 until side) {
                data[y * side + x] = bitMatrix.get(x, y)
            }
        }
        return QrMatrix(side, data)
    }
}
