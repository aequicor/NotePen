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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner

@Composable
actual fun ImmersiveEditorMode() {
    val view = LocalView.current
    val activity = LocalContext.current as? Activity
    val lifecycleOwner = view.findViewTreeLifecycleOwner()
    DisposableEffect(activity, view, lifecycleOwner) {
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

            fun recoverInputAfterResume() {
                view.isFocusableInTouchMode = true
                view.requestFocus()
                enterImmersive()
            }

            fun scheduleResumeRecovery() {
                view.post { recoverInputAfterResume() }
                view.postDelayed({ recoverInputAfterResume() }, RESUME_RECOVERY_DELAY_MS)
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
                    if (hasFocus) scheduleResumeRecovery()
                }
            view.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)

            val lifecycleObserver =
                LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        scheduleResumeRecovery()
                    }
                }
            lifecycleOwner?.lifecycle?.addObserver(lifecycleObserver)
            onDispose {
                view.viewTreeObserver.takeIf { it.isAlive }
                    ?.removeOnWindowFocusChangeListener(focusListener)
                lifecycleOwner?.lifecycle?.removeObserver(lifecycleObserver)
                controller.show(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = previousBehavior
            }
        }
    }
}

private const val RESUME_RECOVERY_DELAY_MS = 150L
