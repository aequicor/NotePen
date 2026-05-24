package ru.kyamshanov.notepen.reflow.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Настройки отображения reflow-ридера.
 *
 * Дефолты обоснованы исследованиями экранного чтения: щедрый кегль и межстрочный
 * (лучше «слишком крупно», чем мелко — устаёт цилиарная мышца), ограниченная длина
 * строки (≈66 знаков) и тёплая «бумажная» палитра вместо чисто-белого и чёрного.
 *
 * @property fontSize кегль основного текста (предполагается в `sp`)
 * @property lineHeightMultiplier межстрочный интервал как множитель кегля
 * @property maxContentWidth максимальная ширина текстовой колонки (≈66 знаков)
 * @property contentPadding отступы колонки от краёв
 * @property blockSpacing вертикальный зазор между блоками
 * @property background цвет фона (тёплая бумага)
 * @property textColor цвет текста (не чистый чёрный)
 * @property highlightColor цвет подсветки выделений (полупрозрачный)
 * @property codeBackground фон inline-кода (моноширинных фрагментов), полупрозрачный
 * @property justify выключка абзацев по ширине колонки (ровный правый край);
 *   по умолчанию выключено — не всем такое нравится, переключается в панели ридера
 * @property ergonomicsEnabled включить заботу о глазах по времени сессии
 *   (напоминание 20-20-20, потепление/затемнение на долгой сессии, ритм-паузы)
 */
public data class ReflowReaderSettings(
    public val fontSize: TextUnit = 19.sp,
    public val lineHeightMultiplier: Float = 1.6f,
    public val maxContentWidth: Dp = 640.dp,
    public val contentPadding: Dp = 24.dp,
    public val blockSpacing: Dp = 14.dp,
    public val background: Color = Color(0xFFFAF6EF),
    public val textColor: Color = Color(0xFF2A2A2A),
    public val highlightColor: Color = Color(0x59FFD24D),
    public val codeBackground: Color = Color(0x14000000),
    public val justify: Boolean = false,
    public val ergonomicsEnabled: Boolean = true,
)
