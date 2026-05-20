package ru.kyamshanov.notepen.rendering.api

/**
 * Контрольные точки одного кубического сегмента Bézier-кривой.
 *
 * @property cp1x первая контрольная точка X
 * @property cp1y первая контрольная точка Y
 * @property cp2x вторая контрольная точка X
 * @property cp2y вторая контрольная точка Y
 */
public data class CubicControlPoints(
    val cp1x: Float,
    val cp1y: Float,
    val cp2x: Float,
    val cp2y: Float,
)

/**
 * Вычисляет контрольные точки кубической Bézier-кривой для сегмента p1→p2
 * по схеме Catmull-Rom (tension = 1/6).
 *
 * @param p0x,p0y предыдущая точка (или p1 на краю)
 * @param p1x,p1y начало сегмента
 * @param p2x,p2y конец сегмента
 * @param p3x,p3y следующая точка (или p2 на краю)
 */
public fun catmullRomControlPoints(
    p0x: Float,
    p0y: Float,
    p1x: Float,
    p1y: Float,
    p2x: Float,
    p2y: Float,
    p3x: Float,
    p3y: Float,
): CubicControlPoints = CubicControlPoints(
    cp1x = p1x + (p2x - p0x) / 6f,
    cp1y = p1y + (p2y - p0y) / 6f,
    cp2x = p2x - (p3x - p1x) / 6f,
    cp2y = p2y - (p3y - p1y) / 6f,
)
