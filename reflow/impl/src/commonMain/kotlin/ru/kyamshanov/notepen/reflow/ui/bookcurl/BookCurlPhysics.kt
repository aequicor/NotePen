package ru.kyamshanov.notepen.reflow.ui.bookcurl

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal enum class BookCurlPhase {
    Idle,
    Dragging,
    Returning,
    Completing,
}

/**
 * Состояние перелистывания одного кадра. Геометрия читает только [progress], [direction], [gripY]
 * (строка захвата, фиксирована на жест) и [fingerY] (живая вертикаль пальца — её отличие от [gripY]
 * наклоняет линию сгиба ⇒ диагональный dog-ear). [velocityX] (реальная px/с) нужна лишь для решения
 * «долистать или вернуть» ([BookCurlPhysics.shouldComplete]) и звука.
 */
internal data class BookCurlState(
    val direction: Int,
    val gripY: Float,
    val fingerY: Float,
    val velocityX: Float,
    val progress: Float,
    val phase: BookCurlPhase,
) {
    val safeDirection: Int get() = if (direction < 0) -1 else 1
}

/** Плотность сетки листа. Изгиб одномерный (поперёк линии сгиба), поэтому колонок больше, чем строк. */
internal data class BookCurlProfile(
    val columns: Int,
    val rows: Int,
) {
    companion object {
        val High = BookCurlProfile(columns = 60, rows = 32)
        val Medium = BookCurlProfile(columns = 48, rows = 26)
        val Low = BookCurlProfile(columns = 38, rows = 20)
    }
}

internal data class BookCurlMesh(
    val columns: Int,
    val rows: Int,
    val widthPx: Float,
    val heightPx: Float,
    val vertices2d: FloatArray,
    val vertices3d: FloatArray,
    val light: FloatArray,
    val facing: FloatArray,
    val maxLiftPx: Float,
    val progress: Float,
    val direction: Int,
    val shadeFade: Float,
) {
    val vertexCount: Int get() = (columns + 1) * (rows + 1)
}

internal data class BookCurlMeshBuffers(
    val columns: Int,
    val rows: Int,
    val textureCoordinates: FloatArray,
    val triangleIndices: ShortArray,
) {
    val vertexCount: Int get() = (columns + 1) * (rows + 1)
}

internal fun bookCurlMeshBuffers(
    columns: Int,
    rows: Int,
    widthPx: Float,
    heightPx: Float,
): BookCurlMeshBuffers {
    val safeColumns = columns.coerceAtLeast(2)
    val safeRows = rows.coerceAtLeast(2)
    val vertexCount = (safeColumns + 1) * (safeRows + 1)
    val textureCoordinates = FloatArray(vertexCount * 2)
    for (row in 0..safeRows) {
        val y = heightPx * row / safeRows.toFloat()
        for (col in 0..safeColumns) {
            val vertexIndex = row * (safeColumns + 1) + col
            val offset = vertexIndex * 2
            textureCoordinates[offset] = widthPx * col / safeColumns.toFloat()
            textureCoordinates[offset + 1] = y
        }
    }

    val triangleIndices = ShortArray(safeColumns * safeRows * VERTICES_PER_CELL)
    var index = 0
    for (row in 0 until safeRows) {
        for (col in 0 until safeColumns) {
            val p00 = row * (safeColumns + 1) + col
            val p10 = p00 + 1
            val p01 = p00 + safeColumns + 1
            val p11 = p01 + 1
            triangleIndices[index++] = p00.toShort()
            triangleIndices[index++] = p10.toShort()
            triangleIndices[index++] = p11.toShort()
            triangleIndices[index++] = p00.toShort()
            triangleIndices[index++] = p11.toShort()
            triangleIndices[index++] = p01.toShort()
        }
    }

    return BookCurlMeshBuffers(
        columns = safeColumns,
        rows = safeRows,
        textureCoordinates = textureCoordinates,
        triangleIndices = triangleIndices,
    )
}

internal object BookCurlPhysics {
    const val COMMIT_PROGRESS = 0.45f

    /** Порог скорости флика для долистывания (реальные px/с экранной протяжки): быстрый щелчок
     * пальцем долистывает даже при малом прогрессе, медленная протяжка — нет. */
    const val FLING_VELOCITY_PX = 600f

