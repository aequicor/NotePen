package ru.kyamshanov.notepen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.kyamshanov.notepen.blur.GlassSurface
import ru.kyamshanov.notepen.titlebar.LocalTitleBarEndInset
import ru.kyamshanov.notepen.titlebar.LocalTitleBarInteraction
import ru.kyamshanov.notepen.titlebar.LocalTitleBarStartInset

/** Visible height of [LiquidGlassTopBar] above the status-bar inset. */
val LIQUID_GLASS_TOP_BAR_HEIGHT: Dp = 56.dp

/**
 * Edge-to-edge frosted top bar that floats over the screen content. The content
 * beneath the bar (the list / grid that fills the screen) is sampled through
 * [GlassSurface] via the surrounding `GlassBackdropProvider`, giving the bar
 * the "liquid glass" refraction. Mirrors the `TopAppBar` slot API so screens can
 * keep their action layouts unchanged.
 *
 * Place inside a `Box` at `Alignment.TopCenter`; the caller is responsible for
 * padding the content below it (typically by adding [LIQUID_GLASS_TOP_BAR_HEIGHT]
 * plus the status-bar inset to the list's top contentPadding).
 *
 * Honors [LocalTitleBarInteraction] so the desktop custom window-chrome treats
 * the bar background as a drag area.
 */
@Composable
fun LiquidGlassTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable () -> Unit = {},
    tint: Color = MaterialTheme.colorScheme.surface,
) {
    val titleBarInteraction = LocalTitleBarInteraction.current
    val startInset = LocalTitleBarStartInset.current
    val endInset = LocalTitleBarEndInset.current
    // GlassSurface должен рисоваться и за статус-баром — иначе системный бар
    // показывает фон скролл-контента, а не тинт тулбара, и цвета не совпадают.
    // Поэтому windowInsetsPadding(statusBars) применяем к внутреннему Row, а не
    // к самому модификатору GlassSurface.
    val barOuter = modifier.fillMaxWidth()
    GlassSurface(
        modifier = titleBarInteraction?.dragArea(barOuter) ?: barOuter,
        shape = RectangleShape,
        tint = tint,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .height(LIQUID_GLASS_TOP_BAR_HEIGHT)
                    .padding(start = startInset, end = endInset)
                    .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Navigation icon slot.
            Box(contentAlignment = Alignment.Center) {
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onSurface,
                ) {
                    navigationIcon()
                }
            }
            // Title slot — uses titleLarge to match Material TopAppBar.
            Box(
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onSurface,
                ) {
                    ProvideTextStyle(MaterialTheme.typography.titleLarge) {
                        title()
                    }
                }
            }
            // Actions slot.
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onSurface,
                ) {
                    actions()
                }
            }
        }
    }
}
