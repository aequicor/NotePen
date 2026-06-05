package ru.kyamshanov.notepen.rendering.api

import kotlin.math.pow

/**
 * Вычисляет ширину сегмента штриха с учётом давления и наклона пера.
 *
 * Давление проходит через gamma-кривую с минимальным множителем, чтобы первые
 * сэмплы пера с малым pressure не превращались в почти невидимый штрих.
 *
 * @param baseWidthPx базовая ширина штриха в пикселях (= normalizedWidth × pageWidthPx)
 * @param pressure давление стилуса `[0..1]`
 * @param tilt наклон стилуса `[0..1]` (0 = перпендикулярно, 1 = параллельно)
 * @param tiltWidthGain множитель влияния наклона (по умолчанию [RenderingConstants.TILT_WIDTH_GAIN])
 * @return ширина сегмента в пикселях, >= [RenderingConstants.MIN_RENDERED_STROKE_PX]
 */
public fun computeSegmentWidth(
    baseWidthPx: Float,
    pressure: Float,
    tilt: Float,
    tiltWidthGain: Float = RenderingConstants.TILT_WIDTH_GAIN,
): Float =
    (baseWidthPx * pressureWidthFactor(pressure) * (1f + tiltWidthGain * tilt))
        .coerceAtLeast(RenderingConstants.MIN_RENDERED_STROKE_PX)

private fun pressureWidthFactor(pressure: Float): Float {
    val curved = pressure.coerceIn(0f, 1f).pow(RenderingConstants.PRESSURE_WIDTH_GAMMA)
    return RenderingConstants.MIN_PRESSURE_WIDTH_FACTOR +
        (1f - RenderingConstants.MIN_PRESSURE_WIDTH_FACTOR) * curved
}
