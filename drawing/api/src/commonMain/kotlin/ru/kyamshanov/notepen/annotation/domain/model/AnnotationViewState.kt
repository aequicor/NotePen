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
 * Позиция reflow-режима хранится **в индексах блоков** (а не страницах ридера) —
 * номер страницы нестабилен при ре-пагинации (смена шрифта/полей/ориентации),
 * а индекс блока — это «валюта» TextAnchor в reflow-документе. Хранится как
 * плоские Int, чтобы не тащить `:reflow:api` в зависимости `:drawing:api`.
 *
 * @property scale масштаб в процентах.
 * @property currentPage индекс верхней видимой страницы.
 * @property currentPageOffset вертикальный сдвиг внутри [currentPage] в пикселях.
 * @property readingMode включён ли режим чтения (reflow) для документа.
 * @property reflowAnchorBlockIndex индекс блока, открывающего текущую страницу
 *   в reflow-режиме (используется как `TextAnchor.blockIndex`). По умолчанию 0
 *   — начало документа, что совпадает с поведением на свежем открытии.
 * @property reflowAnchorCharStart смещение внутри блока для Phase B precision
 *   (line→char через TextLayoutResult). В Phase A всегда 0; поле здесь, чтобы
 *   Phase B не требовала миграции диск-формата.
 */
data class AnnotationViewState(
    val scale: Int = 100,
    val currentPage: Int = 0,
    val currentPageOffset: Int = 0,
    val readingMode: Boolean = false,
    val reflowAnchorBlockIndex: Int = 0,
    val reflowAnchorCharStart: Int = 0,
)
