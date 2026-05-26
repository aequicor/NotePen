package ru.kyamshanov.notepen.reflow.ui

import androidx.compose.runtime.Composable

/** Десктоп: единого системного сигнала reduced motion нет — выбор стиля за пользователем. */
@Composable
internal actual fun isReducedMotionEnabled(): Boolean = false
