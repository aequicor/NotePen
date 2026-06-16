package ru.kyamshanov.notepen.uikitsandbox.presentation.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Android actual, который синхронизирует toggle ориентации с Activity.
 *
 * Предыдущее значение Activity восстанавливается при уходе composable из composition.
 *
 * @param orientation выбранная ориентация sandbox preview.
 */
@Composable
internal actual fun SandboxApplyOrientation(orientation: SandboxPreviewOrientation) {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity, orientation) {
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation =
            when (orientation) {
                SandboxPreviewOrientation.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                SandboxPreviewOrientation.Landscape -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        onDispose {
            if (previous != null) {
                activity.requestedOrientation = previous
            }
        }
    }
}

/**
 * Находит Activity в цепочке [ContextWrapper].
 *
 * @return ближайшая Activity или null, если composable запущен вне Activity.
 */
private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
