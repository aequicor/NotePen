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

// Кэши уровня файла. Все они трогаются ТОЛЬКО из drawBookCurlNative, которая исполняется
// исключительно на единственном UI-потоке Compose (кадр перелистывания). Поэтому изменяемое
// состояние на уровне файла безопасно без синхронизации: одновременных обращений нет. Каждый
// нативный Skia-ресурс (Image/Shader/MaskFilter) ЗАКРЫВАЕТСЯ close() при вытеснении, иначе утечёт.

// #1: переиспользуемый PathBuilder тени (геометрия заполняется заново каждый кадр) + один Paint;
// MaskFilter кэшируем и пересоздаём только при смене квантованного (целопиксельного) радиуса.
// (Skia m144 убрала мутирующий Path API ⇒ строим через PathBuilder.)
private val shadowCastPathBuilder = PathBuilder()
private val shadowCastPaint = Paint()
private var cachedShadowBlurFilter: MaskFilter? = null
private var cachedShadowBlurRadiusPx = -1

// #2: кэш нативного Skia-Image + его Shader по ИДЕНТИЧНОСТИ ImageBitmap (front/back стабильны на
// протяжении перетаскивания — приходят из remember в BookCurlOverlay; идентичность меняется только
// на переворот страницы). Маленький LRU; при вытеснении явно закрываем и Shader, и Image.
private const val TEXTURED_MESH_CACHE_CAPACITY = 6
private val texturedMeshShaderCache = linkedMapOf<ImageBitmap, CachedMeshTexture>()
private val texturedMeshPaint = Paint()
private val texturedMeshSampling = FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE)

private class CachedMeshTexture(
    val image: Image,
    val shader: Shader,
)

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

            // ОДИН проход разбивает индексы треугольников на лицо/изнанку (раньше — два полных скана
            // за кадр с пересозданием массивов). На выходе — точно подогнанные массивы.
            val (frontIndices, backIndices) = mesh.facingIndices(buffers)
            // Лицевые треугольники — текущая страница (нормальные UV).
            drawTexturedMesh(
                image = front,
                mesh = mesh,
                texCoords = buffers.textureCoordinates,
                indices = frontIndices,
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
                    indices = backIndices,
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

    // Достаём Skia-Image+Shader из LRU по идентичности ImageBitmap; создаём только при промахе.
    // Sampling/tile-modes/Matrix.IDENTITY постоянны ⇒ кэширование байт-идентично прежнему.
    fun cachedShader(): Shader {
        texturedMeshShaderCache[image]?.let { existing ->
            // LRU: освежаем позицию (re-put в конец порядка обхода).
            texturedMeshShaderCache.remove(image)
            texturedMeshShaderCache[image] = existing
            return existing.shader
        }
        val skiaImage = Image.makeFromBitmap(image.asSkiaBitmap())
        val shader =
            skiaImage.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, texturedMeshSampling, Matrix33.IDENTITY)
        texturedMeshShaderCache[image] = CachedMeshTexture(image = skiaImage, shader = shader)
        while (texturedMeshShaderCache.size > TEXTURED_MESH_CACHE_CAPACITY) {
            val oldestKey = texturedMeshShaderCache.keys.first()
            // Явно закрываем нативные ресурсы вытесненной записи (не полагаясь на финализатор Shader).
            texturedMeshShaderCache.remove(oldestKey)?.let { evicted ->
                evicted.shader.close()
                evicted.image.close()
            }
        }
        return shader
    }

    drawIntoCanvas { composeCanvas ->
        val canvas = composeCanvas.skiaCanvas
        texturedMeshPaint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        texturedMeshPaint.shader = cachedShader()
        canvas.drawVertices(
            VertexMode.TRIANGLES,
            mesh.vertices2d,
            mesh.shadeColors(),
            texCoords,
            indices,
            BlendMode.MODULATE,
            texturedMeshPaint,
        )
    }
}

/**
 * За ОДИН проход делит индексы треугольников на лицевые (facing ≥ 0 — текущая страница, .first) и
 * завернувшуюся изнанку (facing < 0 — следующая страница, .second). Делим, т.к. стороны кроем
 * разными текстурами. Раньше функция звалась дважды за кадр и каждый раз аллоцировала
 * ShortArray(all.size) + copyOf(n); теперь — один скан и точно подогнанные массивы.
 */
