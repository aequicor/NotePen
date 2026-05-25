package ru.kyamshanov.notepen

import android.app.Activity
import android.view.KeyEvent
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun ReaderVolumeKeyHandler(
    enabled: Boolean,
    onPageDelta: (Int) -> Unit,
) {
    val activity = LocalContext.current as? Activity
    // Колбэк переустанавливается лишь при смене Activity/флага enabled; саму
    // лямбду читаем «свежей» из updated-state, чтобы рекомпозиции с новым
    // onPageDelta не дёргали окно.
    val latestOnPageDelta by rememberUpdatedState(onPageDelta)
    DisposableEffect(activity, enabled) {
        val window = activity?.window
        if (window == null || !enabled) {
            onDispose { }
        } else {
            val original = window.callback
            window.callback = VolumeKeyInterceptingCallback(original) { delta -> latestOnPageDelta(delta) }
            onDispose {
                // Снимаем перехват, только если наш колбэк всё ещё активен — иначе
                // (кто-то обернул поверх нас) восстановление затёрло бы чужую обёртку.
                if (window.callback is VolumeKeyInterceptingCallback) {
                    window.callback = original
                }
            }
        }
    }
}

/**
 * Делегирует всё исходному [delegate], но перехватывает клавиши громкости:
 * `VOLUME_DOWN` → [onPageDelta]`(+1)`, `VOLUME_UP` → [onPageDelta]`(-1)`. И down-,
 * и up-события этих клавиш съедаются (возвращает `true`), чтобы система не показала
 * регулятор громкости и не изменила её. Повтор по удержанию игнорируем, листаем
 * по разовому нажатию.
 */
private class VolumeKeyInterceptingCallback(
    private val delegate: Window.Callback,
    private val onPageDelta: (Int) -> Unit,
) : Window.Callback by delegate {
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) onPageDelta(1)
                true
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) onPageDelta(-1)
                true
            }
            else -> delegate.dispatchKeyEvent(event)
        }
}
