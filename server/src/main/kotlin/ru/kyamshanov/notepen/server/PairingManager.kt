package ru.kyamshanov.notepen.server

import kotlin.random.Random

/** Generates and validates 6-digit pairing codes. Codes are single-use. */
internal class PairingManager {

    @Volatile
    private var activeCode: String? = null

    fun generateCode(): String {
        val code = Random.nextInt(100_000, 999_999).toString()
        activeCode = code
        return code
    }

    fun validateAndConsume(code: String): Boolean {
        val current = activeCode ?: return false
        return if (current == code) {
            activeCode = null
            true
        } else {
            false
        }
    }

    fun invalidate() {
        activeCode = null
    }
}
