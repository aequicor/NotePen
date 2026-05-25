package ru.kyamshanov.notepen.reflow.ui

import kotlin.math.roundToInt

/**
 * Чистые расчёты индикатора прогресса чтения: процент пройденного и оценка
 * времени до конца. Без Compose — чтобы покрываться unit-тестами; формат и
 * раскладку выбирает слой отображения.
 */
internal object ReaderProgress {
    /**
     * Процент пройденного `0..100` по позиции первого видимого блока
     * [firstVisibleBlock] среди [totalBlocks]. Для пустого документа — `0`.
     */
    fun percent(
        firstVisibleBlock: Int,
        totalBlocks: Int,
    ): Int {
        if (totalBlocks <= 1) return if (totalBlocks <= 0) 0 else 100
        val ratio = firstVisibleBlock.toFloat() / (totalBlocks - 1)
        return (ratio.coerceIn(0f, 1f) * 100).roundToInt()
    }

    /**
     * Оценка минут до конца по числу непрочитанных символов [remainingChars]
     * и средней скорости чтения [charsPerMinute]. Округляется вверх до целой
     * минуты; неотрицательно.
     */
    fun minutesLeft(
        remainingChars: Int,
        charsPerMinute: Int = DEFAULT_CHARS_PER_MINUTE,
    ): Int {
        if (remainingChars <= 0 || charsPerMinute <= 0) return 0
        return ((remainingChars + charsPerMinute - 1) / charsPerMinute)
    }

    /** Средняя скорость чтения, символов в минуту (≈ 200–250 слов/мин). */
    const val DEFAULT_CHARS_PER_MINUTE: Int = 1100
}
