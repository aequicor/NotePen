package ru.kyamshanov.notepen.rendering.api

/**
 * Чистые функции расчёта оптимального разрешения растеризации
 * для трёх контекстов: вьюер, лупа (high-res) и ink-cache.
 */
public object ResolutionCalculator {
    /**
     * Разрешение для основного вьюера.
     *
     * `desired = baseWidthPx × scalePercent/100 × densityScale`,
     * обе стороны capped by [maxDimPx] с сохранением [aspectRatio].
     *
     * @param baseWidthPx базовая ширина страницы (при zoom 100%)
     * @param scalePercent текущий масштаб вьюера
     * @param densityScale плотность экрана (`Density.density`)
     * @param maxDimPx потолок пикселей по каждой стороне
     * @param aspectRatio ширина/высота страницы
     */
    public fun computeViewerResolution(
        baseWidthPx: Int,
        scalePercent: Int,
        densityScale: Float,
        maxDimPx: Int,
        aspectRatio: Float,
    ): RenderResolution {
        val desiredWidth =
            (baseWidthPx * scalePercent / 100f * densityScale).toInt()
                .coerceAtLeast(1)
        val widthCapped = desiredWidth.coerceAtMost(maxDimPx)
        val heightFromWidth = (widthCapped / aspectRatio).toInt().coerceAtLeast(1)
        return if (heightFromWidth > maxDimPx) {
            val cappedH = maxDimPx
            val cappedW = (cappedH * aspectRatio).toInt().coerceAtLeast(1)
            RenderResolution(cappedW, cappedH)
        } else {
            RenderResolution(widthCapped, heightFromWidth)
        }
    }

    /**
     * Разрешение для high-res лупы.
     *
     * [maxDimPx] по длинной стороне, вторая пропорционально [aspectRatio].
     *
     * @param aspectRatio ширина/высота страницы
     * @param maxDimPx потолок по каждой стороне
     */
    public fun computeHighResResolution(
        aspectRatio: Float,
        maxDimPx: Int,
    ): RenderResolution {
        val widthCapped = maxDimPx
        val heightFromWidth = (widthCapped / aspectRatio).toInt().coerceAtLeast(1)
        return if (heightFromWidth > maxDimPx) {
            val cappedH = maxDimPx
            val cappedW = (cappedH * aspectRatio).toInt().coerceAtLeast(1)
            RenderResolution(cappedW, cappedH)
        } else {
            RenderResolution(widthCapped, heightFromWidth)
        }
    }

    /**
     * Разрешение ink-cache (off-screen bitmap для завершённых штрихов).
     *
     * При `max(canvasWidth, canvasHeight) > maxDimPx` — пропорциональное
     * уменьшение. Иначе — квантование по [bucketSizePx] (256 px), чтобы
     * pinch-zoom не инвалидировал кэш на каждый пиксель.
     *
     * @param canvasWidth текущая ширина canvas в пикселях
     * @param canvasHeight текущая высота canvas в пикселях
     * @param maxDimPx потолок ([RenderingConstants.INK_CACHE_MAX_DIMENSION_PX])
     * @param bucketSizePx размер бакета ([RenderingConstants.INK_CACHE_DIM_BUCKET_PX])
     * @return квантованное/ограниченное разрешение; [RenderResolution] с нулями если входные <= 0
     */
    public fun computeInkCacheResolution(
        canvasWidth: Int,
        canvasHeight: Int,
        maxDimPx: Int,
        bucketSizePx: Int,
    ): RenderResolution {
        if (canvasWidth <= 0 || canvasHeight <= 0) {
            return RenderResolution(0, 0)
        }
        val longest = maxOf(canvasWidth, canvasHeight)
        return if (longest > maxDimPx) {
            RenderResolution(
                widthPx = (canvasWidth.toLong() * maxDimPx / longest).toInt().coerceAtLeast(1),
                heightPx = (canvasHeight.toLong() * maxDimPx / longest).toInt().coerceAtLeast(1),
            )
        } else {
            val b = bucketSizePx.coerceAtLeast(1)
            RenderResolution(
                widthPx = ((canvasWidth + b - 1) / b * b).coerceAtLeast(b),
                heightPx = ((canvasHeight + b - 1) / b * b).coerceAtLeast(b),
            )
        }
    }
}