    fun shouldComplete(state: BookCurlState): Boolean {
        val fling = -state.velocityX * state.safeDirection > FLING_VELOCITY_PX
        return state.progress >= COMMIT_PROGRESS || fling
    }

    fun autoProfile(
        widthPx: Float,
        heightPx: Float,
        density: Float,
    ): BookCurlProfile {
        val pixels = widthPx * heightPx
        return when {
            pixels > 1_800_000f && density >= 1.5f -> BookCurlProfile.High
            pixels > 850_000f -> BookCurlProfile.Medium
            else -> BookCurlProfile.Low
        }
    }

    /**
     * Строит сетку переворачиваемого листа как ФРОНТАЛЬНЫЙ (ортографический) ЦИЛИНДРИЧЕСКИЙ завиток:
     * камера смотрит на лист в плоскости экрана, z идёт ТОЛЬКО в затенение, не в экранную позицию —
     * поэтому лист никогда не встаёт ребром к камере. Часть за линией сгиба заворачивается вокруг
     * цилиндра радиуса [PageTurnStyle.radiusFrac], дальше ложится изнанкой (следующая страница).
     * Развёртка цилиндра — изометрия ⇒ нет «резины». Наклон линии сгиба (от ухода пальца) ограничен,
     * корешок зажат построчно у книги (нельзя оторвать), радиус схлопывается к концу ⇒ лист ложится
     * плашмя на дальнюю сторону, открывая следующую страницу.
     */
    fun mesh(
        state: BookCurlState,
        widthPx: Float,
        heightPx: Float,
        profile: BookCurlProfile,
        style: PageTurnStyle = PageTurnStyle.Default,
    ): BookCurlMesh {
        val build =
            MeshBuild(
                columns = profile.columns.coerceAtLeast(2),
                rows = profile.rows.coerceAtLeast(2),
                widthPx = widthPx.coerceAtLeast(1f),
                heightPx = heightPx.coerceAtLeast(1f),
                gripY = state.gripY,
                fingerY = state.fingerY,
                direction = state.safeDirection,
                progress = state.progress.coerceIn(0f, 1f),
                radiusFrac = style.radiusFrac,
            )
        for (row in 0..build.rows) build.marchRow(row)
        return build.toMesh()
    }
}

/**
 * Изменяемое построение одной сетки листа за вызов [BookCurlPhysics.mesh].
 *
 * Координаты листа: s ∈ [0, w] вдоль ширины (s=0 — корешок, s=w — свободный край), y ∈ [0, h].
 * Линия сгиба едет от свободного края к корешку (baseFoldS = w·(1−progress)), с ограниченным наклоном
 * [tiltS] по строкам. За линией сгиба лист заворачивается вокруг цилиндра радиуса [radius] (дуга φ
 * 0→[wrap]≤π), затем — плоская изнанка («задняя панель»). Зеркало направления — в [writeVertex].
 */
