package ru.kyamshanov.notepen.sync.infrastructure

import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PairingManagerTest {
    private val crockfordAlphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toSet()

    @Test
    fun generatedCodeHasExpectedLengthAndCharset() {
        val manager = PairingManager()
        repeat(50) {
            val code = manager.generateCode()
            assertEquals(EXPECTED_LENGTH, code.length, "code length")
            assertTrue(
                code.all { it in crockfordAlphabet },
                "code '$code' has a character outside the Crockford base32 alphabet",
            )
        }
    }

    @Test
    fun generatedCodesAreDistinctAcrossManyDraws() {
        val manager = PairingManager()
        val codes = (1..200).map { manager.generateCode() }.toSet()
        // 130 bits of entropy: a collision in 200 draws is astronomically unlikely.
        assertEquals(200, codes.size, "expected all generated codes to be unique")
    }

    @Test
    fun validateAcceptsTheActiveCode() {
        val manager = PairingManager()
        val code = manager.generateCode()
        assertTrue(manager.validate(code))
    }

    @Test
    fun validateIsReusableAcrossMultipleClients() {
        val manager = PairingManager()
        val code = manager.generateCode()
        assertTrue(manager.validate(code))
        assertTrue(manager.validate(code), "code must stay valid for repeated (multi-client) use")
    }

    @Test
    fun validateRejectsWrongCode() {
        val manager = PairingManager()
        manager.generateCode()
        assertFalse(manager.validate("0123456789ABCDEFGHJKMNPQRS"))
    }

    @Test
    fun validateRejectsCodeOfDifferentLengthWithoutThrowing() {
        val manager = PairingManager()
        val code = manager.generateCode()
        // Constant-time compare must tolerate (and reject) shorter, longer, and empty
        // candidates without an index-out-of-bounds.
        assertFalse(manager.validate(""))
        assertFalse(manager.validate(code.dropLast(1)))
        assertFalse(manager.validate(code + "0"))
    }

    @Test
    fun validateRejectsBeforeAnyCodeGenerated() {
        val manager = PairingManager()
        assertFalse(manager.validate("anything"))
    }

    @Test
    fun invalidateRevokesTheCode() {
        val manager = PairingManager()
        val code = manager.generateCode()
        manager.invalidate()
        assertFalse(manager.validate(code))
    }

    @Test
    fun generateCodeRotatesTheActiveCode() {
        val manager = PairingManager()
        val first = manager.generateCode()
        val second = manager.generateCode()
        assertFalse(manager.validate(first), "the previous code must no longer validate")
        assertTrue(manager.validate(second))
    }

    @Test
    fun usesInjectedSecureRandomDeterministicallyForTesting() {
        // Two managers seeded identically must mint identical codes — proves the
        // injected SecureRandom is the sole entropy source (no hidden randomness).
        fun seeded() = PairingManager(secureRandom = SecureRandom.getInstance("SHA1PRNG").apply { setSeed(42L) })
        assertEquals(seeded().generateCode(), seeded().generateCode())
    }

    @Test
    fun constantTimeEqualsMatchesValueEqualityOnEqualLengthInputs() {
        // Spot-check the constant-time comparator against plain equality so the
        // bit-folding logic can't silently diverge from `==` for valid codes.
        val manager = PairingManager()
        val code = manager.generateCode()
        assertTrue(manager.validate(String(code.toCharArray())), "a fresh copy must compare equal")
    }

    private companion object {
        const val EXPECTED_LENGTH = 26
    }
}
