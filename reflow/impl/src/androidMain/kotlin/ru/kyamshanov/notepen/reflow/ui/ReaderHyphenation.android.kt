package ru.kyamshanov.notepen.reflow.ui

/**
 * Android: системный переносчик (AOSP, с реальными словарями) работает через `Hyphens.Auto` — ручной
 * перенос не нужен, мягкие переносы не вставляем (карта остаётся тождественной).
 */
internal actual fun platformSoftHyphenPositions(text: String): IntArray = EMPTY_POSITIONS

private val EMPTY_POSITIONS = IntArray(0)
