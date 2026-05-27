package ru.kyamshanov.notepen.mainscreen.domain.model

actual fun generateUuid(): String =
    java.util.UUID
        .randomUUID()
        .toString()
