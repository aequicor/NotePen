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
     */
    public data class Heading(
        public val text: String,
        public val level: Int,
    ) : ReflowBlock

    /**
     * Абзац основного текста.
     *
     * Мягкие переносы строк внутри абзаца сняты, перенос по дефису на конце
     * строки склеен, слова разделены одиночными пробелами — текст готов к
     * повторной верстке под произвольную ширину.
     *
     * @property text текст абзаца
     */
    public data class Paragraph(
        public val text: String,
    ) : ReflowBlock

    /**
     * Нетекстовая область (картинка, таблица, формула), которую нельзя
     * осмысленно переформатировать в текст. Рендерится как кроп-изображение
     * соответствующей области исходной страницы внутри потока.
     *
     * @property pageIndex нулевой индекс исходной страницы
     * @property bounds область на странице в координатах PDF (пункты)
     */
    public data class Figure(
        public val pageIndex: Int,
        public val bounds: ReflowRect,
    ) : ReflowBlock
}
