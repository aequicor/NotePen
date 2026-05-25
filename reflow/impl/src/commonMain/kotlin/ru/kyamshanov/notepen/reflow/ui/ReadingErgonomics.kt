package ru.kyamshanov.notepen.reflow.ui

/**
 * Чистая логика эргономики чтения по времени сессии: адаптивное
 * потепление/затемнение на долгой сессии и точки визуального «ритма».
 */
internal object ReadingErgonomics {
    /**
     * Доля тёплого затемнения для долгой сессии: `0` до [afterMs], затем линейно
     * нарастает до [maxDim] за [rampMs] и держится.
     */
    fun dimAlpha(
        elapsedMs: Long,
        afterMs: Long,
        rampMs: Long,
        maxDim: Float,
    ): Float {
        if (maxDim <= 0f || elapsedMs <= afterMs) return 0f
        if (rampMs <= 0L) return maxDim
        val progress = ((elapsedMs - afterMs).toFloat() / rampMs).coerceIn(0f, 1f)
        return progress * maxDim
    }

    /** Точка визуальной паузы-«вдоха»: после каждых [every] блоков. */
    fun isRhythmBreak(
        blockIndex: Int,
        every: Int,
    ): Boolean = every > 0 && (blockIndex + 1) % every == 0
}
