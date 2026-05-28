package ru.kyamshanov.notepen.blur

import android.os.Build

actual fun isBlurEffectSupported(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
