package ru.kyamshanov.notepen.reflow.ui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import ru.kyamshanov.notepen.reflow.api.TextAnchor

/**
 * Окружение выделения текста для ридера. Чтобы не пробрасывать «как создать/как
 * запускать» через все уровни композиции (контент → блок → текст), передаётся
 * ambient-значением через [LocalReflowSelection].
 *
 * Выделение в режиме чтения доступно всегда (а не только при активном маркере) и
 * на отпускании всегда создаёт подсветку. От состояния маркера зависит лишь жест
 * запуска: [immediate] — тянем сразу (маркер активен), иначе — после удержания.
 *
 * @property immediate `true` — выделение стартует немедленным перетаскиванием
 *   (активен маркер); `false` — после долгого нажатия (чтобы не мешать листанию)
 * @property onCreate вызывается на отпускании со всеми диапазонами текста под
 *   жестом (по одному [TextAnchor] на покрытый блок), которые надо превратить в
 *   подсветку; пустой список не передаётся
 */
public class ReflowSelection(
    public val immediate: Boolean = false,
    public val onCreate: (List<TextAnchor>) -> Unit = {},
)

/** Ambient-окружение выделения текста для блоков ридера; по умолчанию — после удержания. */
public val LocalReflowSelection: ProvidableCompositionLocal<ReflowSelection> =
    compositionLocalOf { ReflowSelection() }
