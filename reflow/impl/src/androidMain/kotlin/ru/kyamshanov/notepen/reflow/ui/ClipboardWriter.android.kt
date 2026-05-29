package ru.kyamshanov.notepen.reflow.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Android: пишем в системный буфер обмена через [ClipboardManager], полученный из
 * [LocalContext] (так же, как другие android-actual'ы этого модуля берут платформенные
 * сервисы из композиционного контекста — см. `ReducedMotion.android.kt`). Метка ярлыка —
 * «Выделенный текст»; пустой текст не копируется.
 */
@Composable
internal actual fun rememberClipboardWriter(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { text ->
            if (text.isNotEmpty()) {
                val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                manager?.setPrimaryClip(ClipData.newPlainText("Выделенный текст", text))
            }
        }
    }
}
