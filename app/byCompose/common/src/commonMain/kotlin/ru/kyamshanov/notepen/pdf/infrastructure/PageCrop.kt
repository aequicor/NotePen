package ru.kyamshanov.notepen.pdf.infrastructure

/**
 * `true`, если нормированная вырезка `(l, t, r, b)` покрывает всю страницу
 * (нет разделения разворота — FEATURE #4), и кроп можно пропустить. Вынесено,
 * чтобы платформенные рендереры не повторяли четырёхчленное условие
 * (detekt ComplexCondition).
 */
internal fun isFullCrop(
    l: Float,
    t: Float,
    r: Float,
    b: Float,
): Boolean = l <= 0f && t <= 0f && r >= 1f && b >= 1f
