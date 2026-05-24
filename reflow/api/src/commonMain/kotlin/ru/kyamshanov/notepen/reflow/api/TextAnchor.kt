package ru.kyamshanov.notepen.reflow.api

/**
 * Привязка аннотации к диапазону текста reflow-документа — результат
 * сопоставления рукописного штриха с переверстанным текстом.
 *
 * Указывает на блок [ReflowDocument.blocks] по индексу и диапазон символов
 * внутри его `.text`. Обратное преобразование к областям исходной страницы
 * выполняется через [SourceSpan] блока.
 *
 * @property blockIndex индекс блока в [ReflowDocument.blocks]
 * @property charStart индекс начала диапазона в `.text` блока (включительно)
 * @property charEnd индекс конца диапазона в `.text` блока (исключительно)
 */
public data class TextAnchor(
    public val blockIndex: Int,
    public val charStart: Int,
    public val charEnd: Int,
)
