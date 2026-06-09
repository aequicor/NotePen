package ru.kyamshanov.notepen.sync.infrastructure

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionMessageRateLimiterTest {
    /** Test clock: advance manually to drive the fixed window deterministically. */
    private class FakeClock(
        var nowMillis: Long = 0L,
    ) {
        fun advance(delta: Long) {
            nowMillis += delta
        }

        fun reader(): () -> Long = { nowMillis }
    }

    @Test
    fun allowsUpToTheBudgetWithinAWindow() {
        val clock = FakeClock()
        val limiter = SessionMessageRateLimiter(maxMessages = 3, windowMillis = 1_000L, now = clock.reader())
        // The first three messages are within budget.
        repeat(3) { assertTrue(limiter.allow(), "message ${it + 1} should be allowed") }
    }

    @Test
    fun blocksOnceTheBudgetIsExceededInTheWindow() {
        val clock = FakeClock()
        val limiter = SessionMessageRateLimiter(maxMessages = 3, windowMillis = 1_000L, now = clock.reader())
        repeat(3) { assertTrue(limiter.allow()) }
        // The fourth message in the same window trips the limit — the session is flooding.
        assertFalse(limiter.allow(), "exceeding the budget in one window must be rejected")
        // Still flooding: subsequent messages in the same window remain rejected.
        assertFalse(limiter.allow())
    }

    @Test
    fun counterResetsWhenTheWindowRolls() {
        val clock = FakeClock()
        val limiter = SessionMessageRateLimiter(maxMessages = 2, windowMillis = 1_000L, now = clock.reader())
        assertTrue(limiter.allow())
        assertTrue(limiter.allow())
        assertFalse(limiter.allow(), "over budget in the first window")
        // Cross the window boundary: the counter resets and traffic is allowed again.
        clock.advance(1_000L)
        assertTrue(limiter.allow(), "a fresh window must reset the counter")
        assertTrue(limiter.allow())
        assertFalse(limiter.allow())
    }

    @Test
    fun sustainedDripUnderBudgetNeverTrips() {
        val clock = FakeClock()
        val limiter = SessionMessageRateLimiter(maxMessages = 5, windowMillis = 1_000L, now = clock.reader())
        // One message every 300ms: at most two land in any 1s window, well under budget.
        repeat(20) {
            assertTrue(limiter.allow(), "steady low-rate traffic must always be allowed")
            clock.advance(300L)
        }
    }

    @Test
    fun defaultBudgetIsGenerousEnoughForNormalSync() {
        val clock = FakeClock()
        // Defaults (2000 msg/s): a burst of 1000 messages in one window — far above any real
        // stroke/delta/file-chunk rate — must still be allowed without tripping.
        val limiter = SessionMessageRateLimiter(now = clock.reader())
        repeat(1_000) { assertTrue(limiter.allow(), "message ${it + 1} within the default budget") }
    }
}
