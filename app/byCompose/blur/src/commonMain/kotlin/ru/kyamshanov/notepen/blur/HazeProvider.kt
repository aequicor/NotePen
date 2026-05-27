package ru.kyamshanov.notepen.blur

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * The [HazeState] shared between the blurred glass panels and the background
 * they sample. Defaults to a standalone state so [GlassSurface] never crashes
 * outside a [GlassBackdropProvider]; in that case there is simply no source
 * attached and the panels fall back to alpha + outline only.
 */
val LocalHazeState = staticCompositionLocalOf { HazeState() }

/** Runtime switch for the backdrop blur. When `false`, glass panels keep only alpha + outline. */
val LocalBlurEnabled = staticCompositionLocalOf { true }

/**
 * Hosts a [HazeState] for an editor screen and exposes it (plus the [blurEnabled]
 * switch) to descendant glass panels. Wrap the screen that contains both the
 * background ([Modifier.glassSource]) and the floating [GlassSurface] panels.
 */
@Composable
fun GlassBackdropProvider(
    blurEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val hazeState = rememberHazeState(blurEnabled = blurEnabled)
    CompositionLocalProvider(
        LocalHazeState provides hazeState,
        LocalBlurEnabled provides blurEnabled,
        content = content,
    )
}

/**
 * Marks the receiver as the backdrop sampled by glass panels. Apply to the
 * content that should appear blurred behind the panels (e.g. the PDF canvas).
 */
@Composable
fun Modifier.glassSource(): Modifier {
    if (!LocalBlurEnabled.current) return this
    return hazeSource(LocalHazeState.current)
}
