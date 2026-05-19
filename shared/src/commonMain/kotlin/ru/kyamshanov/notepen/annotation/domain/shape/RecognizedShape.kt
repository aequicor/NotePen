package ru.kyamshanov.notepen.annotation.domain.shape

import ru.kyamshanov.notepen.annotation.domain.model.DrawingPoint

/**
 * Геометрический примитив, в который [ShapeRecognizer] может «снапнуть»
 * свободный штрих. Все координаты — нормализованные `[0..1]` относительно
 * PDF-страницы, как у [DrawingPoint].
 */
public sealed interface RecognizedShape {
    /** Прямой отрезок. */
    public data class Line(val start: DrawingPoint, val end: DrawingPoint) : RecognizedShape

    /** Эллипс с центром `(cx, cy)` и полуосями `rx, ry` (все в нормализованных координатах). */
    public data class Ellipse(
        val cx: Float,
        val cy: Float,
        val rx: Float,
        val ry: Float,
    ) : RecognizedShape

    /** Осё-выровненный прямоугольник по bbox. */
    public data class Rectangle(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    ) : RecognizedShape
}
