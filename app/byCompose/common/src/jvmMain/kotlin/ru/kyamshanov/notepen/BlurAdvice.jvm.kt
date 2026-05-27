package ru.kyamshanov.notepen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

private const val LOW_HEAP_MB = 512L
private const val LOW_CORE_COUNT = 2

@Composable
actual fun rememberBlurAdvice(): BlurAdvice =
    remember {
        val runtime = Runtime.getRuntime()
        val maxHeapMb = runtime.maxMemory() / (1024 * 1024)
        val cores = runtime.availableProcessors()
        // Desktop has no portable battery API → only flag low-end hardware.
        val lowEnd = cores <= LOW_CORE_COUNT || maxHeapMb in 1..LOW_HEAP_MB
        BlurAdvice(isLowEndDevice = lowEnd, isBatteryLow = false)
    }
