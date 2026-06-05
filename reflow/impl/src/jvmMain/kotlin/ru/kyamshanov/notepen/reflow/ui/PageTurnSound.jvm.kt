package ru.kyamshanov.notepen.reflow.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.util.concurrent.atomic.AtomicLong
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

@Composable
internal actual fun rememberPageTurnSoundPlayer(): () -> Unit {
    val lastPlayedAt = remember { AtomicLong(0L) }
    return remember(lastPlayedAt) {
        {
            val now = System.nanoTime()
            val previous = lastPlayedAt.get()
            if (now - previous > PAGE_TURN_MIN_INTERVAL_NANOS && lastPlayedAt.compareAndSet(previous, now)) {
                thread(name = "notepen-page-turn-sound", isDaemon = true) {
                    runCatching { playPageTurnRustle() }
                }
            }
        }
    }
}

private fun playPageTurnRustle() {
    val format = AudioFormat(SAMPLE_RATE.toFloat(), BITS_PER_SAMPLE, CHANNELS, true, false)
    val line = AudioSystem.getSourceDataLine(format)
    val bytes = pageTurnBytes()
    line.open(format, bytes.size)
    line.start()
    line.write(bytes, 0, bytes.size)
    line.drain()
    line.stop()
    line.close()
}

private fun pageTurnBytes(): ByteArray {
    val samples = (SAMPLE_RATE * DURATION_MS / 1000f).toInt()
    val random = Random(System.nanoTime())
    return ByteArray(samples * 2).also { bytes ->
        repeat(samples) { i ->
            val t = i / SAMPLE_RATE.toFloat()
            val progress = i / samples.toFloat()
            val attack = (progress / 0.18f).coerceIn(0f, 1f)
            val release = ((1f - progress) / 0.82f).coerceIn(0f, 1f)
            val envelope = attack * release * release
            val noise = random.nextFloat() * 2f - 1f
            val paperScrape = sin(2f * PI.toFloat() * (920f + 360f * progress) * t)
            val sample = ((noise * 0.72f + paperScrape * 0.18f) * envelope * MAX_AMPLITUDE).toInt()
            bytes[i * 2] = (sample and 0xFF).toByte()
            bytes[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
        }
    }
}

private const val SAMPLE_RATE = 22_050
private const val BITS_PER_SAMPLE = 16
private const val CHANNELS = 1
private const val DURATION_MS = 130
private const val MAX_AMPLITUDE = 5_200
private const val PAGE_TURN_MIN_INTERVAL_NANOS = 120_000_000L
