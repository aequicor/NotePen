package ru.kyamshanov.notepen

import android.content.Context

/**
 * Holds the application [Context] for platform code that is constructed outside
 * the Activity/DI graph (e.g. [createAnnotationRepository], document-name
 * resolution). Set once from `Application.onCreate`.
 */
object AppContextHolder {
    lateinit var context: Context
}
