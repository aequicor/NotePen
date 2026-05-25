package ru.kyamshanov.notepen.rendering.api

import ru.kyamshanov.notepen.pdf.domain.model.PdfDocument

/**
 * Абстракция над растеризацией PDF-страниц с выбором оптимального разрешения.
 *
 * Реализации обязаны быть main-safe.
 */
public interface PageRasterizer {
    /**
     * Растеризует страницу под текущий zoom вьюера.
     *
     * Итоговое разрешение = `baseWidthPx × scalePercent/100 × densityScale`,
     * ограниченное [maxDimPx] по каждой стороне с сохранением aspect ratio.
     *
     * @param document открытый PDF-документ
     * @param pageIndex нулевой индекс страницы
     * @param baseWidthPx базовая ширина страницы в пикселях (при zoom = 100%)
     * @param scalePercent текущий zoom вьюера (100 = 1:1)
     * @param densityScale коэффициент плотности экрана (`Density.density`)
     * @param maxDimPx потолок по каждой стороне (JVM = 4000, Android = 2400)
     * @return растеризованная страница
     */
    public suspend fun renderForViewer(
        document: PdfDocument,
        pageIndex: Int,
        baseWidthPx: Int,
        scalePercent: Int,
        densityScale: Float,
        maxDimPx: Int,
    ): PageBitmap

    /**
     * Растеризует страницу при максимальном разрешении для лупы.
     *
     * Обе стороны ограничены [maxDimPx] с сохранением aspect ratio.
     *
     * @param document открытый PDF-документ
     * @param pageIndex нулевой индекс страницы
     * @param maxDimPx потолок по каждой стороне
     * @return растеризованная страница в максимальном разрешении
     */
    public suspend fun renderHighRes(
        document: PdfDocument,
        pageIndex: Int,
        maxDimPx: Int,
    ): PageBitmap
}
