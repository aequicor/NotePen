package ru.kyamshanov.notepen.reflow.ui.bookcurl

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.graphics.toArgb
import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PathBuilder
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Shader
import org.jetbrains.skia.VertexMode

internal actual fun isBookCurlNativeRendererSupported(): Boolean = true

internal actual fun DrawScope.drawBookCurlNative(
    front: ImageBitmap,
    back: ImageBitmap?,
    mesh: BookCurlMesh,
    buffers: BookCurlMeshBuffers,
    offsetX: Float,
    paint: BookCurlPaint,
) {
    drawIntoCanvas { composeCanvas ->
        val canvas = composeCanvas.skiaCanvas
        canvas.save()
        canvas.translate(offsetX, 0f)
        try {
            drawCastShadow(canvas = canvas, mesh = mesh, paint = paint)
            drawPaperBase(canvas = canvas, mesh = mesh, paint = paint)

            back?.let { image ->
                drawTexturedMesh(
                    image = image,
                    mesh = mesh,
                    buffers = buffers,
                    alpha = 0.18f,
                )
            }
            drawReadableTextureBase(
                image = front,
                mesh = mesh,
                alpha = mesh.readableBaseAlpha,
            )
            drawTexturedMesh(
                image = front,
                mesh = mesh,
                buffers = buffers,
                alpha = mesh.frontAlpha,
            )

            drawRimHighlight(canvas = canvas, mesh = mesh, paint = paint)
        } finally {
            canvas.restore()
        }
    }
}

private fun DrawScope.drawTexturedMesh(
    image: ImageBitmap,
    mesh: BookCurlMesh,
    buffers: BookCurlMeshBuffers,
    alpha: Float,
) {
    drawIntoCanvas { composeCanvas ->
        val canvas = composeCanvas.skiaCanvas
        val skiaImage = Image.makeFromBitmap(image.asSkiaBitmap())
        val sampling = FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE)
        val drawPaint =
            Paint().apply {
                this.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
                shader = skiaImage.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, sampling, Matrix33.IDENTITY)
            }
        try {
            canvas.drawVertices(
                VertexMode.TRIANGLES,
                mesh.vertices2d,
                null,
                buffers.textureCoordinates,
                buffers.triangleIndices,
                BlendMode.MODULATE,
                drawPaint,
            )
        } finally {
            skiaImage.close()
        }
    }
}

private fun DrawScope.drawReadableTextureBase(
    image: ImageBitmap,
    mesh: BookCurlMesh,
    alpha: Float,
) {
    drawIntoCanvas { composeCanvas ->
        val canvas = composeCanvas.skiaCanvas
        val skiaImage = Image.makeFromBitmap(image.asSkiaBitmap())
        val drawPaint = Paint().apply { this.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt() }
        try {
            canvas.drawImageRect(
                image = skiaImage,
                srcLeft = 0f,
                srcTop = 0f,
                srcRight = image.width.toFloat(),
                srcBottom = image.height.toFloat(),
                dstLeft = 0f,
                dstTop = 0f,
                dstRight = mesh.widthPx,
                dstBottom = mesh.heightPx,
                samplingMode = FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE),
                paint = drawPaint,
                strict = true,
            )
        } finally {
            skiaImage.close()
        }
    }
}

private fun drawPaperBase(
    canvas: org.jetbrains.skia.Canvas,
    mesh: BookCurlMesh,
    paint: BookCurlPaint,
) {
    canvas.drawPath(mesh.outlinePath(), Paint().apply { color = paint.paperTint.scaleRgb(0.99f).toArgb() })
}

private fun BookCurlMesh.outlinePath(): org.jetbrains.skia.Path {
    fun vertexX(index: Int): Float = vertices2d[index * 2]

    fun vertexY(index: Int): Float = vertices2d[index * 2 + 1]

    val builder = PathBuilder().moveTo(vertexX(0), vertexY(0))
    for (col in 1..columns) builder.lineTo(vertexX(col), vertexY(col))
    for (row in 1..rows) {
        val index = row * (columns + 1) + columns
        builder.lineTo(vertexX(index), vertexY(index))
    }
    for (col in (columns - 1) downTo 0) {
        val index = rows * (columns + 1) + col
        builder.lineTo(vertexX(index), vertexY(index))
    }
    for (row in (rows - 1) downTo 1) builder.lineTo(vertexX(row * (columns + 1)), vertexY(row * (columns + 1)))
    return builder.closePath().detach()
}

