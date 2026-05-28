package ru.kyamshanov.notepen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.kyamshanov.notepen.blur.GlassSurface
import ru.kyamshanov.notepen.blur.LocalBlurEnabled

/** 24dp matches `GlassCornerRadius` — keeps dialogs visually consistent with floating panels. */
val LiquidGlassDialogShape: Shape = RoundedCornerShape(24.dp)

/**
 * Drop-in replacement for Material3 `AlertDialog`, rebuilt on `Dialog` so the
 * surface itself is a `GlassSurface` (not a flat Material `Surface`).
 *
 * Note on blur: dialogs render in a separate compose root, so the host
 * screen's `GlassBackdropProvider` cannot supply a sampled backdrop here.
 * `LocalBlurEnabled = false` forces `GlassSurface`'s flat-fallback look,
 * which we then dial up (denser tint + soft drop shadow + thin outline) so the
 * dialog reads as a frosted panel even without live refraction. Content
 * underneath stops bleeding through (the prior 0.92α was too sheer over the
 * library hero gradient — buttons blurred into the cards behind).
 */
@Composable
fun LiquidGlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = LiquidGlassDialogShape,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    properties: DialogProperties = DialogProperties(),
) {
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        CompositionLocalProvider(LocalBlurEnabled provides false) {
            Box(
                modifier =
                    modifier
                        .widthIn(min = LiquidGlassDialogMinWidth, max = LiquidGlassDialogMaxWidth)
                        .wrapContentSize()
                        .shadow(elevation = 24.dp, shape = shape, clip = false)
                        .clip(shape),
            ) {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = shape,
                    tint = containerColor,
                    fillAlpha = LIQUID_GLASS_DIALOG_FILL_ALPHA,
                ) {
                    LiquidGlassDialogBody(
                        icon = icon,
                        title = title,
                        text = text,
                        confirmButton = confirmButton,
                        dismissButton = dismissButton,
                    )
                }
            }
        }
    }
}

@Composable
private fun LiquidGlassDialogBody(
    icon: (@Composable () -> Unit)?,
    title: (@Composable () -> Unit)?,
    text: (@Composable () -> Unit)?,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)?,
) {
    Column(modifier = Modifier.padding(LiquidGlassDialogContentPadding)) {
        if (icon != null) {
            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) { icon() }
        }
        if (title != null) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                ProvideTextStyle(MaterialTheme.typography.headlineSmall) { title() }
            }
            Spacer(Modifier.height(16.dp))
        }
        if (text != null) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                ProvideTextStyle(MaterialTheme.typography.bodyMedium) { text() }
            }
            Spacer(Modifier.height(24.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            if (dismissButton != null) {
                dismissButton()
                Spacer(Modifier.width(8.dp))
            }
            confirmButton()
        }
    }
}

/**
 * Dialog body is opaque-feeling so contents behind it (library cards on the
 * hero gradient) don't read through. Lower than 1.0 keeps a hint of the
 * underlying color so the panel still feels lifted off the surface.
 */
private const val LIQUID_GLASS_DIALOG_FILL_ALPHA = 0.96f
private val LiquidGlassDialogMinWidth = 280.dp
private val LiquidGlassDialogMaxWidth = 560.dp
private val LiquidGlassDialogContentPadding = 24.dp
