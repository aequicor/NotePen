package ru.kyamshanov.notepen.annotation.domain.shape

import ru.kyamshanov.notepen.annotation.domain.model.DrawingPoint
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Распознаёт в наборе сэмплированных точек штриха один из примитивов
 * ([RecognizedShape]). Чистая логика, без зависимостей от UI.
 *
 * Координаты точек — нормализованы к странице `[0..1]`. Параметр [aspect]
 * = `pageWidthPx / pageHeightPx` нужен, чтобы метрики (длины, радиальная
 * дисперсия) считались в физическом пространстве: иначе круг на A4 будет
 * выглядеть эллипсом в нормализованном пространстве.
 */
public object ShapeRecognizer {
    private const val MIN_POINTS: Int = 8

    /** `chord / pathLength` > этого порога → штрих считаем линией. */
    private const val LINE_STRAIGHTNESS: Float = 0.90f

    /** `chord / pathLength` < этого порога → штрих считаем замкнутым (кандидат на круг/прямоугольник). */
    private const val CLOSURE_RATIO: Float = 0.25f

    /**
     * Среднее отклонение `|r-1|` точек от единичной окружности после нормировки
     * на полуоси bbox — порог для эллипса. Для идеального вписанного эллипса
     * значение 0; для квадрата ≈ 0.15 (углы дальше центра, чем стороны). 0.10
     * надёжно разделяет шумный круг и шумный прямоугольник.
     */
    private const val ELLIPSE_RADIAL_VARIANCE: Float = 0.10f

    /** Минимальный bbox по узкой оси (в физических единицах) — отсекает «точку». */
    private const val MIN_PHYS_EXTENT: Float = 0.02f

    /**
     * Возвращает распознанный примитив или `null`, если штрих не соответствует
     * ни одному из поддерживаемых шаблонов.
     */
    public fun recognize(
        points: List<DrawingPoint>,
        aspect: Float,
    ): RecognizedShape? {
        if (points.size < MIN_POINTS) return null
        val safeAspect = if (aspect > 0f) aspect else 1f

        var xMin = Float.POSITIVE_INFINITY
        var xMax = Float.NEGATIVE_INFINITY
        var yMin = Float.POSITIVE_INFINITY
        var yMax = Float.NEGATIVE_INFINITY
        var pathLength = 0f
        var prevX = points[0].x
        var prevY = points[0].y
        for (i in points.indices) {
            val p = points[i]
            if (p.x < xMin) xMin = p.x
            if (p.x > xMax) xMax = p.x
            if (p.y < yMin) yMin = p.y
            if (p.y > yMax) yMax = p.y
            if (i > 0) {
                val dx = (p.x - prevX) * safeAspect
                val dy = p.y - prevY
                pathLength += sqrt(dx * dx + dy * dy)
            }
            prevX = p.x
            prevY = p.y
        }

        val first = points.first()
        val last = points.last()
        val cdx = (last.x - first.x) * safeAspect
        val cdy = last.y - first.y
        val chord = sqrt(cdx * cdx + cdy * cdy)
        if (pathLength <= 0f) return null

        val straightness = chord / pathLength
        if (straightness > LINE_STRAIGHTNESS) {
            return RecognizedShape.Line(first, last)
        }

        val physW = (xMax - xMin) * safeAspect
        val physH = yMax - yMin
        if (max(physW, physH) < MIN_PHYS_EXTENT) return null

        if (straightness < CLOSURE_RATIO) {
            val cx = (xMin + xMax) * 0.5f
            val cy = (yMin + yMax) * 0.5f
            val rx = (xMax - xMin) * 0.5f
            val ry = (yMax - yMin) * 0.5f
            if (rx <= 0f || ry <= 0f) return null

            val radialVar = ellipseRadialVariance(points, cx, cy, rx, ry)
            return if (radialVar < ELLIPSE_RADIAL_VARIANCE) {
                RecognizedShape.Ellipse(cx, cy, rx, ry)
            } else {
                RecognizedShape.Rectangle(xMin, yMin, xMax, yMax)
            }
        }
        return null
    }

    /**
     * Среднее относительное отклонение точки от единичной окружности после
     * нормировки на полуоси. Для идеального эллипса даёт 0; для квадрата —
     * заметно > 0.2 (углы дальше центра, чем стороны).
     */
    private fun ellipseRadialVariance(
        points: List<DrawingPoint>,
        cx: Float,
        cy: Float,
        rx: Float,
        ry: Float,
    ): Float {
        var sum = 0f
        for (p in points) {
            val ex = (p.x - cx) / rx
            val ey = (p.y - cy) / ry
            val r = sqrt(ex * ex + ey * ey)
            sum += abs(r - 1f)
        }
        return sum / points.size
    }
}
