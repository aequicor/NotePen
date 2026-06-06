package ru.kyamshanov.notepen.reflow.ui.bookcurl

import kotlin.math.PI
import kotlin.math.abs
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

internal data class BookCurlMesh(
    val columns: Int,
    val rows: Int,
    val widthPx: Float,
    val heightPx: Float,
    val vertices2d: FloatArray,
    val vertices3d: FloatArray,
    val light: FloatArray,
    val maxLiftPx: Float,
    val progress: Float,
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
        elapsedSeconds: Float,
        profile: BookCurlProfile,
    ): BookCurlMesh {
        val columns = profile.columns.coerceAtLeast(2)
        val rows = profile.rows.coerceAtLeast(2)
        val count = (columns + 1) * (rows + 1)
        val vertices2d = FloatArray(count * 2)
        val vertices3d = FloatArray(count * 3)
        val light = FloatArray(count)
        val progress = state.progress.coerceIn(0f, 1f)
        val direction = state.safeDirection
        val gripY = state.gripY.coerceIn(0f, heightPx)
        val wind = windStrength(state, progress)
        val bandRadius = lerp(heightPx * 0.08f, heightPx * 0.58f, smooth(progress))
        var maxLift = 0f
        for (row in 0..rows) {
            val y = row * heightPx / rows.toFloat()
            val gripDistance = abs(y - gripY)
            val gripInfluence = gaussian(gripDistance, bandRadius)
            for (col in 0..columns) {
                val u = col / columns.toFloat()
                val idx = row * (columns + 1) + col
                val edge = if (direction > 0) u else 1f - u
                val x = u * widthPx
                val rowLag = (gripDistance / heightPx).coerceIn(0f, 1f)
                val delay = (rowLag * 0.52f + (1f - edge) * 0.14f).coerceIn(0f, 0.62f)
                val local = smooth(((progress - delay) / (1f - delay)).coerceIn(0f, 1f))
                val freeEdge = edge * edge
                val curlAngle = local * PI.toFloat() * 1.18f * freeEdge
                val radius = widthPx * lerp(0.26f, 0.08f, progress)
                val lift = sin(curlAngle).coerceAtLeast(0f) * radius * gripInfluence
                val rowTravel = lerp(0.32f, 1.82f, gripInfluence)
                val travel = widthPx * local * freeEdge * rowTravel
                val flutter =
                    flutter(
                        y = y,
                        edge = edge,
                        elapsedSeconds = elapsedSeconds,
                        progress = progress,
                        wind = wind,
                        gripInfluence = gripInfluence,
                    )
                val signedTravel = travel * direction
                val projectedLift = lift * 0.16f * direction
                val diagonalLag = widthPx * rowLag * progress * edge * edge * 0.26f * direction
                val x2 = x - signedTravel + projectedLift + diagonalLag + flutter * 0.35f * direction
                val y2 = y + (y - gripY) * local * edge * 0.035f + flutter * 0.22f
                val z = (lift + flutter).coerceAtLeast(0f)
                maxLift = max(maxLift, z)

                vertices2d[idx * 2] = x2
                vertices2d[idx * 2 + 1] = y2
                vertices3d[idx * 3] = x2
                vertices3d[idx * 3 + 1] = y2
                vertices3d[idx * 3 + 2] = z
                val lightValue = lightAt(progress, edge, gripInfluence, z, radius)
                light[idx] = lightValue
            }
        }
        return BookCurlMesh(columns, rows, widthPx, heightPx, vertices2d, vertices3d, light, maxLift, progress)
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

    private fun flutter(
        y: Float,
        edge: Float,
        elapsedSeconds: Float,
        progress: Float,
        wind: Float,
        gripInfluence: Float,
    ): Float {
        val amplitude = 10f * wind * edge * edge * (1f - progress * 0.35f) * gripInfluence
        val waveA = sin(y * 0.036f + elapsedSeconds * 14f + progress * 5f)
        val waveB = sin(y * 0.017f - elapsedSeconds * 9f)
        return amplitude * (waveA * 0.72f + waveB * 0.28f)
    }

    private fun lightAt(
        progress: Float,
        edge: Float,
        gripInfluence: Float,
        lift: Float,
        radius: Float,
    ): Float {
        val liftRatio = (lift / radius.coerceAtLeast(1f)).coerceIn(0f, 1f)
        val diffuse = 0.97f + 0.025f * edge
        val rim = 0.18f * sin(progress * PI.toFloat()).coerceAtLeast(0f) * edge * gripInfluence
        val selfShadow = 0.32f * liftRatio * gripInfluence
        return (diffuse + rim - selfShadow).coerceIn(0.62f, 1.08f)
    }

    private fun gaussian(
        distance: Float,
        sigma: Float,
    ): Float {
        val s = sigma.coerceAtLeast(1f)
        return exp(-(distance * distance) / (2f * s * s))
    }

    private fun smooth(t: Float): Float = t * t * (3f - 2f * t)

    private fun lerp(
        a: Float,
        b: Float,
        t: Float,
    ): Float = a + (b - a) * t.coerceIn(0f, 1f)
}

private const val VERTICES_PER_CELL = 6
