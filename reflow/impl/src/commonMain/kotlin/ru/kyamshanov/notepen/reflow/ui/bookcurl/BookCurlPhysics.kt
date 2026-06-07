package ru.kyamshanov.notepen.reflow.ui.bookcurl

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal enum class BookCurlPhase {
    Idle,
    Dragging,
    Returning,
    Completing,
}

internal data class BookCurlState(
    val direction: Int,
    val gripY: Float,
    val fingerX: Float,
    val fingerY: Float,
    val velocityX: Float,
    val velocityY: Float,
    val progress: Float,
    val phase: BookCurlPhase,
) {
    val safeDirection: Int get() = if (direction < 0) -1 else 1
}

internal data class BookCurlProfile(
    val columns: Int,
    val rows: Int,
    val solverSubsteps: Int,
) {
    companion object {
        val High = BookCurlProfile(columns = 36, rows = 52, solverSubsteps = 4)
        val Medium = BookCurlProfile(columns = 28, rows = 40, solverSubsteps = 3)
        val Low = BookCurlProfile(columns = 20, rows = 30, solverSubsteps = 2)
    }
}

/**
 * Физические свойства листа. [weight] (0..1) — тяжесть: больше → лист сильнее провисает под
 * собственным весом вдали от места захвата. [stiffness] (0.05..1) — жёсткость: больше → лист
 * держит форму, провисает и загибается меньше (плотная бумага/картон против тонкой кальки).
 */
