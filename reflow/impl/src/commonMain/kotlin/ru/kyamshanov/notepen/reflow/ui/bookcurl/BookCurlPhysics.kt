package ru.kyamshanov.notepen.reflow.ui.bookcurl

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
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
    val spec: FloatArray = FloatArray(0),
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

    /**
     * Строит сетку листа как РАЗВЁРТЫВАЕМЫЙ КОНУС: вершина на корешке у строки захвата, радиус завитка
     * растёт от вершины (тугой залом у захвата ⇒ диагональный dog-ear от угла). Развёртка ⇒ изометрия
     * (нерастяжимо вдоль и поперёк — нет «резины»); корешок всегда на книге (нельзя оторвать); к концу
     * лист доворачивается за π и ложится плашмя на дальнюю сторону. Форма зависит от точки захвата.
     */
    fun mesh(
        state: BookCurlState,
        widthPx: Float,
        heightPx: Float,
        profile: BookCurlProfile,
        material: BookCurlMaterial = BookCurlMaterial.Default,
    ): BookCurlMesh {
        val w = widthPx.coerceAtLeast(1f)
        val build =
            MeshBuild(
                columns = profile.columns.coerceAtLeast(2),
                rows = profile.rows.coerceAtLeast(2),
                widthPx = w,
                heightPx = heightPx.coerceAtLeast(1f),
                gripY = state.gripY,
                fingerY = state.fingerY,
                direction = state.safeDirection,
                progress = state.progress.coerceIn(0f, 1f),
                derived = BookCurlDerived(material, w),
            )
        for (row in 0..build.rows) build.marchRow(row)
        return build.toMesh()
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
}

/**
 * Изменяемое состояние построения одной сетки листа. Лист заворачивается вокруг РАЗВЁРТЫВАЕМОГО КОНУСА:
 * вершина на корешке у строки захвата (apexY=gripY), рёбра ПАРАЛЛЕЛЬНЫ корешку, радиус завитка rho(y)
 * растёт от вершины (тугой залом у захвата, мягче вдали ⇒ диагональный dog-ear от угла). Конус — это
 * развёртка ⇒ изометрия (нерастяжимо вдоль и поперёк). Корешок (s=0) ВСЕГДА на книге (z=0) — не оторвать.
 * В конце (layFrac) панель доворачивается за π и радиус схлопывается ⇒ лист ЛОЖИТСЯ плашмя на дальнюю
 * сторону. Один объект на вызов `mesh()`.
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
    val derived: BookCurlDerived,
) {
    private val count = (columns + 1) * (rows + 1)
    private val vertices2d = FloatArray(count * 2)
    private val vertices3d = FloatArray(count * 3)
    private val light = FloatArray(count)
    private val spec = FloatArray(count)
    private val facing = FloatArray(count)
    private val camera = (widthPx * CURL_CAMERA_DISTANCE).coerceAtLeast(1f)
    private val centerX = widthPx * 0.5f
    private val centerY = heightPx * 0.5f
    private val radius = derived.rCurl.coerceAtLeast(1f)

    // Вершина конуса — на корешке у строки захвата; угол конуса (dog-ear) — от смещения захвата к краю и
    // увода пальца, спадает к развороту (coneFade), чтобы не растягивать поперёк. Линия залома sFold едет
    // к корешку; wrap — сколько завёрнуто (0..π). В окне layFrac панель доворачивается за π (layDown) и
    // радиус схлопывается (radiusScale) ⇒ лист ложится плашмя.
    private val apexY = gripY.coerceIn(0f, heightPx)
    private val edgeBias = (abs(apexY - heightPx * 0.5f) / (heightPx * 0.5f).coerceAtLeast(1f)).coerceIn(0f, 1f)
    private val fingerDrift = (abs(fingerY - apexY) / heightPx).coerceIn(0f, 1f)
    private val tanBeta0 = (TAN_BETA_EDGE * edgeBias + TAN_BETA_FINGER * fingerDrift).coerceAtMost(TAN_BETA_MAX)
    private val coneFade = smoothstep01((progress - CONE_FADE_START) / (LAYDOWN_START - CONE_FADE_START))
    private val tanBeta = tanBeta0 * (1f - coneFade)
    private val sFold = (widthPx * (1f - progress)).coerceIn(0f, widthPx)
    private val wrap = (PI_F * progress).coerceIn(0f, PI_F)
    private val layFrac = smoothstep01((progress - LAYDOWN_START) / (1f - LAYDOWN_START))
    private val radiusScale = 1f - RADIUS_COLLAPSE * layFrac
    private val panelAng = wrap + LAYDOWN_MAX * layFrac
    private var maxLift = 0f

    /**
     * Одна строка листа: профиль завитка с ПОСТРОЧНЫМ радиусом [rho] (растёт от вершины конуса у захвата).
     * До [sFold] лист плоско лежит на книге (z=0); затем дуга радиуса [rho] (phi 0→[wrap]); дальше — панель
     * под углом [panelAng] (в конце >π ⇒ z уходит вниз и клэмп кладёт лист плашмя). Корешок s=0 ≤ sFold ⇒ z=0.
     */
    fun marchRow(row: Int) {
        val y = row / rows.toFloat() * heightPx
        val rho = ((radius + abs(y - apexY) * tanBeta) * radiusScale).coerceAtLeast(EPS_RADIUS)
        val foldEnd = sFold + rho * wrap
        val hEnd = sFold + rho * sin(wrap)
        val zEnd = rho * (1f - cos(wrap))
        for (col in 0..columns) {
            val u = col / columns.toFloat()
            val s = if (direction > 0) u * widthPx else (1f - u) * widthPx
            val h: Float
            val z: Float
            val theta: Float
            when {
                s <= sFold -> {
                    h = s
                    z = 0f
                    theta = 0f
                }
                s <= foldEnd -> {
                    val phi = (s - sFold) / rho
                    h = sFold + rho * sin(phi)
                    z = rho * (1f - cos(phi))
                    theta = phi
                }
                else -> {
                    val a = s - foldEnd
                    h = hEnd + a * cos(panelAng)
                    z = zEnd + a * sin(panelAng)
                    theta = panelAng
                }
            }
            writeVertex(row = row, col = col, worldX = h, worldY = y, z = z, theta = theta)
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
        val facingCos = cos(theta)
        val zc = z.coerceAtLeast(0f)
        val xPage = if (direction > 0) worldX else widthPx - worldX
        val persp = camera / (camera - zc.coerceAtMost(camera * PERSP_CAP))
        vertices2d[idx * 2] = centerX + (xPage - centerX) * persp
        vertices2d[idx * 2 + 1] = centerY + (worldY - centerY) * persp
        vertices3d[idx * 3] = xPage
        vertices3d[idx * 3 + 1] = worldY
        vertices3d[idx * 3 + 2] = zc
        light[idx] = diffuse(facingCos)
        spec[idx] = specular(theta, facingCos)
        facing[idx] = facingCos
        maxLift = max(maxLift, zc)
    }

    /** Диффуз: ярче там, где поверхность смотрит на свет (лицо/завёрнутая изнанка), темнее на сгибе. */
    private fun diffuse(facingCos: Float): Float {
        val lit = (CURL_AMBIENT + CURL_DIFFUSE * abs(facingCos)).coerceIn(0f, 1f)
        return if (facingCos >= 0f) lit else lit * CURL_BACK_DIM
    }

    /** Блик (Blinn): узкий для глянца, нулевой для матовых; только на лицевой стороне. */
    private fun specular(
        phi: Float,
        facingCos: Float,
    ): Float {
        if (derived.glossiness < GLOSS_MIN || facingCos < 0f) return 0f
        val nH = (-sin(phi) * HALF_X + cos(phi) * HALF_Z).coerceAtLeast(0f)
        return derived.glossiness * nH.pow(derived.glossShininess)
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
            spec = spec,
        )
}

