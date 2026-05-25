package ru.kyamshanov.notepen.book

import ru.kyamshanov.notepen.reflow.api.ReflowDocument

/**
 * Результат верстки [BookContent] в PDF: оглавление и reflow-документ, собранные
 * в одном проходе верстки.
 *
 * Рендерер — единственное место, где известны одновременно истинная структура
 * (из [BookContent]) и геометрия каждого фрагмента на странице PDF (он сам его
 * туда поставил). Поэтому он отдаёт готовый [reflow] со [ru.kyamshanov.notepen.reflow.api.SourceSpan]-ами:
 * это избавляет режим чтения от обратного выскребания текста из PDF и эвристик,
 * а штрихи editor'а сопоставляются с текстом по тем же нормализованным координатам.
 *
 * @property toc оглавление (заголовки → страница PDF)
 * @property reflow переформатируемый документ с привязкой фрагментов к страницам
 */
data class BookRenderResult(
    val toc: List<TocEntry>,
    val reflow: ReflowDocument,
)
