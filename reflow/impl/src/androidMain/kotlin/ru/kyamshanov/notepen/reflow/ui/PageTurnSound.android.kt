package ru.kyamshanov.notepen.reflow.ui

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember

@Composable
internal actual fun rememberPageTurnSoundPlayer(): () -> Unit {
    val tone = remember { ToneGenerator(AudioManager.STREAM_MUSIC, PAGE_TURN_VOLUME) }
    DisposableEffect(tone) {
        onDispose { tone.release() }
    }
    return remember(tone) {
        { tone.startTone(ToneGenerator.TONE_PROP_ACK, PAGE_TURN_DURATION_MS) }
    }
}

private const val PAGE_TURN_VOLUME = 28
private const val PAGE_TURN_DURATION_MS = 45
