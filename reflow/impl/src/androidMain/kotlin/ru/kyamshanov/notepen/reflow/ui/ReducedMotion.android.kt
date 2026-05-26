package ru.kyamshanov.notepen.reflow.ui

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Системная настройка «Удалить анимации» обнуляет шкалы анимаций; проверяем
 * [Settings.Global.ANIMATOR_DURATION_SCALE] — именно она управляет
 * property-анимациями контента (наш переход страниц). Значение по умолчанию `1f`,
 * поэтому при отсутствии ключа возвращаем «движение разрешено».
 */
@Composable
internal actual fun isReducedMotionEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}