internal data class BookCurlMaterial(
    val weight: Float = 0.5f,
    val stiffness: Float = 0.6f,
) {
    val sagFactor: Float get() = weight / stiffness.coerceAtLeast(0.05f)

    companion object {
        val Default = BookCurlMaterial()
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
    const val FIXED_STEP_SECONDS = 1f / 120f
    const val MAX_FRAME_SECONDS = 0.033f
    const val COMMIT_PROGRESS = 0.52f
    const val FLING_VELOCITY_PX = 1400f

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

    fun mesh(
        state: BookCurlState,
        widthPx: Float,
        heightPx: Float,
        profile: BookCurlProfile,
        material: BookCurlMaterial = BookCurlMaterial.Default,
    ): BookCurlMesh {
        val columns = profile.columns.coerceAtLeast(2)
        val rows = profile.rows.coerceAtLeast(2)
        val count = (columns + 1) * (rows + 1)
        val vertices2d = FloatArray(count * 2)
        val vertices3d = FloatArray(count * 3)
        val light = FloatArray(count)
        val facing = FloatArray(count)
        val progress = state.progress.coerceIn(0f, 1f)
        val direction = state.safeDirection
        // Место захвата по вертикали (0..1) — от него считаем провисание листа под весом.
        val gripFrac = (state.gripY / heightPx).coerceIn(0f, 1f)

        // Две фазы: сперва лист ПОДНИМАЕТСЯ почти прямым (жёсткий поворот вокруг корешка на угол
        // lift), и лишь после CURL_LIFT_FRACTION прогресса начинает ЗАГИБАТЬСЯ (линия загиба едет
        // от свободного края к корешку). Так страница встаёт ребром не сразу, а как настоящая.
        val radius = (widthPx * CURL_RADIUS_FRACTION).coerceAtLeast(1f)
        val lift = (progress / CURL_LIFT_FRACTION).coerceAtMost(1f) * CURL_LIFT_ANGLE
        val liftFraction = (lift / CURL_LIFT_ANGLE).coerceIn(0f, 1f)
        val curled = ((progress - CURL_LIFT_FRACTION) / (1f - CURL_LIFT_FRACTION)).coerceIn(0f, 1f)
        val curlStart = widthPx * (1f - curled)
        // Перспектива: приподнятая часть (z к зрителю) проецируется крупнее — объём, а не плоский узор.
        val camera = (widthPx * CURL_CAMERA_DISTANCE).coerceAtLeast(1f)
        val centerX = widthPx * 0.5f
        val centerY = heightPx * 0.5f
        // Подъём СЛЕДУЕТ ЗА ЗАХВАТОМ: строка захвата поднимается полностью, дальние строки —
        // тем меньше, чем дальше (провисают). Так при тяге снизу поднимается низ, а не верх.
        val sagStrength = material.sagFactor * CURL_SAG_STRENGTH
        var maxLift = 0f
        for (row in 0..rows) {
            val y = row * heightPx / rows.toFloat()
            // Чем дальше строка от захвата, тем меньше её подъём (провисает); 0 — у строки захвата.
            val vProfile = smoothstep(abs(y / heightPx - gripFrac))
            val rowLift = lift * (1f - (sagStrength * vProfile).coerceAtMost(CURL_MAX_SAG))
            for (col in 0..columns) {
                val u = col / columns.toFloat()
                val idx = row * (columns + 1) + col
                // s — расстояние вдоль листа от корешка (0) до свободного края (widthPx).
                val s = if (direction > 0) u * widthPx else (1f - u) * widthPx
                val sFrac = s / widthPx
                val point = curlPoint(s, curlStart, radius, rowLift)
                val z = point.z
                val xPage = if (direction > 0) point.h else widthPx - point.h
                // Перспектива (обе оси): приподнятая часть крупнее → трапеция (3D), а не прямоугольник.
                val persp = camera / (camera - z.coerceAtMost(camera * 0.82f))
                vertices2d[idx * 2] = centerX + (xPage - centerX) * persp
                vertices2d[idx * 2 + 1] = centerY + (y - centerY) * persp
                vertices3d[idx * 3] = xPage
                vertices3d[idx * 3 + 1] = y
                vertices3d[idx * 3 + 2] = z
                light[idx] = curlLight(point.phi) * (1f - CURL_SAG_SHADOW * liftFraction * vProfile * sFrac)
                facing[idx] = cos(point.phi)
                maxLift = max(maxLift, z)
            }
        }
        return BookCurlMesh(
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
        )
    }

    fun settleProgress(
        current: Float,
        target: Float,
        velocity: Float,
        dtSeconds: Float,
    ): Float {
        val dt = min(dtSeconds, MAX_FRAME_SECONDS).coerceAtLeast(0f)
        val stiffness = 22f
        val damping = 0.78f
        val delta = target - current
        val step = delta * (1f - exp(-stiffness * dt)) + velocity * dt * (1f - damping) / 1000f
        return (current + step).coerceIn(0f, 1f)
    }

    fun windStrength(
        state: BookCurlState,
        progress: Float,
    ): Float {
        val velocity = sqrt(state.velocityX * state.velocityX + state.velocityY * state.velocityY)
        val gestureWind = (velocity / 2600f).coerceIn(0f, 1f)
        val releaseDecay = if (state.phase == BookCurlPhase.Dragging) 1f else 1f - progress * 0.65f
        return gestureWind * releaseDecay.coerceIn(0f, 1f)
    }

    private fun curlLight(phi: Float): Float {
        // Цилиндрическое освещение (свет спереди-сверху): ярче там, где поверхность смотрит на свет
        // (|cos phi|→1), темнее всего на СГИБЕ (phi≈90°, лист ребром) — там тень от изгиба. После
        // сгиба завернувшаяся ИЗНАНКА снова ловит свет и светлеет (лишь чуть темнее лица), а не
        // тонет в тени, как раньше.
        val facing = cos(phi)
        val lit = (CURL_AMBIENT + CURL_DIFFUSE * abs(facing)).coerceIn(0f, 1f)
        return if (facing >= 0f) lit else lit * CURL_BACK_DIM
    }

    /** Гладкая S-кривая 0..1 (без острого излома) — для упругого профиля провисания. */
    private fun smoothstep(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    /**
     * Точка листа на расстоянии [s] от корешка. До [curlStart] лист — упругая ДУГА «корешок→захват»:
     * касательная к столу у корешка (угол 0) плавно набирает угол до [lift] у свободного края
     * (эластик, а не прямой пандус с изломом у корешка). Дальше лист заворачивается вокруг цилиндра
     * радиуса [radius], угол касательной phi растёт от [lift] до 180°. [CurlPoint.h] — горизонталь
     * от корешка, z — подъём.
     */
    private fun curlPoint(
        s: Float,
        curlStart: Float,
        radius: Float,
        lift: Float,
    ): CurlPoint {
        // Кривизна дуги: угол растёт линейно по длине, от 0 у корешка до lift на curlStart.
        val k = lift / curlStart.coerceAtLeast(1f)
        if (s <= curlStart || k < 1e-4f) {
            val phi = k * s
            return if (k < 1e-4f) {
                CurlPoint(h = s, z = 0f, phi = 0f)
            } else {
                CurlPoint(h = sin(phi) / k, z = (1f - cos(phi)) / k, phi = phi)
            }
        }
        return cylinderPoint(s = s, curlStart = curlStart, radius = radius, lift = lift, k = k)
    }

    /**
     * Загиб вокруг цилиндра радиуса [radius] для точки за [curlStart]. База берётся от конца упругой
     * дуги (кривизна [k], угол касательной = [lift]); дальше угол phi растёт до 180°.
     */
    private fun cylinderPoint(
        s: Float,
        curlStart: Float,
        radius: Float,
        lift: Float,
        k: Float,
    ): CurlPoint {
        val hBase = sin(lift) / k
        val zBase = (1f - cos(lift)) / k
        val arcLen = s - curlStart
        val maxArc = (PI.toFloat() - lift) * radius
        return if (arcLen <= maxArc) {
            val phi = lift + arcLen / radius
            CurlPoint(
                h = hBase + radius * (sin(phi) - sin(lift)),
                z = zBase + radius * (cos(lift) - cos(phi)),
                phi = phi,
            )
        } else {
            val flatBack = arcLen - maxArc
            CurlPoint(
                h = hBase - radius * sin(lift) - flatBack,
                z = zBase + radius * (cos(lift) + 1f),
                phi = PI.toFloat(),
            )
        }
    }
}

/**
 * Per-vertex ARGB множители (серый, по [BookCurlMesh.light]) для MODULATE-затенения текстуры на
 * загибе: на плоской части ≈ белый (без изменений), на обратном склоне цилиндра темнее.
 */
internal fun BookCurlMesh.shadeColors(): IntArray {
    val out = IntArray(vertexCount)
    for (i in 0 until vertexCount) {
        val g = (light[i].coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
        out[i] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
    }
    return out
}

private data class CurlPoint(
    val h: Float,
    val z: Float,
    val phi: Float,
)

private const val VERTICES_PER_CELL = 6

/** Радиус цилиндра загиба (доля ширины листа): чем больше — тем мягче и крупнее загиб. */
private const val CURL_RADIUS_FRACTION = 0.18f

/** Дистанция камеры (в ширинах листа): меньше — сильнее перспективный «подъём» загиба к зрителю. */
private const val CURL_CAMERA_DISTANCE = 2.6f

/** Доля прогресса на фазу подъёма «почти прямым» листом; дальше начинается загиб. */
private const val CURL_LIFT_FRACTION = 0.45f

/**
 * Угол касательной у свободного края к концу фазы подъёма (радианы, ~74°). Больше прямого пандуса:
 * дуга «съедает» высоту, поэтому угол увеличен, чтобы лист поднимался ощутимо.
 */
private const val CURL_LIFT_ANGLE = 1.3f

/** Сила тени на самом листе в месте прогиба (где провис — там темнее). */
private const val CURL_SAG_SHADOW = 0.45f

/** Рассеянный (фоновый) свет: яркость поверхности ребром к свету (на сгибе). */
private const val CURL_AMBIENT = 0.55f

/** Прямой свет: добавка к яркости там, где поверхность смотрит на свет (лицо/завёрнутая изнанка). */
private const val CURL_DIFFUSE = 0.45f

/** Изнанка чуть темнее лица (подложка бумаги), но всё равно освещена, а не в тени. */
private const val CURL_BACK_DIM = 0.92f

/** Сила провисания дальних строк (доля подъёма, ×[BookCurlMaterial.sagFactor]). */
private const val CURL_SAG_STRENGTH = 0.65f

/** Потолок провисания строки (доля подъёма) — дальняя строка не падает в ноль полностью. */
private const val CURL_MAX_SAG = 0.8f