private class MeshBuild(
    val columns: Int,
    val rows: Int,
    val widthPx: Float,
    val heightPx: Float,
    val gripY: Float,
    val fingerY: Float,
    val direction: Int,
    val progress: Float,
    radiusFrac: Float,
) {
    private val count = (columns + 1) * (rows + 1)
    private val vertices2d = FloatArray(count * 2)
    private val vertices3d = FloatArray(count * 3)
    private val light = FloatArray(count)
    private val facing = FloatArray(count)
    private var maxLift = 0f

    private val baseFoldS = (widthPx * (1f - progress)).coerceIn(0f, widthPx)

    // Наклон линии сгиба от ухода пальца по вертикали (диагональный dog-ear). ОГРАНИЧЕН TILT_GAIN:
    // наклонный цилиндр приближённо изометричен лишь при малом наклоне — иначе поперечное растяжение
    // («резина»). Квадратичная оценка ошибки держит её <~1.5 % даже при полном уводе.
    // ЗАТУХАЕТ к концу переворота (как в реальности): завёрнутый лист доворачивается вокруг корешка и
    // ВЫПРЯМЛЯЕТСЯ, ложась ровно — иначе на большом прогрессе перекошенная панель даёт артефакты.
    private val tiltS =
        run {
            val fade = 1f - smoothstep01((progress - TILT_FADE_START) / (TILT_FADE_END - TILT_FADE_START))
            ((fingerY - gripY) / heightPx).coerceIn(-1f, 1f) * TILT_GAIN * widthPx * fade
        }

    // Радиус завитка: постоянный до начала схлопывания, затем → 0 к progress=1 ⇒ лист ложится плашмя
    // (изнанка/следующая страница оказываются на дальней стороне, z→0). EPS_RADIUS — лишь пол деления.
    private val radius =
        run {
            val collapse = smoothstep01((progress - R_COLLAPSE_START) / (1f - R_COLLAPSE_START))
            (radiusFrac * widthPx * (1f - collapse)).coerceAtLeast(EPS_RADIUS)
        }

    // «Горб» затенения: 0 в НАЧАЛЬНОМ (progress→0) и КОНЕЧНОМ (progress→1) положениях, ~1 в середине.
    // В покое (лист у статики в начале и лёг плашмя в конце) цвета СОВПАДАЮТ со статичной страницей —
    // без затемнения/блика; 3D-светотень нарастает только в движении. Вход (fadeIn) симметрично зеркалит
    // выход (fadeOut), поэтому ни на старте, ни в конце «искажения» цвета подсветок нет.
    val shadeFade =
        run {
            val fadeIn = smoothstep01(progress / SHADE_FADE_IN_END)
            val fadeOut = 1f - smoothstep01((progress - SHADE_FADE_START) / (1f - SHADE_FADE_START))
            fadeIn * fadeOut
        }

    /**
     * Одна строка листа. Линия сгиба для строки зажата у корешка (foldS≥0): корешок (s=0≤foldS) всегда
     * плоско на книге (z=0) при любом наклоне/прогрессе — оторвать нельзя. [wrap]=min(π,(w−foldS)/r) —
     * сколько листа реально завернулось (геометрия, не отдельный прогресс). До foldS — плоско; затем
     * дуга радиуса [radius]; дальше — плоская изнанка по касательной (z держится ⇒ лежит над страницей,
     * во фронтальной проекции z уходит только в свет).
     */
    fun marchRow(row: Int) {
        val y = row / rows.toFloat() * heightPx
        val foldS = (baseFoldS + tiltS * ((y - gripY) / heightPx)).coerceIn(0f, widthPx)
        val wrap = min(PI_F, (widthPx - foldS) / radius).coerceAtLeast(0f)
        val arcEnd = foldS + radius * wrap
        val crestX = foldS + radius * sin(wrap)
        val crestZ = radius * (1f - cos(wrap))
        for (col in 0..columns) {
            val u = col / columns.toFloat()
            val s = if (direction > 0) u * widthPx else (1f - u) * widthPx
            val worldX: Float
            val z: Float
            val theta: Float
            when {
                s <= foldS -> {
                    worldX = s
                    z = 0f
                    theta = 0f
                }
                s <= arcEnd -> {
                    val phi = (s - foldS) / radius
                    worldX = foldS + radius * sin(phi)
                    z = radius * (1f - cos(phi))
                    theta = phi
                }
                else -> {
                    val a = s - arcEnd
                    worldX = crestX + a * cos(wrap)
                    z = (crestZ + a * sin(wrap)).coerceAtLeast(0f)
                    theta = wrap
                }
            }
            writeVertex(row = row, col = col, worldX = worldX, worldY = y, z = z, theta = theta)
        }
    }

    private fun writeVertex(
        row: Int,
        col: Int,
        worldX: Float,
        worldY: Float,
        z: Float,
        theta: Float,
    ) {
        val idx = row * (columns + 1) + col
        val zc = z.coerceAtLeast(0f)
        // Фронтальная (ортографическая) проекция: экранные координаты = (xPage, worldY), БЕЗ перспективы.
        // z НЕ влияет на позицию — только на свет/тень; поэтому лист не встаёт ребром к камере.
        val xPage = if (direction > 0) worldX else widthPx - worldX
        val facingCos = cos(theta)
        vertices2d[idx * 2] = xPage
        vertices2d[idx * 2 + 1] = worldY
        vertices3d[idx * 3] = xPage
        vertices3d[idx * 3 + 1] = worldY
        vertices3d[idx * 3 + 2] = zc
        // По мере укладки плашмя (shadeFade→0) затенение/приглушение изнанки уходит, и лист
        // выходит на ПОЛНУЮ яркость: лежащая в финале страница освещена нормально, а не тёмно-серая.
        val lit = diffuse(facingCos)
        light[idx] = lit + (1f - lit) * (1f - shadeFade)
        facing[idx] = facingCos
        maxLift = max(maxLift, zc)
    }

    /** Диффуз: ярче там, где поверхность смотрит на свет (лицо/завёрнутая изнанка), темнее на сгибе. */
    private fun diffuse(facingCos: Float): Float {
        val lit = (CURL_AMBIENT + CURL_DIFFUSE * abs(facingCos)).coerceIn(0f, 1f)
        return if (facingCos >= 0f) lit else lit * CURL_BACK_DIM
    }

    fun toMesh(): BookCurlMesh =
        BookCurlMesh(
            columns = columns,
            rows = rows,
            widthPx = widthPx,
            heightPx = heightPx,
            vertices2d = vertices2d,
            vertices3d = vertices3d,
            light = light,
            facing = facing,
            maxLiftPx = maxLift,
            progress = progress,
            direction = direction,
            shadeFade = shadeFade,
        )
}

