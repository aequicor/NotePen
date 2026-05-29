package ru.kyamshanov.notepen.annotation.domain.model

/**
 * Поворот страницы в четвертях оборота (90°), задаваемый пользователем поверх
 * собственного поворота страницы из PDF-словаря (`PdfPageInfo.rotation`).
 *
 * Хранится как `Int` в `[0, 3]`, где значение `q` соответствует повороту на
 * `q * 90°` по часовой стрелке. Кумулятивный поворот по кнопке ↻ — это
 * `(q + 1) mod 4`.
 *
 * Все функции — чистые и детерминированные (без Compose/SDK), тестируются
 * отдельно от UI.
 */
public object PageRotation {
    /** Число четвертей в полном обороте. */
    public const val QUARTERS: Int = 4

    /**
     * Нормализует произвольное число четвертей в диапазон `[0, 3]`
     * (корректно для отрицательных значений).
     */
    public fun normalizeQuarters(quarters: Int): Int = ((quarters % QUARTERS) + QUARTERS) % QUARTERS

    /**
     * Следующее значение пользовательского поворота при тапе по кнопке ↻:
     * `0 → 1 → 2 → 3 → 0`.
     */
    public fun nextQuarter(current: Int): Int = normalizeQuarters(current + 1)

    /**
     * Эффективный поворот в градусах = (собственный поворот PDF + пользовательский)
     * по модулю 360. [nativeRotationDegrees] — `PdfPageInfo.rotation` (0/90/180/270);
     * [userQuarters] — пользовательские четверти.
     */
    public fun effectiveDegrees(
        nativeRotationDegrees: Int,
        userQuarters: Int,
    ): Int = (normalizeQuarters(nativeRotationDegrees / 90 + userQuarters)) * 90

    /**
     * Переводит градусы (любые кратные 90°) в нормализованные четверти `[0, 3]`.
     */
    public fun degreesToQuarters(degrees: Int): Int = normalizeQuarters(degrees / 90)

    /**
     * Поворачивает нормализованную точку страницы `(x, y)` на **+90° по часовой
     * стрелке** в системе координат с осью Y вниз (как на экране).
     *
     * Формула: `(x, y) → (1 - y, x)`.
     *
     * Вывод (угол отсчитывается так, что верхний край страницы становится
     * правым): старый верх-лево `(0,0)` уходит в новый верх-право `(1,0)`,
     * старый верх-право `(1,0)` — в низ-право `(1,1)` и т.д. Это в точности
     * совпадает с поворотом растра на 90° CW (PDFBox `AffineTransformOp` /
     * Android `Matrix.postRotate(90f)`), поэтому штрих остаётся под тем же
     * содержимым.
     */
    public fun rotatePointCw(point: DrawingPoint): DrawingPoint = point.copy(x = 1f - point.y, y = point.x)

    /**
     * Поворачивает [point] на [quarters] четвертей по часовой стрелке
     * (повторное применение [rotatePointCw]).
     */
    public fun rotatePoint(
        point: DrawingPoint,
        quarters: Int,
    ): DrawingPoint {
        var p = point
        repeat(normalizeQuarters(quarters)) { p = rotatePointCw(p) }
        return p
    }

    /**
     * Поворачивает [extent] на +90° по часовой стрелке. Прямоугольник в
     * нормализованных координатах страницы (Y вниз): углы преобразуются как
     * точки, затем берутся новые min/max.
     */
    public fun rotateExtentCw(extent: PageExtent): PageExtent {
        // Преобразуем два противоположных угла: (left, top) и (right, bottom).
        val ax = 1f - extent.top
        val ay = extent.left
        val bx = 1f - extent.bottom
        val by = extent.right
        return PageExtent(
            left = minOf(ax, bx),
            top = minOf(ay, by),
            right = maxOf(ax, bx),
            bottom = maxOf(ay, by),
        )
    }

    /**
     * Поворачивает прямоугольник [rect] на +90° по часовой стрелке (углы как
     * точки, затем новые min/max). Используется для поворота областей липкого
     * маркера вместе со страницей.
     */
    public fun rotateRectCw(rect: NormalizedRect): NormalizedRect {
        val ax = 1f - rect.top
        val ay = rect.left
        val bx = 1f - rect.bottom
        val by = rect.right
        return NormalizedRect(
            left = minOf(ax, bx),
            top = minOf(ay, by),
            right = maxOf(ax, bx),
            bottom = maxOf(ay, by),
        )
    }

    /** Поворачивает выделение [highlight] на +90° по часовой стрелке. */
    public fun rotateHighlightCw(highlight: StickyHighlight): StickyHighlight = highlight.copy(rects = highlight.rects.map(::rotateRectCw))

    /**
     * Поворачивает штрих [path] на +90° по часовой стрелке, перенося все точки и
     * корректируя [DrawingPath.strokeWidth].
     *
     * [pageAspect] — соотношение сторон страницы (ширина / высота) **до**
     * поворота. Толщина штриха нормирована к ширине страницы; при повороте на
     * 90° ширина и высота меняются местами, поэтому абсолютная толщина
     * сохраняется умножением нормированной толщины на [pageAspect]
     * (`w_new = w_old * W_old / H_old`). Для поворота на 0/180° толщина не
     * меняется — там стороны не меняются местами.
     */
    public fun rotatePathCw(
        path: DrawingPath,
        pageAspect: Float,
    ): DrawingPath {
        val safeAspect = if (pageAspect > 0f) pageAspect else 1f
        return path.copy(
            points = path.points.map(::rotatePointCw),
            strokeWidth = path.strokeWidth * safeAspect,
        )
    }
}
