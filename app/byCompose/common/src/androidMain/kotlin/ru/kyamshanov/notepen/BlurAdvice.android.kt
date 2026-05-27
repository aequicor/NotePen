package ru.kyamshanov.notepen

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

private const val LOW_BATTERY_THRESHOLD = 0.2f

@Composable
actual fun rememberBlurAdvice(): BlurAdvice {
    val context = LocalContext.current
    val isLowEnd =
        remember(context) {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.isLowRamDevice == true
        }
    var batteryLow by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    ctx: Context?,
                    intent: Intent?,
                ) {
                    intent ?: return
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val charging =
                        status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL
                    val pct = if (level >= 0 && scale > 0) level.toFloat() / scale else 1f
                    batteryLow = !charging && pct <= LOW_BATTERY_THRESHOLD
                }
            }
        // registerReceiver returns the current sticky battery Intent; seed initial state from it.
        val sticky = context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        receiver.onReceive(context, sticky)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return BlurAdvice(isLowEndDevice = isLowEnd, isBatteryLow = batteryLow)
}