private fun BookCurlMesh.facingIndices(buffers: BookCurlMeshBuffers): Pair<ShortArray, ShortArray> {
    val all = buffers.triangleIndices
    // Первый проход: считаем число лицевых треугольников, чтобы выделить массивы точного размера.
    var frontCount = 0
    var i = 0
    while (i < all.size) {
        if (facing[all[i].toInt()] + facing[all[i + 1].toInt()] + facing[all[i + 2].toInt()] >= 0f) frontCount += 3
        i += 3
    }
    val frontIndices = ShortArray(frontCount)
    val backIndices = ShortArray(all.size - frontCount)
    // Второй проход: раскладываем тройки индексов по лицу/изнанке.
    var frontPos = 0
    var backPos = 0
    i = 0
    while (i < all.size) {
        if (facing[all[i].toInt()] + facing[all[i + 1].toInt()] + facing[all[i + 2].toInt()] >= 0f) {
            frontIndices[frontPos++] = all[i]
            frontIndices[frontPos++] = all[i + 1]
            frontIndices[frontPos++] = all[i + 2]
        } else {
            backIndices[backPos++] = all[i]
            backIndices[backPos++] = all[i + 1]
            backIndices[backPos++] = all[i + 2]
        }
        i += 3
    }
    return frontIndices to backIndices
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
    // Сила тени — по фактическому ПОДЪЁМУ цилиндра (meshShadow), БЕЗ множителя shadeFade: shadeFade
    // (горб светотени лица) гаснет уже с progress=0.55, из-за чего тень от ещё закрученного листа
    // преждевременно слабела. meshShadow сам = 0 в покое (подъёма нет) и спадает, когда лист лёг плашмя.
    val strength = meshShadow(mesh)
    if (strength <= 0.02f) return
    // Тень ОТ ЦИЛИНДРА на нижнюю (открывающуюся) страницу. ТОЧЕЧНЫЙ свет прямо над корешком: каждую
    // поднятую (z>порог) вершину проецируем лучом «свет→вершина» на плоскость страницы (тени расходятся
    // ОТ корешка тем дальше, чем выше вершина); силуэт объединения поднятых треугольников (non-zero
    // winding ⇒ без двойного затемнения). Рисуется ПЕРВОЙ — поверх under-page, но ДО paperBase/меша:
    // под самим листом тень перекрыта листом, а на открытую страницу падает видимая тень.
    val v = mesh.vertices3d
    val threshold = (mesh.maxLiftPx * SHADOW_LIFT_THRESHOLD).coerceAtLeast(0.5f)
    val spineX = if (mesh.direction > 0) 0f else mesh.widthPx
    val lightY = mesh.heightPx * 0.5f
    val lightZ = (mesh.widthPx * SHADOW_LIGHT_HEIGHT_FRAC).coerceAtLeast(mesh.maxLiftPx + 1f)
    // #1: переиспользуем file-scoped PathBuilder вместо аллокации нового каждый кадр. ГЕОМЕТРИЯ
    // строится заново (проекция цилиндра меняется каждый кадр по мере подъёма) — переиспользуем
    // только ОБЪЕКТ билдера (reset), не его содержимое.
    shadowCastPathBuilder.reset()
    var any = false

    fun shadowT(i: Int): Float = v[i * 3 + 2] / (lightZ - v[i * 3 + 2])

    fun projX(i: Int): Float = v[i * 3].let { vx -> vx + shadowT(i) * (vx - spineX) }

    fun projY(i: Int): Float = v[i * 3 + 1].let { vy -> vy + shadowT(i) * (vy - lightY) }

    fun addTriangle(
        a: Int,
        b: Int,
        c: Int,
    ) {
        if ((v[a * 3 + 2] + v[b * 3 + 2] + v[c * 3 + 2]) / 3f <= threshold) return
        shadowCastPathBuilder.moveTo(projX(a), projY(a)).lineTo(projX(b), projY(b)).lineTo(projX(c), projY(c)).closePath()
        any = true
    }
    for (row in 0 until mesh.rows) {
        for (col in 0 until mesh.columns) {
            val p00 = row * (mesh.columns + 1) + col
            addTriangle(p00, p00 + 1, p00 + mesh.columns + 2)
            addTriangle(p00, p00 + mesh.columns + 2, p00 + mesh.columns + 1)
        }
    }
    if (!any) return
    // Радиус размытия — в супер-сэмплированных ПИКСЕЛАХ текстуры; на пике подъёма доходит до 20-30px
    // (доминирующая стоимость на GPU). Капируем потолком и квантуем до целого пикселя: makeBlur
    // пересоздаём только при смене квантованного радиуса, иначе переиспользуем кэшированный фильтр.
    val blurPx = (mesh.maxLiftPx * SHADOW_CAST_BLUR).coerceIn(1f, SHADOW_CAST_BLUR_MAX_PX)
    val quantizedBlurPx = blurPx.toInt()
    if (quantizedBlurPx != cachedShadowBlurRadiusPx) {
        cachedShadowBlurFilter?.close()
        cachedShadowBlurFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, quantizedBlurPx.toFloat())
        cachedShadowBlurRadiusPx = quantizedBlurPx
    }
    shadowCastPaint.color = paint.shadow.copy(alpha = SHADOW_CAST_ALPHA * strength).toArgb()
    shadowCastPaint.maskFilter = cachedShadowBlurFilter
    // snapshot() отдаёт Path для отрисовки, НЕ сбрасывая билдер (reset делаем в начале кадра).
    // Этот Path короткоживущий — закрываем сразу, чтобы не копить нативную память.
    val shadowPath = shadowCastPathBuilder.snapshot()
    try {
        canvas.drawPath(shadowPath, shadowCastPaint)
    } finally {
        shadowPath.close()
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

/** Прозрачность (на пике подъёма) тени, отбрасываемой цилиндром на нижнюю страницу. Это главный
 * сигнал «лист приподнят и разворачивается» (а не просто едет/проявляется) — держим заметным. */
private const val SHADOW_CAST_ALPHA = 0.40f

/** Высота точечного света над страницей (× ширину листа): ниже свет ⇒ длиннее тени от корешка ⇒
 * тень дальше уходит на открывающийся разворот и лучше видна. */
private const val SHADOW_LIGHT_HEIGHT_FRAC = 0.85f

/** Радиус размытия тени (× подъём цилиндра): мягкая тень читается как «парящий» лист, а не край. */
private const val SHADOW_CAST_BLUR = 0.09f

// Потолок радиуса размытия тени (в супер-сэмплированных пикселах текстуры): ограничивает худший
// случай стоимости makeBlur на GPU, но даёт тени быть достаточно мягкой для ощущения объёма.
private const val SHADOW_CAST_BLUR_MAX_PX = 24f

/** Порог высоты (× maxLift), выше которого вершина считается «поднятой» и отбрасывает тень. */
private const val SHADOW_LIFT_THRESHOLD = 0.12f
