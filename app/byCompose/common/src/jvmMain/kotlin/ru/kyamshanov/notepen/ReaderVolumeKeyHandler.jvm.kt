package ru.kyamshanov.notepen

import androidx.compose.runtime.Composable

/**
 * На десктопе клавиш громкости нет — листание привязано к стрелкам/PageUp-Down/Space
 * через общий `onKeyEvent` в `DetailsContent`. Перехватывать нечего, поэтому no-op.
 */
@Composable
actual fun ReaderVolumeKeyHandler(
    enabled: Boolean,
    onPageDelta: (Int) -> Unit,
) {
    // no-op
}
