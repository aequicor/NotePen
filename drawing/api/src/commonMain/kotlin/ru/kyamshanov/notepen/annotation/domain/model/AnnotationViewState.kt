package ru.kyamshanov.notepen.annotation.domain.model

/**
 * Лёгкое состояние вида документа (масштаб и позиция прокрутки), хранимое
 * отдельно от тяжёлого набора штрихов.
 *
 * Выделено в самостоятельную сущность, чтобы при открытии документа можно было
 * восстановить зум/страницу ДО парсинга всех штрихов — иначе страница сначала
 * рисовалась на дефолтном масштабе и резко «доворачивалась» к сохранённому
 * через 1–2 с (после загрузки всего файла аннотаций).
 *
 * @property scale масштаб в процентах.
 * @property currentPage индекс верхней видимой страницы.
 * @property currentPageOffset вертикальный сдвиг внутри [currentPage] в пикселях.
 * @property readingMode включён ли режим чтения (reflow) для документа.
 */
data class AnnotationViewState(
    val scale: Int = 100,
    val currentPage: Int = 0,
    val currentPageOffset: Int = 0,
    val readingMode: Boolean = false,
)
