package ru.kyamshanov.notepen.annotation.domain.shape

import ru.kyamshanov.notepen.annotation.domain.model.DrawingPoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Превращает [RecognizedShape] в плотную выборку [DrawingPoint], пригодную
 * для замены `livePoints` штриха. Pressure/tilt берутся из исходного штриха
 * (как среднее), чтобы snapped-штрих визуально совпадал с обычным росчерком.
 */
public object ShapeResampler {
    private const val LINE_SAMPLES: Int = 32
    private const val ELLIPSE_SAMPLES: Int = 128
    private const val RECT_SAMPLES_PER_SIDE: Int = 32

    /**
     * Возвращает плотную выборку для фигуры. Первая точка помечена `isNewPath = true`
     * (как у обычного штриха), последующие — `false`.
     */
    public fun toPoints(
        shape: RecognizedShape,
        basePressure: Float,
        baseTilt: Float,
    ): List<DrawingPoint> =
        when (shape) {
            is RecognizedShape.Line -> sampleLine(shape, basePressure, baseTilt)
            is RecognizedShape.Ellipse -> sampleEllipse(shape, basePressure, baseTilt)
            is RecognizedShape.Rectangle -> sampleRectangle(shape, basePressure, baseTilt)
        }

    private fun sampleLine(
        line: RecognizedShape.Line,
        pressure: Float,
        tilt: Float,
    ): List<DrawingPoint> {
        val n = LINE_SAMPLES
        val out = ArrayList<DrawingPoint>(n)
        for (i in 0 until n) {
            val t = i.toFloat() / (n - 1)
            val x = line.start.x + (line.end.x - line.start.x) * t
            val y = line.start.y + (line.end.y - line.start.y) * t
            out.add(
                DrawingPoint(
                    x = x,
                    y = y,
                    isNewPath = i == 0,
                    pressure = pressure,
                    tilt = tilt,
                ),
            )
        }
        return out
    }

    private fun sampleEllipse(
        e: RecognizedShape.Ellipse,
        pressure: Float,
        tilt: Float,
    ): List<DrawingPoint> {
        val n = ELLIPSE_SAMPLES
        val out = ArrayList<DrawingPoint>(n + 1)
        for (i in 0..n) {
            val t = (i.toFloat() / n) * 2f * PI.toFloat()
            val x = e.cx + e.rx * cos(t)
            val y = e.cy + e.ry * sin(t)
            out.add(
                DrawingPoint(
                    x = x,
                    y = y,
                    isNewPath = i == 0,
                    pressure = pressure,
                    tilt = tilt,
                ),
            )
        }
        return out
    }

    private fun sampleRectangle(
        r: RecognizedShape.Rectangle,
        pressure: Float,
        tilt: Float,
    ): List<DrawingPoint> {
        val corners =
            listOf(
                r.left to r.top,
                r.right to r.top,
                r.right to r.bottom,
                r.left to r.bottom,
                r.left to r.top,
            )
        val n = RECT_SAMPLES_PER_SIDE
        val out = ArrayList<DrawingPoint>(4 * n + 1)
        var index = 0
        for (side in 0 until 4) {
            val (sx, sy) = corners[side]
            val (ex, ey) = corners[side + 1]
            for (i in 0 until n) {
                val t = i.toFloat() / n
                out.add(
                    DrawingPoint(
                        x = sx + (ex - sx) * t,
                        y = sy + (ey - sy) * t,
                        isNewPath = index == 0,
                        pressure = pressure,
                        tilt = tilt,
                    ),
                )
                index++
            }
        }
        // Закрываем контур точно в стартовую точку (последний угол).
        out.add(
            DrawingPoint(
                x = r.left,
                y = r.top,
                pressure = pressure,
                tilt = tilt,
            ),
        )
        return out
    }
}
