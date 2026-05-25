package ru.kyamshanov.notepen.sync.infrastructure

import kotlin.random.Random

/**
 * Generates and validates 6-digit pairing codes.
 *
 * In the multi-client model the active code is **reusable** for the lifetime
 * of a `start()`/`stop()` cycle — any number of clients may pair with the
 * same code while the server is running. The code is invalidated only when
 * the caller explicitly calls [invalidate] (typically at server stop).
 */
internal class PairingManager {
    @Volatile
    private var activeCode: String? = null

    fun generateCode(): String {
        val code = Random.nextInt(100_000, 999_999).toString()
        activeCode = code
        return code
    }

    /** True if [code] matches the currently active code; non-consuming. */
    fun validate(code: String): Boolean {
        val current = activeCode ?: return false
        return current == code
    }

    fun invalidate() {
        activeCode = null
    }
}
