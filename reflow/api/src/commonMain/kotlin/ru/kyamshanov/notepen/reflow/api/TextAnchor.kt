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
) {
    public companion object {
        /** Якорь «начало документа»: первый блок, нулевой диапазон. */
        public val START: TextAnchor = TextAnchor(blockIndex = 0, charStart = 0, charEnd = 0)

        /**
         * Якорь на начало блока [blockIndex] без точечного диапазона.
         * Для позиции чтения, когда charOffset ещё не считается (Phase A).
         */
        public fun ofBlock(blockIndex: Int): TextAnchor =
            TextAnchor(blockIndex = blockIndex, charStart = 0, charEnd = 0)
    }
}
