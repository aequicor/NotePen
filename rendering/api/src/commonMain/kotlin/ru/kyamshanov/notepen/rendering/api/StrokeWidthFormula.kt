package ru.kyamshanov.notepen.rendering.api

/**
 * Вычисляет ширину сегмента штриха с учётом давления и наклона пера.
 *
 * Формула: `baseWidthPx × pressure × (1 + tiltWidthGain × tilt)`,
 * результат не менее [RenderingConstants.MIN_RENDERED_STROKE_PX].
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
): Float = (baseWidthPx * pressure * (1f + tiltWidthGain * tilt))
    .coerceAtLeast(RenderingConstants.MIN_RENDERED_STROKE_PX)
