package ru.kyamshanov.notepen.reflow.ui

import java.util.Calendar

internal actual fun currentLocalHour(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
