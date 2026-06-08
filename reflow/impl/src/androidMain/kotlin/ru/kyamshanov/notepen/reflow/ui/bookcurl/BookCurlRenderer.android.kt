package ru.kyamshanov.notepen.reflow.ui.bookcurl

import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
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

            val meshPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { alpha = 255 }
            // Лицо — текущая страница на весь меш.
            canvas.drawBitmapMesh(
                front.toSoftwareBookCurlBitmap(),
                mesh.columns,
                mesh.rows,
                verts,
                0,
                mesh.shadeColors(),
                0,
                meshPaint,
            )
            // Изнанка завернувшейся части — следующая страница (картинка уже зеркальная). drawBitmapMesh
            // не умеет отбирать треугольники, поэтому клипуем по силуэту обратных (facing<0) граней.
            back?.let { image ->
                val backPath = mesh.backFacingPath()
                if (!backPath.isEmpty) {
                    canvas.save()
                    canvas.clipPath(backPath)
                    canvas.drawBitmapMesh(
                        image.toSoftwareBookCurlBitmap(),
                        mesh.columns,
                        mesh.rows,
                        verts,
                        0,
                        mesh.shadeColors(),
                        0,
                        meshPaint,
                    )
                    canvas.restore()
                }
            }

            drawRimHighlight(canvas = canvas, mesh = mesh, paint = paint)
        } finally {
            canvas.restore()
        }
    }
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

/** Силуэт завернувшихся (facing < 0) треугольников — клип для текстуры изнанки. */
private fun BookCurlMesh.backFacingPath(): Path {
    val path = Path()

    fun addTriangle(
        a: Int,
        b: Int,
        c: Int,
    ) {
        path.moveTo(vertices2d[a * 2], vertices2d[a * 2 + 1])
        path.lineTo(vertices2d[b * 2], vertices2d[b * 2 + 1])
        path.lineTo(vertices2d[c * 2], vertices2d[c * 2 + 1])
        path.close()
    }

    for (row in 0 until rows) {
        for (col in 0 until columns) {
            val p00 = row * (columns + 1) + col
            val p10 = p00 + 1
            val p01 = p00 + columns + 1
            val p11 = p01 + 1
            if (facing[p00] + facing[p10] + facing[p11] < 0f) addTriangle(p00, p10, p11)
            if (facing[p00] + facing[p11] + facing[p01] < 0f) addTriangle(p00, p11, p01)
        }
    }
    return path
}

private fun drawCastShadow(
    canvas: android.graphics.Canvas,
    mesh: BookCurlMesh,
    paint: BookCurlPaint,
) {
    val strength = (mesh.maxLiftPx / (mesh.widthPx * 0.18f).coerceAtLeast(1f)).coerceIn(0f, 1f) * mesh.shadeFade
    if (strength <= 0.02f) return
    // Тень — силуэт страницы (с изогнутым краем), смещённый и размытый: повторяет форму изгиба
    // края на открываемом листе. Рисуется первой — страница сверху перекрывает свою тень.
    val lift = mesh.maxLiftPx
    val shadowPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = paint.shadow.copy(alpha = SHADOW_CAST_ALPHA * strength).toArgb()
            maskFilter = BlurMaskFilter((lift * SHADOW_CAST_BLUR).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
        }
    canvas.save()
    canvas.translate(lift * SHADOW_CAST_OFFSET_X * mesh.direction, lift * SHADOW_CAST_OFFSET_Y)
    try {
        canvas.drawPath(mesh.outlinePath(), shadowPaint)
    } finally {
        canvas.restore()
    }
}

private fun drawRimHighlight(
    canvas: android.graphics.Canvas,
    mesh: BookCurlMesh,
    paint: BookCurlPaint,
) {
    val band = liftedBand(mesh) ?: return
    val strength = (mesh.maxLiftPx / (mesh.widthPx * 0.18f).coerceAtLeast(1f)).coerceIn(0f, 1f) * mesh.shadeFade
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
                                paint.highlight.copy(alpha = 0.16f * strength).toArgb(),
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

private fun androidx.compose.ui.graphics.Color.scaleRgb(scale: Float): androidx.compose.ui.graphics.Color =
    copy(
        red = (red * scale).coerceIn(0f, 1f),
        green = (green * scale).coerceIn(0f, 1f),
        blue = (blue * scale).coerceIn(0f, 1f),
        alpha = 1f,
    )

/** Прозрачность отбрасываемой тени-силуэта. */
private const val SHADOW_CAST_ALPHA = 0.18f

/** Смещение силуэта тени по горизонтали (× подъём, со знаком направления). */
private const val SHADOW_CAST_OFFSET_X = 0.05f

/** Смещение силуэта тени вниз (× подъём) — тень ложится под приподнятый лист. */
private const val SHADOW_CAST_OFFSET_Y = 0.1f

/** Радиус размытия тени (× подъём). */
private const val SHADOW_CAST_BLUR = 0.05f
