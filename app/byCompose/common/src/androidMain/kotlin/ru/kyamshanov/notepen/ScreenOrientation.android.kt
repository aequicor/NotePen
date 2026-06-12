package ru.kyamshanov.notepen

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ru.kyamshanov.notepen.appsettings.domain.model.ScreenOrientationMode

/**
 * Выставляет `Activity.requestedOrientation` под выбранный [mode] на время жизни
 * эффекта. Жёсткие режимы используют `SENSOR_*` — экран залочен в ориентации, но
 * может перевернуться на 180° (удобно держать планшет любой стороной лёжа).
 * `AUTO` возвращает `UNSPECIFIED` (поведение по умолчанию — следовать системе).
 *
 * При выходе из композиции восстанавливает прежнее значение, поэтому выход из
 * редактора/ридера снимает лок (на корне приложения — `UNSPECIFIED`).
 */
@Composable
actual fun ApplyScreenOrientation(mode: ScreenOrientationMode) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() } ?: return
    DisposableEffect(activity, mode) {
        val previous = activity.requestedOrientation
        activity.requestedOrientation =
            when (mode) {
                ScreenOrientationMode.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                ScreenOrientationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                ScreenOrientationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
        onDispose { activity.requestedOrientation = previous }
    }
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
