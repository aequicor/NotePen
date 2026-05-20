package ru.kyamshanov.notepen.rendering.api

/**
 * Закэшированная запись растеризованной страницы.
 *
 * @property bitmap растеризованный битмап страницы
 * @property renderedAtScalePercent процент масштаба, при котором был отрендерен
 */
public data class CachedPage(
    val bitmap: PageBitmap,
    val renderedAtScalePercent: Int,
)
