package ru.kyamshanov.notepen

import ru.kyamshanov.notepen.annotation.domain.port.AnnotationRepository

actual fun createAnnotationRepository(): AnnotationRepository = AnnotationRepositoryJvmAndroid()
