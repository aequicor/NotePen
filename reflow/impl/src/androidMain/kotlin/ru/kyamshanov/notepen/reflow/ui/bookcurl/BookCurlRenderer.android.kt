package ru.kyamshanov.notepen.reflow.ui.bookcurl

import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb

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
        val canvas = composeCanvas.nativeCanvas
        val verts = mesh.vertices2d
        if (buffers.columns != mesh.columns || buffers.rows != mesh.rows) return@drawIntoCanvas
        canvas.save()
        canvas.translate(offsetX, 0f)
        try {
            drawCastShadow(canvas = canvas, mesh = mesh, paint = paint)
            drawPaperBase(canvas = canvas, mesh = mesh, paint = paint)

            back?.let {
                val backPaint =
                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                        alpha = 46
                        colorFilter =
                            PorterDuffColorFilter(
                                paint.paperTint.copy(alpha = 0.18f).toArgb(),
                                PorterDuff.Mode.SRC_ATOP,
                            )
                    }
                canvas.drawBitmapMesh(it.toSoftwareBookCurlBitmap(), mesh.columns, mesh.rows, verts, 0, null, 0, backPaint)
            }

            val frontBitmap = front.toSoftwareBookCurlBitmap()
            val frontPaint =
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    alpha = (frontAlpha(mesh) * 255).toInt()
                }
            val readablePaint =
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    alpha = (readableBaseAlpha(mesh) * 255).toInt()
                }
            drawReadableTextureBase(canvas = canvas, bitmap = frontBitmap, mesh = mesh, paint = readablePaint)
            canvas.drawBitmapMesh(
                frontBitmap,
                mesh.columns,
                mesh.rows,
                verts,
                0,
                null,
                0,
                frontPaint,
            )

            drawRimHighlight(canvas = canvas, mesh = mesh, paint = paint)
        } finally {
            canvas.restore()
        }
    }
}

private fun drawReadableTextureBase(
    canvas: android.graphics.Canvas,
    bitmap: android.graphics.Bitmap,
    mesh: BookCurlMesh,
    paint: Paint,
) {
    canvas.drawBitmap(bitmap, null, RectF(0f, 0f, mesh.widthPx, mesh.heightPx), paint)
}

private fun drawPaperBase(
    canvas: android.graphics.Canvas,
    mesh: BookCurlMesh,
    paint: BookCurlPaint,
) {
    val surfacePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = paint.paperTint.scaleRgb(0.99f).toArgb()
        }
    canvas.drawPath(mesh.outlinePath(), surfacePaint)
}

private fun BookCurlMesh.outlinePath(): Path {
    val path = Path()

    fun lineToVertex(index: Int) {
        val xy = index * 2
        path.lineTo(vertices2d[xy], vertices2d[xy + 1])
    }

    path.moveTo(vertices2d[0], vertices2d[1])
    for (col in 1..columns) lineToVertex(col)
    for (row in 1..rows) lineToVertex(row * (columns + 1) + columns)
    for (col in (columns - 1) downTo 0) lineToVertex(rows * (columns + 1) + col)
    for (row in (rows - 1) downTo 1) lineToVertex(row * (columns + 1))
    path.close()
    return path
}

private fun drawCastShadow(
    canvas: android.graphics.Canvas,
    mesh: BookCurlMesh,
    paint: BookCurlPaint,
) {
    val band = liftedBand(mesh) ?: return
    val strength = (mesh.maxLiftPx / (mesh.widthPx * 0.18f).coerceAtLeast(1f)).coerceIn(0f, 1f)
    val spread = mesh.widthPx * (0.09f + 0.16f * strength)
    val left = (band.centerX - spread * 0.55f).coerceAtLeast(0f)
    val right = (band.centerX + spread * 1.1f).coerceAtMost(mesh.widthPx)
    if (right <= left) return
    val shadowPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader =
                LinearGradient(
                    left,
                    0f,
                    right,
                    0f,
                    intArrayOf(
                        paint.shadow.copy(alpha = 0f).toArgb(),
                        paint.shadow.copy(alpha = 0.24f * strength).toArgb(),
                        paint.shadow.copy(alpha = 0.07f * strength).toArgb(),
                        paint.shadow.copy(alpha = 0f).toArgb(),
                    ),
                    floatArrayOf(0f, 0.34f, 0.62f, 1f),
                    Shader.TileMode.CLAMP,
                )
        }
    canvas.drawRect(
        RectF(
            left,
            (band.minY - spread * 0.32f).coerceAtLeast(0f),
            right,
            (band.maxY + spread * 0.32f).coerceAtMost(mesh.heightPx),
        ),
        shadowPaint,
    )
}

private fun drawRimHighlight(
    canvas: android.graphics.Canvas,
    mesh: BookCurlMesh,
    paint: BookCurlPaint,
) {
    val band = liftedBand(mesh) ?: return
    val strength = (mesh.maxLiftPx / (mesh.widthPx * 0.18f).coerceAtLeast(1f)).coerceIn(0f, 1f)
    if (strength > 0f) {
        val spread = mesh.widthPx * (0.012f + 0.025f * strength)
        val left = (band.centerX - spread * 1.2f).coerceAtLeast(0f)
        val right = (band.centerX + spread * 1.4f).coerceAtMost(mesh.widthPx)
        if (right > left) {
            val highlightPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader =
                        LinearGradient(
                            left,
                            0f,
                            right,
                            0f,
                            intArrayOf(
                                paint.highlight.copy(alpha = 0f).toArgb(),
                                paint.highlight.copy(alpha = 0.11f * strength).toArgb(),
                                paint.highlight.copy(alpha = 0f).toArgb(),
                            ),
                            floatArrayOf(0f, 0.46f, 1f),
                            Shader.TileMode.CLAMP,
                        )
                }
            canvas.drawRect(
                RectF(
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

private fun frontAlpha(mesh: BookCurlMesh): Float {
    val t = ((mesh.progress - 0.84f) / 0.16f).coerceIn(0f, 1f)
    val smooth = t * t * (3f - 2f * t)
    return 1f - smooth * 0.12f
}

private fun readableBaseAlpha(mesh: BookCurlMesh): Float {
    val t = ((mesh.progress - 0.58f) / 0.42f).coerceIn(0f, 1f)
    val smooth = t * t * (3f - 2f * t)
    return 1f - smooth * 0.88f
}

private fun androidx.compose.ui.graphics.Color.scaleRgb(scale: Float): androidx.compose.ui.graphics.Color =
    copy(
        red = (red * scale).coerceIn(0f, 1f),
        green = (green * scale).coerceIn(0f, 1f),
        blue = (blue * scale).coerceIn(0f, 1f),
        alpha = 1f,
    )
