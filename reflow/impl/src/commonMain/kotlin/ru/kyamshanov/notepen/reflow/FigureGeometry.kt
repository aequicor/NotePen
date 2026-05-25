package ru.kyamshanov.notepen.reflow

import ru.kyamshanov.notepen.reflow.api.ReflowRect

/**
 * Геометрия врезок-изображений: перевод матрицы размещения (CTM) в
 * прямоугольник страницы и отсев полностраничных изображений (фон скана).
 */
internal object FigureGeometry {
    /** Доля страницы, начиная с которой изображение считается фоном, а не врезкой. */
    private const val FULL_PAGE_FRACTION = 0.9f

    /** Минимальная доля площади страницы, чтобы изображение считалось значимой врезкой (а не декором/иконкой). */
    private const val MIN_AREA_FRACTION = 0.005f

    /**
     * Прямоугольник изображения в координатах страницы (origin сверху-слева,
     * пункты) по матрице CTM, заданной шестью значениями. CTM отображает
     * единичный квадрат `[0..1]²` в пользовательское пространство PDF (origin
     * внизу-слева, Y вверх); результат переводится в систему top-left (Y вниз),
     * совпадающую с координатами глифов.
     */
    fun imageRectFromCtm(
        scaleX: Float,
        shearY: Float,
        shearX: Float,
        scaleY: Float,
        translateX: Float,
        translateY: Float,
        pageHeightPt: Float,
    ): ReflowRect {
        val x0 = translateX
        val x1 = scaleX + translateX
        val x2 = scaleX + shearX + translateX
        val x3 = shearX + translateX
        val y0 = translateY
        val y1 = shearY + translateY
        val y2 = shearY + scaleY + translateY
        val y3 = scaleY + translateY
        val minX = minOf(x0, x1, x2, x3)
        val maxX = maxOf(x0, x1, x2, x3)
        val minY = minOf(y0, y1, y2, y3)
        val maxY = maxOf(y0, y1, y2, y3)
        return ReflowRect(left = minX, top = pageHeightPt - maxY, right = maxX, bottom = pageHeightPt - minY)
    }

    /** Покрывает ли область почти всю страницу — тогда это фон скана, а не врезка-картинка. */
    fun isFullPage(
        rect: ReflowRect,
        pageWidthPt: Float,
        pageHeightPt: Float,
    ): Boolean = rect.width >= pageWidthPt * FULL_PAGE_FRACTION && rect.height >= pageHeightPt * FULL_PAGE_FRACTION

    /** Слишком ли мала область, чтобы быть значимой врезкой (мелкий декор/иконка). */
    fun isTooSmall(
        rect: ReflowRect,
        pageWidthPt: Float,
        pageHeightPt: Float,
    ): Boolean {
        val pageArea = pageWidthPt * pageHeightPt
        return pageArea <= 0f || rect.width * rect.height < pageArea * MIN_AREA_FRACTION
    }
}