/**
 * Per-vertex ARGB множители (серый) для MODULATE-затенения текстуры: значение диффуза
 * [BookCurlMesh.light]. На плоской части ≈ белый, на сгибе темнее, на изнанке — освещённая подложка.
 */
internal fun BookCurlMesh.shadeColors(): IntArray {
    val out = IntArray(vertexCount)
    for (i in 0 until vertexCount) {
        val g = (light[i].coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
        out[i] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
    }
    return out
}

private const val VERTICES_PER_CELL = 6

/** π как Float — потолок угла заворота (полцилиндра; дальше — плоская изнанка). */
private val PI_F = PI.toFloat()

/** Потолок наклона линии сгиба (доля ширины): диагональный dog-ear, но без поперечной «резины». */
private const val TILT_GAIN = 0.12f

/** Прогресс, с которого наклон сгиба начинает выпрямляться (dog-ear виден на старте отрыва угла). */
private const val TILT_FADE_START = 0.3f

/** Прогресс, к которому наклон полностью выпрямлен (лист доворачивается ровно и ложится плашмя). */
private const val TILT_FADE_END = 0.75f

/** Прогресс, с которого радиус завитка начинает схлопываться к нулю (лист ложится плашмя). */
private const val R_COLLAPSE_START = 0.55f

/** Прогресс, с которого затухают блик/тень и лист выходит на полную яркость (совпадает с началом
 * схлопывания радиуса): к финалу подсветки/приглушения не остаётся, страница лежит нормально освещённой. */
private const val SHADE_FADE_START = 0.55f

/** Прогресс, к которому светотень ВХОДИТ от нуля на старте: до него цвета как на статичной странице,
 * затем 3D-затенение нарастает к середине. Симметрично [SHADE_FADE_START] на выходе. */
private const val SHADE_FADE_IN_END = 0.25f

/** Пол радиуса завитка (px) — лишь чтобы не делить на ноль на полном перевороте. */
private const val EPS_RADIUS = 1.5f

/** Гладкая ступень 0..1: 0 при t≤0, 1 при t≥1, плавно между. */
private fun smoothstep01(t: Float): Float {
    val c = t.coerceIn(0f, 1f)
    return c * c * (3f - 2f * c)
}

/** Рассеянный (фоновый) свет: яркость поверхности ребром к свету (на сгибе). */
private const val CURL_AMBIENT = 0.55f

/** Прямой свет: добавка к яркости там, где поверхность смотрит на свет (лицо/завёрнутая изнанка). */
private const val CURL_DIFFUSE = 0.45f

/** Изнанка листа = приглушённая следующая страница: ощутимо темнее лица (низ листа в полутени),
 * но читаемо. Затемняем через MODULATE-свет (а не alpha) — лист остаётся непрозрачным. */
private const val CURL_BACK_DIM = 0.80f
