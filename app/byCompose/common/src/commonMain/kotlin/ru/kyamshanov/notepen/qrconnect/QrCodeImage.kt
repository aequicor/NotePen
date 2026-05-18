package ru.kyamshanov.notepen.qrconnect

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.kyamshanov.notepen.qrconnect.domain.port.QrMatrix

/**
 * Renders a [QrMatrix] as a black-on-white square.
 *
 * Drawing through [Canvas] avoids platform `Bitmap` / `BufferedImage` types,
 * so the same composable works on desktop, Android, and any future iOS target.
 */
@Composable
fun QrCodeImage(
    matrix: QrMatrix,
    modifier: Modifier = Modifier,
    sizeDp: Dp = DEFAULT_QR_DP,
    foreground: Color = Color.Black,
    background: Color = Color.White,
) {
    Canvas(modifier.size(sizeDp)) {
        drawRect(color = background, topLeft = Offset.Zero, size = this.size)
        val cell = this.size.minDimension / matrix.size
        for (y in 0 until matrix.size) {
            for (x in 0 until matrix.size) {
                if (matrix[x, y]) {
                    drawRect(
                        color = foreground,
                        topLeft = Offset(x * cell, y * cell),
                        size = Size(cell, cell),
                        style = Fill,
                    )
                }
            }
        }
    }
}

private val DEFAULT_QR_DP = 240.dp
