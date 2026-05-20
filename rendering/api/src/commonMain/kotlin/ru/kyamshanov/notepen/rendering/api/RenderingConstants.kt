package ru.kyamshanov.notepen.rendering.api

/** Общие константы рендеринга, разделяемые между всеми контекстами отрисовки. */
public object RenderingConstants {

    /** Множитель расширения штриха при наклоне пера (tilt 0..1 → ширина ×(1..1+[TILT_WIDTH_GAIN])). */
    public const val TILT_WIDTH_GAIN: Float = 0.5f

    /** Минимальная толщина сегмента штриха в пикселях canvas (ниже — Skia рисует артефакты). */
    public const val MIN_RENDERED_STROKE_PX: Float = 1f

    /** Потолок каждого измерения off-screen ink-cache bitmap (px). */
    public const val INK_CACHE_MAX_DIMENSION_PX: Int = 3072

    /** Квантование ink-cache для стабильности при pinch-zoom (px). */
    public const val INK_CACHE_DIM_BUCKET_PX: Int = 256

    /** Потолок страницы, при котором low-latency overlay на Android ещё разрешён (px). */
    public const val LOW_LATENCY_OVERLAY_MAX_DIM_PX: Int = 2400

    /** Потолок high-res рендера для лупы (px). */
    public const val MAGNIFIER_HIGH_RES_DIM_PX: Int = 4000
}
