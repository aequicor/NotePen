package ru.kyamshanov.notepen

import android.app.Activity
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

@Composable
actual fun ImmersiveEditorMode() {
    val view = LocalView.current
    val activity = LocalContext.current as? Activity
    DisposableEffect(activity, view) {
        val window = activity?.window
        if (window == null) {
            onDispose { }
        } else {
            val controller = WindowCompat.getInsetsController(window, view)
            val previousBehavior = controller.systemBarsBehavior

            fun enterImmersive() {
                // Swipe from an edge briefly reveals the bars, then they auto-hide
                // again — otherwise the clock and notifications would be unreachable
                // while drawing.
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            }
            enterImmersive()
            // Backgrounding the app (or pulling the notification shade) makes the
            // system reveal the bars as a transient layer; on some devices that
            // layer stays armed after the window regains focus and then swallows
            // every touch while the bars themselves are already hidden again —
            // the editor looks full-screen but ignores all input. Re-asserting
            // immersive on each focus gain tears that stale touch layer down.
            val focusListener =
                ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
                    if (hasFocus) enterImmersive()
                }
            view.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)
            onDispose {
                view.viewTreeObserver.takeIf { it.isAlive }
                    ?.removeOnWindowFocusChangeListener(focusListener)
                controller.show(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = previousBehavior
            }
        }
    }
}
