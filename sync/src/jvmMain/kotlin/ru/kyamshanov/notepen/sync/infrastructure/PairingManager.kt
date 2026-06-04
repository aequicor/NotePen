package ru.kyamshanov.notepen.sync.infrastructure

import java.security.SecureRandom

/**
 * Generates and validates high-entropy pairing codes.
 *
 * The code is a 26-character [Crockford base32](https://www.crockford.com/base32.html)
 * string drawn from [java.security.SecureRandom] (~130 bits of entropy), so it is
 * infeasible to guess online. It is delivered out-of-band — only via the pairing
 * QR or manual entry — and is **never** broadcast on the LAN.
 *
 * In the multi-client model the active code is **reusable** for the lifetime
 * of a `start()`/`stop()` cycle — any number of clients may pair with the
 * same code while the server is running. The code is invalidated only when
 * the caller explicitly calls [invalidate] (typically at server stop).
 */
internal class PairingManager(
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    @Volatile
    private var activeCode: String? = null

    fun generateCode(): String {
        val chars = CharArray(CODE_LENGTH)
        for (i in chars.indices) {
            // nextInt(bound) is unbiased modulo, so the alphabet stays uniform.
            chars[i] = ALPHABET[secureRandom.nextInt(ALPHABET.length)]
        }
        val code = String(chars)
        activeCode = code
        return code
    }

    /**
     * True if [code] matches the currently active code; non-consuming.
     *
     * The comparison is constant-time (it folds every character of both strings
     * and the length difference into a single accumulator) so a network attacker
     * cannot recover the code byte-by-byte from response timing.
     */
    fun validate(code: String): Boolean {
        val current = activeCode ?: return false
        return constantTimeEquals(current, code)
    }

    fun invalidate() {
        activeCode = null
    }

    private companion object {
        const val CODE_LENGTH = 26

        // Crockford base32: digits + uppercase letters minus I, L, O, U to avoid
        // visual ambiguity (and accidental profanity). 32 symbols => 5 bits each;
        // 26 chars => 130 bits of entropy.
        const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

        /**
         * Length-independent equality: never short-circuits on a mismatch, so its
         * running time does not leak how many leading characters matched. Strings of
         * different lengths always return false, but still take the same time as an
         * equal-length compare of [a].
         */
        fun constantTimeEquals(
            a: String,
            b: String,
        ): Boolean {
            var diff = a.length xor b.length
            for (i in a.indices) {
                // Read b at a clamped index so a shorter (or empty) b never throws and
                // the loop count depends only on a; the length xor above still forces a
                // reject whenever the lengths differ.
                val bChar = if (b.isEmpty()) 0 else b[i % b.length].code
                diff = diff or (a[i].code xor bChar)
            }
            return diff == 0
        }
    }
}
