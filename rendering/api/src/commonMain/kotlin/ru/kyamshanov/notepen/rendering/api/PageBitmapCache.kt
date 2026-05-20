package ru.kyamshanov.notepen.rendering.api

/**
 * LRU-кэш растеризованных страниц PDF.
 *
 * Реализация управляет эвикцией по числу записей и суммарному объёму в пикселях.
 */
public interface PageBitmapCache {

    /**
     * Возвращает кэшированную страницу или `null`, если не найдена.
     *
     * Обращение обновляет LRU-порядок записи.
     */
    public fun get(pageIndex: Int): CachedPage?

    /**
     * Кладёт страницу в кэш. Если запись уже существует — перезаписывает.
     *
     * При превышении лимитов вытесняет наименее используемые записи.
     */
    public fun put(pageIndex: Int, page: CachedPage)

    /**
     * Проактивно вытесняет off-screen записи с устаревшим масштабом.
     *
     * Удаляет записи, у которых `renderedAtScalePercent × maxScaleRatio < currentScale`,
     * кроме видимых ([visibleIndices]).
     *
     * @param visibleIndices индексы видимых страниц (защищены от эвикции)
     * @param currentScale текущий масштаб вьюера в процентах
     * @param maxScaleRatio допустимое отклонение масштаба (например, 2.0)
     */
    public fun evictStaleScale(
        visibleIndices: Set<Int>,
        currentScale: Int,
        maxScaleRatio: Float,
    )

    /** Очищает весь кэш. */
    public fun clear()
}
