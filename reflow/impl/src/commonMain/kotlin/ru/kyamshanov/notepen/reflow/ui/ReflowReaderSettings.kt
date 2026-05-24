package ru.kyamshanov.notepen.reflow.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Настройки отображения reflow-ридера.
 *
 * Дефолты обоснованы исследованиями экранного чтения: ограниченная длина строки
 * (≈66 знаков), межстрочный интервал ~1.5 и тёплая «бумажная» палитра вместо
 * чисто-белого фона и чисто-чёрного текста.
 *
 * @property fontSize кегль основного текста (предполагается в `sp`)
 * @property lineHeightMultiplier межстрочный интервал как множитель кегля
 * @property maxContentWidth максимальная ширина текстовой колонки (≈66 знаков)
 * @property contentPadding отступы колонки от краёв
 * @property blockSpacing вертикальный зазор между блоками
 * @property background цвет фона (тёплая бумага)
 * @property textColor цвет текста (не чистый чёрный)
 * @property highlightColor цвет подсветки выделений (полупрозрачный)
 */
public data class ReflowReaderSettings(
    public val fontSize: TextUnit = 18.sp,
    public val lineHeightMultiplier: Float = 1.5f,
    public val maxContentWidth: Dp = 640.dp,
    public val contentPadding: Dp = 24.dp,
    public val blockSpacing: Dp = 14.dp,
    public val background: Color = Color(0xFFFAF6EF),
    public val textColor: Color = Color(0xFF2A2A2A),
    public val highlightColor: Color = Color(0x59FFD24D),
)
