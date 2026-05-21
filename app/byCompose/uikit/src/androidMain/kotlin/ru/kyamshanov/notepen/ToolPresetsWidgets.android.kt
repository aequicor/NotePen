package ru.kyamshanov.notepen

import androidx.compose.ui.Modifier

/** No secondary mouse button on touch devices — deletion uses long-press. */
internal actual fun Modifier.secondaryClickModifier(onSecondaryClick: () -> Unit): Modifier = this
