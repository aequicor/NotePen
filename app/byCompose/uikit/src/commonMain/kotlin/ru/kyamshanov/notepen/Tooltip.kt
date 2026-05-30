package ru.kyamshanov.notepen

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Оборачивает [content] (обычно иконочную кнопку) подсказкой с текстом [text].
 *
 * Способ вызова подсказки зависит от платформы (единственное различие — actual):
 *  - **Desktop** — наведение мышью с задержкой ([TOOLTIP_HOVER_DELAY_MS]);
 *  - **Mobile** — долгое нажатие; обычный тап при этом по-прежнему доходит до
 *    [content] (кнопка срабатывает), а долгое нажатие показывает подсказку, не
 *    активируя кнопку.
 *
 * Визуально подсказка одинакова на всех платформах — см. [TooltipChip].
 *
 * @param text текст подсказки; пустая строка делает обёртку прозрачной (подсказка
 *   не показывается), что удобно для кнопок без подписи.
 * @param modifier применяется к области-якорю, реагирующей на наведение/нажатие.
 */
@Composable
public expect fun Tooltip(
    text: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
)

/**
 * Общий для всех платформ «пузырёк» подсказки: тёмная плашка с инверсной палитрой
 * темы ([androidx.compose.material3.ColorScheme.inverseSurface] /
 * [androidx.compose.material3.ColorScheme.inverseOnSurface]), как у стандартных
 * подсказок Material. Используется обоими actual-реализациями [Tooltip].
 */
@Composable
internal fun TooltipChip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shape = RoundedCornerShape(TOOLTIP_CORNER),
        shadowElevation = TOOLTIP_ELEVATION,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = TOOLTIP_PADDING_H, vertical = TOOLTIP_PADDING_V),
        )
    }
}

/** Скругление углов плашки подсказки. */
internal val TOOLTIP_CORNER = 6.dp

/** Высота тени плашки подсказки. */
internal val TOOLTIP_ELEVATION = 4.dp

/** Горизонтальный внутренний отступ текста подсказки. */
internal val TOOLTIP_PADDING_H = 8.dp

/** Вертикальный внутренний отступ текста подсказки. */
internal val TOOLTIP_PADDING_V = 4.dp

/** Зазор между якорем и плашкой подсказки (mobile). */
internal val TOOLTIP_GAP = 8.dp

/** Задержка перед показом подсказки при наведении мышью (desktop), мс. */
internal const val TOOLTIP_HOVER_DELAY_MS = 600

/** Сколько подсказка держится на экране после долгого нажатия (mobile), мс. */
internal const val TOOLTIP_VISIBLE_MS = 1500L
