package ru.kyamshanov.notepen.annotation.domain

import ru.kyamshanov.notepen.annotation.domain.model.DrawingPoint
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Прореживает выборку точек штриха алгоритмом Рамера—Дугласа—Пекера (RDP).
 *
 * Перо сэмплируется на 60–120 Гц, поэтому «сырой» штрих содержит сотни почти
 * коллинеарных точек. Они раздувают и файл аннотаций (вплоть до OOM при
 * сериализации), и стоимость растеризации. RDP убирает точки, чьё отклонение
 * незначимо, сохраняя визуальную форму.
 *
 * Толщина штриха модулируется по `pressure`/`tilt`, поэтому чисто
 * геометрический RDP терял профиль нажатия (резкие переходы толщины и «худеющий»
 * конец на отрыве пера). Поэтому точка удерживается, если значимо отклонение
 * **либо** по геометрии (хорда, [DEFAULT_EPSILON]), **либо** по pressure/tilt
 * от линейно интерполированного значения ([DEFAULT_ATTR_EPSILON]).
 *
 * Координаты считаются нормализованными в `[0..1]` (canvas space), как и
 * pressure/tilt, поэтому пороги заданы в тех же единицах.
 */
public object StrokeSimplifier {

    /**
     * Геометрический порог по умолчанию в нормализованных координатах `[0..1]`.
     *
     * Задан на субпиксельном уровне (≈0.6 px на странице шириной ~2000 px),
     * чтобы прореживание было визуально без потерь: при бо́льших значениях
     * мелкие детали рукописного текста (петли букв) срезались, и непрерывное
     * письмо становилось нечитабельным. Бо́льшая часть выигрыша всё равно
     * приходит от удаления плотных near-duplicate сэмплов пера и от
     * субпиксельной агрегации, а не от агрессивного сглаживания формы.
     */
    public const val DEFAULT_EPSILON: Float = 0.0003f

    /**
     * Порог отклонения pressure/tilt по умолчанию (оба в `[0..1]`). Точка с
     * отклонением нажатия/наклона больше этого значения от интерполированного
     * сохраняется, чтобы переходы толщины оставались плавными. Уменьшить, если
     * переходы толщины выглядят ступенчатыми.
     */
    public const val DEFAULT_ATTR_EPSILON: Float = 0.04f

    /**
     * Возвращает прореженную копию [points]. Первая и последняя точки всегда
     * сохраняются (вместе с их `isNewPath`/`pressure`/`tilt`). Списки из 2 и
     * менее точек возвращаются без изменений.
     *
     * @param epsilon геометрический порог отклонения от хорды.
     * @param attrEpsilon порог отклонения pressure/tilt от интерполированного значения.
     */
    public fun simplify(
        points: List<DrawingPoint>,
        epsilon: Float = DEFAULT_EPSILON,
        attrEpsilon: Float = DEFAULT_ATTR_EPSILON,
    ): List<DrawingPoint> {
        if (points.size <= 2) return points
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.size - 1] = true

        fun rdp(startIndex: Int, endIndex: Int) {
            if (endIndex <= startIndex + 1) return
            val start = points[startIndex]
            val end = points[endIndex]
            val span = (endIndex - startIndex).toFloat()
            var maxScore = 0f
            var maxIndex = -1
            for (i in (startIndex + 1) until endIndex) {
                val point = points[i]
                // Нормируем оба отклонения на их пороги и берём максимум, чтобы
                // одной проверкой ловить значимость и по форме, и по нажатию.
                val geomScore = perpendicularDistance(point, start, end) / epsilon
                val t = (i - startIndex) / span
                val attrScore = attrDeviation(point, start, end, t) / attrEpsilon
                val score = maxOf(geomScore, attrScore)
                if (score > maxScore) {
                    maxScore = score
                    maxIndex = i
                }
            }
            if (maxScore > 1f && maxIndex != -1) {
                keep[maxIndex] = true
                rdp(startIndex, maxIndex)
                rdp(maxIndex, endIndex)
            }
        }

        rdp(0, points.size - 1)
        return points.filterIndexed { index, _ -> keep[index] }
    }

    private fun attrDeviation(point: DrawingPoint, start: DrawingPoint, end: DrawingPoint, t: Float): Float {
        val pressureDev = abs(point.pressure - (start.pressure + (end.pressure - start.pressure) * t))
        val tiltDev = abs(point.tilt - (start.tilt + (end.tilt - start.tilt) * t))
        return maxOf(pressureDev, tiltDev)
    }

    private fun perpendicularDistance(point: DrawingPoint, lineStart: DrawingPoint, lineEnd: DrawingPoint): Float {
        val dx = lineEnd.x - lineStart.x
        val dy = lineEnd.y - lineStart.y
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared == 0f) {
            val ddx = point.x - lineStart.x
            val ddy = point.y - lineStart.y
            return sqrt(ddx * ddx + ddy * ddy)
        }
        val area = (dy * point.x - dx * point.y + lineEnd.x * lineStart.y - lineEnd.y * lineStart.x)
        return abs(area) / sqrt(lengthSquared)
    }
}
