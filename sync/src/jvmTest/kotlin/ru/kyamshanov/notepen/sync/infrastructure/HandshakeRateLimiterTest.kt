package ru.kyamshanov.notepen.sync.infrastructure

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HandshakeRateLimiterTest {
    /** Test clock: advance manually to drive the sliding window deterministically. */
    private class FakeClock(
        var nowMillis: Long = 0L,
    ) {
        fun advance(delta: Long) {
            nowMillis += delta
        }

        fun reader(): () -> Long = { nowMillis }
    }

    @Test
    fun notLockedOutInitially() {
        val limiter = HandshakeRateLimiter(maxFailures = 5, windowMillis = 60_000L)
        assertFalse(limiter.isLockedOut("1.2.3.4"))
    }

    @Test
    fun locksOutAfterReachingThreshold() {
        val clock = FakeClock()
        val limiter = HandshakeRateLimiter(maxFailures = 5, windowMillis = 60_000L, now = clock.reader())
        val key = "1.2.3.4"
        // First four failures stay under the budget.
        repeat(4) { assertFalse(limiter.recordFailure(key), "failure ${it + 1} should not lock out yet") }
        assertFalse(limiter.isLockedOut(key))
        // The fifth failure trips the lockout.
        assertTrue(limiter.recordFailure(key), "the 5th failure should trigger lockout")
        assertTrue(limiter.isLockedOut(key))
    }

    @Test
    fun lockoutIsPerKey() {
        val limiter = HandshakeRateLimiter(maxFailures = 3, windowMillis = 60_000L)
        val attacker = "10.0.0.9"
        repeat(3) { limiter.recordFailure(attacker) }
        assertTrue(limiter.isLockedOut(attacker))
        // A different remote is unaffected.
        assertFalse(limiter.isLockedOut("10.0.0.10"))
    }

    @Test
    fun windowSlidesSoOldFailuresStopCounting() {
        val clock = FakeClock()
        val limiter = HandshakeRateLimiter(maxFailures = 3, windowMillis = 1_000L, now = clock.reader())
        val key = "1.2.3.4"
        // Two failures at t=0.
        limiter.recordFailure(key)
        limiter.recordFailure(key)
        // Move past the window: those two age out.
        clock.advance(1_500L)
        assertFalse(limiter.isLockedOut(key), "failures older than the window must not count")
        // A single fresh failure is not enough on its own.
        assertFalse(limiter.recordFailure(key))
        assertFalse(limiter.isLockedOut(key))
    }

    @Test
    fun failuresWithinWindowAccumulateEvenWhenSpacedOut() {
        val clock = FakeClock()
        val limiter = HandshakeRateLimiter(maxFailures = 3, windowMillis = 10_000L, now = clock.reader())
        val key = "1.2.3.4"
        limiter.recordFailure(key)
        clock.advance(3_000L)
        limiter.recordFailure(key)
        clock.advance(3_000L)
        // All three are inside the 10s window (t=0,3000,6000).
        assertTrue(limiter.recordFailure(key))
        assertTrue(limiter.isLockedOut(key))
    }

    @Test
    fun resetClearsHistoryForKey() {
        val limiter = HandshakeRateLimiter(maxFailures = 3, windowMillis = 60_000L)
        val key = "1.2.3.4"
        repeat(3) { limiter.recordFailure(key) }
        assertTrue(limiter.isLockedOut(key))
        limiter.reset(key)
        assertFalse(limiter.isLockedOut(key), "reset must clear the lockout for the key")
    }

    @Test
    fun clearDropsAllKeys() {
        val limiter = HandshakeRateLimiter(maxFailures = 2, windowMillis = 60_000L)
        repeat(2) { limiter.recordFailure("a") }
        repeat(2) { limiter.recordFailure("b") }
        assertTrue(limiter.isLockedOut("a"))
        assertTrue(limiter.isLockedOut("b"))
        limiter.clear()
        assertFalse(limiter.isLockedOut("a"))
        assertFalse(limiter.isLockedOut("b"))
    }

    @Test
    fun staysLockedForRemainderOfWindowAfterThreshold() {
        val clock = FakeClock()
        val limiter = HandshakeRateLimiter(maxFailures = 2, windowMillis = 10_000L, now = clock.reader())
        val key = "1.2.3.4"
        limiter.recordFailure(key)
        limiter.recordFailure(key)
        assertTrue(limiter.isLockedOut(key))
        // Still within the window a few seconds later -> still locked.
        clock.advance(5_000L)
        assertTrue(limiter.isLockedOut(key))
        // Past the window -> recovers.
        clock.advance(6_000L)
        assertFalse(limiter.isLockedOut(key))
    }
}