/**
 * Per-vertex ARGB множители (серый) для MODULATE-затенения текстуры: диффуз [BookCurlMesh.light] плюс
 * блик [BookCurlMesh.spec] (для глянцевых материалов высветляет гребень загиба к белому). На плоской
 * части ≈ белый, на сгибе темнее, на изнанке — освещённая подложка.
 */
internal fun BookCurlMesh.shadeColors(): IntArray {
    val out = IntArray(vertexCount)
    val hasSpec = spec.size == vertexCount
    for (i in 0 until vertexCount) {
        val shade = if (hasSpec) light[i] + spec[i] else light[i]
        val g = (shade.coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
        out[i] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
    }
    return out
}

private const val VERTICES_PER_CELL = 6

/** π как Float — потолок накопленного угла загиба (полный переворот листа). */
private val PI_F = PI.toFloat()

/** Дистанция камеры (в ширинах листа): меньше — сильнее перспективный «подъём» загиба к зрителю.
 * Держим большой (почти ортографично): близкая камера раздувает поднятую панель и веером гнёт строки. */
private const val CURL_CAMERA_DISTANCE = 4.5f

/** Потолок z для перспективы (доля дистанции камеры) — деление не уходит в ноль. */
private const val PERSP_CAP = 0.82f

/** Потолок угла конуса (tan) — диагональный dog-ear у углового захвата; держит поперечную изометрию <~1%. */
private const val TAN_BETA_MAX = 0.06f

/** Вклад смещения захвата к краю (верх/низ) в угол конуса. */
private const val TAN_BETA_EDGE = 0.06f

/** Вклад увода пальца по вертикали в угол конуса. */
private const val TAN_BETA_FINGER = 0.025f

/** Прогресс, с которого угол конуса начинает спадать (к развороту дуга не должна растягивать поперёк). */
private const val CONE_FADE_START = 0.45f

/** Прогресс, с которого начинается «укладывание» листа плашмя. */
private const val LAYDOWN_START = 0.80f

/** Доворот панели за π в конце (рад, ~25°) — z панели уходит вниз, лист ложится на дальнюю сторону. */
private const val LAYDOWN_MAX = 0.4363f

/** Доля схлопывания радиуса завитка к концу — тугой залом у корешка, лист плашмя. */
private const val RADIUS_COLLAPSE = 0.90f

/** Пол радиуса завитка (px) — залом тугой, но конечный (без деления на ноль). */
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

/** Изнанка чуть темнее лица (подложка бумаги), но всё равно освещена, а не в тени. */
private const val CURL_BACK_DIM = 0.92f

/** Глянцевость ниже этого порога — блик не считаем (матовая/газетная/офисная). */
private const val GLOSS_MIN = 0.05f

/** Половинный вектор (между взглядом 0,1 и светом спереди-сверху) для расчёта блика. */
private const val HALF_X = 0.178f
private const val HALF_Z = 0.984f
