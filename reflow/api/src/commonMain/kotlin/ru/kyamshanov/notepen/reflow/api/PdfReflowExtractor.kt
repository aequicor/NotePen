package ru.kyamshanov.notepen.reflow.api

/**
 * Порт извлечения переформатируемого (reflow) содержимого из PDF.
 *
 * Реализации обязаны быть main-safe: блокирующее IO/CPU выполняется через
 * инжектируемый [kotlinx.coroutines.CoroutineDispatcher], а не через
 * `Dispatchers.*` напрямую.
 */
public interface PdfReflowExtractor {
    /**
     * Быстро классифицирует тип содержимого, не выполняя полного извлечения
     * и группировки. Предназначен для решения, предлагать ли пользователю
     * режим reflow.
     *
     * @param path абсолютный платформенный путь к файлу или URI
     * @return тип содержимого
     * @throws IllegalArgumentException если файл не найден или не является валидным PDF
     */
    public suspend fun probe(path: String): PdfContentKind

    /**
     * Извлекает [ReflowDocument]: текст, сгруппированный в заголовки и абзацы
     * в порядке чтения, и нетекстовые области как [ReflowBlock.Figure].
     *
     * Для [PdfContentKind.IMAGE_ONLY] вернёт документ без текстовых блоков —
     * вызывающая сторона должна предусмотреть OCR.
     *
     * @param path абсолютный платформенный путь к файлу или URI
     * @return извлечённый reflow-документ
     * @throws IllegalArgumentException если файл не найден или не является валидным PDF
     */
    public suspend fun extract(path: String): ReflowDocument

    /**
     * Как [extract], плюс Lattice-уточнение таблиц: для каждой low-confidence
     * Stream-таблицы (Figure'ов c `wasTableFallback = true`) рендерит
     * соответствующую страницу через [pageBitmaps] и пытается восстановить
     * таблицу по нарисованной грид-сетке (см. `LatticeTableRefiner`).
     *
     * Колбэк дёргается лениво — только для страниц с кандидатами; если на
     * документе вообще нет low-conf Stream-таблиц, рендер не происходит ни
     * разу. Если колбэк возвращает `null` или рефайнер не нашёл грид-сетку,
     * Figure-крoп сохраняется.
     *
     * Дефолтная реализация эквивалентна [extract] — для тех extractor'ов,
     * которые не умеют (или не хотят) Lattice-уточнения.
     *
     * @param path абсолютный платформенный путь к файлу или URI
     * @param pageBitmaps колбэк растеризации страницы под Lattice
     * @return документ с уточнёнными таблицами
     */
    public suspend fun extractWithLattice(
        path: String,
        pageBitmaps: PageBitmapProvider,
    ): ReflowDocument = extract(path)
}
