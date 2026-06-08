package ru.kyamshanov.notepen.reflow.ui.bookcurl

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.graphics.toArgb
import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MaskFilter
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

            // Лицевые треугольники — текущая страница (нормальные UV).
            drawTexturedMesh(
                image = front,
                mesh = mesh,
                texCoords = buffers.textureCoordinates,
                indices = mesh.facingIndices(buffers, keepFront = true),
                alpha = 1f,
            )
            // Изнанка завернувшегося листа — СЛЕДУЮЩАЯ страница (картинка уже зеркальная, см.
            // BookCurlOverlay): после переворота вокруг корешка читается нормально. Без неё изнанка
            // была бы пустой бумагой.
            back?.let { image ->
                drawTexturedMesh(
                    image = image,
                    mesh = mesh,
                    texCoords = buffers.textureCoordinates,
                    indices = mesh.facingIndices(buffers, keepFront = false),
                    alpha = 1f,
                )
            }

            drawRimHighlight(canvas = canvas, mesh = mesh, paint = paint)
        } finally {
            canvas.restore()
        }
    }
}

private fun DrawScope.drawTexturedMesh(
    image: ImageBitmap,
    mesh: BookCurlMesh,
    texCoords: FloatArray,
    indices: ShortArray,
    alpha: Float,
) {
    if (indices.isEmpty()) return
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
                mesh.shadeColors(),
                texCoords,
                indices,
                BlendMode.MODULATE,
                drawPaint,
            )
        } finally {
            skiaImage.close()
        }
    }
}

/**
 * Индексы треугольников одной стороны: [keepFront]=true — лицевые (facing ≥ 0, текущая страница),
 * false — завернувшаяся изнанка (facing < 0, следующая страница). Делим, т.к. стороны кроем
 * разными текстурами.
 */
private fun BookCurlMesh.facingIndices(
    buffers: BookCurlMeshBuffers,
    keepFront: Boolean,
): ShortArray {
    val all = buffers.triangleIndices
    val out = ShortArray(all.size)
    var n = 0
    var i = 0
    while (i < all.size) {
        val a = all[i].toInt()
        val b = all[i + 1].toInt()
        val c = all[i + 2].toInt()
        if ((facing[a] + facing[b] + facing[c] >= 0f) == keepFront) {
            out[n++] = all[i]
            out[n++] = all[i + 1]
            out[n++] = all[i + 2]
        }
        i += 3
    }
    return out.copyOf(n)
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
    val strength = meshShadow(mesh) * mesh.shadeFade
    if (strength <= 0.02f) return
    // Тень — силуэт страницы (с изогнутым свободным краем), смещённый и размытый: на открываемом
    // листе тень повторяет ФОРМУ изгиба края, а не идёт прямым градиентом. Рисуется первой —
    // страница сверху перекрывает свою тень, остаётся только смещённый «полумесяц» снизу.
    val lift = mesh.maxLiftPx
    val shadowPaint =
        Paint().apply {
            color = paint.shadow.copy(alpha = SHADOW_CAST_ALPHA * strength).toArgb()
            maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, (lift * SHADOW_CAST_BLUR).coerceAtLeast(1f))
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
    canvas: org.jetbrains.skia.Canvas,
    mesh: BookCurlMesh,
    paint: BookCurlPaint,
) {
    val band = liftedBand(mesh) ?: return
    val strength = meshShadow(mesh) * mesh.shadeFade
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
                                    paint.highlight.copy(alpha = 0.16f * strength).toArgb(),
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

private fun meshShadow(mesh: BookCurlMesh): Float = (mesh.maxLiftPx / (mesh.widthPx * 0.18f).coerceAtLeast(1f)).coerceIn(0f, 1f)

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
