package ru.kyamshanov.notepen.reflow.api

/**
 * Один блок переформатированного (reflow) документа.
 *
 * Блоки следуют в порядке чтения (см. [ReflowDocument.blocks]). Иерархия
 * закрытая (`sealed`) — расширение типами блоков происходит через добавление
 * новых наследников, а не через флаги.
 */
public sealed interface ReflowBlock {

    /**
     * Заголовок раздела: текст с укрупнённым относительно основного кеглем.
     *
     * @property text текст заголовка
     * @property level уровень вложенности от 1 (самый крупный); глубже —
     *   больше число
     * @property source фрагменты исходного текста с привязкой к странице
     *   (см. [SourceSpan]); [SourceSpan.charStart]/[SourceSpan.charEnd]
     *   индексируют [text]
     */
    public data class Heading(
        public val text: String,
        public val level: Int,
        public val source: List<SourceSpan> = emptyList(),
    ) : ReflowBlock

    /**
     * Абзац основного текста.
     *
     * Мягкие переносы строк внутри абзаца сняты, перенос по дефису на конце
     * строки склеен, слова разделены одиночными пробелами — текст готов к
     * повторной верстке под произвольную ширину.
     *
     * @property text текст абзаца
     * @property source фрагменты исходного текста с привязкой к странице
     *   (см. [SourceSpan]); [SourceSpan.charStart]/[SourceSpan.charEnd]
     *   индексируют [text]
     */
    public data class Paragraph(
        public val text: String,
        public val source: List<SourceSpan> = emptyList(),
    ) : ReflowBlock

    /**
     * Нетекстовая область (картинка, таблица, формула), которую нельзя
     * осмысленно переформатировать в текст. Рендерится как кроп-изображение
     * соответствующей области исходной страницы внутри потока.
     *
     * @property pageIndex нулевой индекс исходной страницы
     * @property bounds область на странице в нормализованных координатах
     *   `[0..1]` (см. [ReflowRect])
     */
    public data class Figure(
        public val pageIndex: Int,
        public val bounds: ReflowRect,
    ) : ReflowBlock
}