private fun drawCastShadow(
    canvas: org.jetbrains.skia.Canvas,
    mesh: BookCurlMesh,
    paint: BookCurlPaint,
) {
    val band = liftedBand(mesh) ?: return
    val strength = meshShadow(mesh)
    val spread = mesh.widthPx * (0.09f + 0.16f * strength)
    val left = (band.centerX - spread * 0.55f).coerceAtLeast(0f)
    val right = (band.centerX + spread * 1.1f).coerceAtMost(mesh.widthPx)
    if (right <= left) return
    val shadowPaint =
        Paint().apply {
            shader =
                Shader.makeLinearGradient(
                    x0 = left,
                    y0 = 0f,
                    x1 = right,
                    y1 = 0f,
                    colors =
                        intArrayOf(
                            paint.shadow.copy(alpha = 0f).toArgb(),
                            paint.shadow.copy(alpha = 0.24f * strength).toArgb(),
                            paint.shadow.copy(alpha = 0.07f * strength).toArgb(),
                            paint.shadow.copy(alpha = 0f).toArgb(),
                        ),
                    positions = floatArrayOf(0f, 0.34f, 0.62f, 1f),
                )
        }
    canvas.drawRect(
        Rect.makeLTRB(
            left,
            (band.minY - spread * 0.32f).coerceAtLeast(0f),
            right,
            (band.maxY + spread * 0.32f).coerceAtMost(mesh.heightPx),
        ),
        shadowPaint,
    )
}

private fun drawRimHighlight(
    canvas: org.jetbrains.skia.Canvas,
    mesh: BookCurlMesh,
    paint: BookCurlPaint,
) {
    val band = liftedBand(mesh) ?: return
    val strength = meshShadow(mesh)
    if (strength > 0f) {
        val spread = mesh.widthPx * (0.012f + 0.025f * strength)
        val left = (band.centerX - spread * 1.2f).coerceAtLeast(0f)
        val right = (band.centerX + spread * 1.4f).coerceAtMost(mesh.widthPx)
        if (right > left) {
            val highlightPaint =
                Paint().apply {
                    shader =
                        Shader.makeLinearGradient(
                            x0 = left,
                            y0 = 0f,
                            x1 = right,
                            y1 = 0f,
                            colors =
                                intArrayOf(
                                    paint.highlight.copy(alpha = 0f).toArgb(),
                                    paint.highlight.copy(alpha = 0.11f * strength).toArgb(),
                                    paint.highlight.copy(alpha = 0f).toArgb(),
                                ),
                            positions = floatArrayOf(0f, 0.46f, 1f),
                        )
                }
            canvas.drawRect(
                Rect.makeLTRB(
                    left,
                    band.minY.coerceAtLeast(0f),
                    right,
                    band.maxY.coerceAtMost(mesh.heightPx),
                ),
                highlightPaint,
            )
        }
    }
}

private data class LiftedBand(
    val centerX: Float,
    val minY: Float,
    val maxY: Float,
)

private fun liftedBand(mesh: BookCurlMesh): LiftedBand? {
    val threshold = (mesh.maxLiftPx * 0.18f).coerceAtLeast(0.5f)
    var weightedX = 0f
    var weight = 0f
    var minY = Float.POSITIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    for (row in 0..mesh.rows) {
        for (col in 0..mesh.columns) {
            val vertexIndex = row * (mesh.columns + 1) + col
            val xy = vertexIndex * 2
            val xyz = vertexIndex * 3
            val z = mesh.vertices3d[xyz + 2]
            if (z <= threshold) continue
            val x = mesh.vertices2d[xy]
            val y = mesh.vertices2d[xy + 1]
            weightedX += x * z
            weight += z
            minY = minOf(minY, y)
            maxY = maxOf(maxY, y)
        }
    }
    if (weight <= 0f || minY == Float.POSITIVE_INFINITY || maxY == Float.NEGATIVE_INFINITY) return null
    return LiftedBand(centerX = weightedX / weight, minY = minY, maxY = maxY)
}

private val BookCurlMesh.frontAlpha: Float
    get() {
        val t = ((progress - 0.84f) / 0.16f).coerceIn(0f, 1f)
        val smooth = t * t * (3f - 2f * t)
        return 1f - smooth * 0.12f
    }

private val BookCurlMesh.readableBaseAlpha: Float
    get() {
        val t = ((progress - 0.58f) / 0.42f).coerceIn(0f, 1f)
        val smooth = t * t * (3f - 2f * t)
        return 1f - smooth * 0.88f
    }

private fun meshShadow(mesh: BookCurlMesh): Float = (mesh.maxLiftPx / (mesh.widthPx * 0.18f).coerceAtLeast(1f)).coerceIn(0f, 1f)

private fun androidx.compose.ui.graphics.Color.scaleRgb(scale: Float): androidx.compose.ui.graphics.Color =
    copy(
        red = (red * scale).coerceIn(0f, 1f),
        green = (green * scale).coerceIn(0f, 1f),
        blue = (blue * scale).coerceIn(0f, 1f),
        alpha = 1f,
    )
