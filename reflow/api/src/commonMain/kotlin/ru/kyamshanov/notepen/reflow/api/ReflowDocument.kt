package ru.kyamshanov.notepen.reflow.api

/**
 * Результат reflow-извлечения: содержимое PDF, развёрнутое в линейный поток
 * блоков в порядке чтения, независимый от исходной постраничной вёрстки.
 *
 * Типографику (шрифт, кегль, межстрочный интервал, ширину колонки) применяет
 * слой отображения — в модель она намеренно не зашивается.
 *
 * @property kind тип исходного содержимого (см. [PdfContentKind])
 * @property blocks блоки в порядке чтения
 */
public data class ReflowDocument(
    public val kind: PdfContentKind,
    public val blocks: List<ReflowBlock>,
)
