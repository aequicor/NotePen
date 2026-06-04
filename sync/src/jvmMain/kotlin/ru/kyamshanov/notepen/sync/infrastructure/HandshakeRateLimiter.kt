package ru.kyamshanov.notepen.sync.infrastructure

/**
 * Sliding-window limiter for failed pairing handshakes, keyed by a caller-chosen
 * identity (the remote IP address in [KtorPeerServer]).
 *
 * A remote that fails code validation [maxFailures] times within [windowMillis]
 * is locked out: [isLockedOut] returns true and the server can reject further
 * attempts cheaply, without doing the (constant-time) code compare or otherwise
 * revealing whether a guess was close. The window slides — once the oldest
 * recorded failure ages past [windowMillis] it stops counting, so a remote that
 * backs off recovers automatically. A successful pairing should call [reset] to
 * clear that remote's history.
 *
 * Not tied to coroutines or a real clock: the time source is injected as
 * [now] (epoch millis) so the policy is deterministically unit-testable.
 *
 * Thread-safety: all mutation happens under [lock]; safe to call from the
 * concurrent Ktor session coroutines.
 *
 * @param maxFailures number of failures within the window that triggers lockout.
 * @param windowMillis length of the sliding window, in milliseconds.
 * @param now epoch-millis time source; defaults to the system wall clock.
 */
internal class HandshakeRateLimiter(
    private val maxFailures: Int = DEFAULT_MAX_FAILURES,
    private val windowMillis: Long = DEFAULT_WINDOW_MILLIS,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private val lock = Any()

    // key -> recent failure timestamps (epoch millis), oldest first. Pruned to the
    // window on every touch so the map can't grow without bound for one key.
    private val failures = mutableMapOf<String, ArrayDeque<Long>>()

    /**
     * True if [key] has already reached the failure threshold inside the current
     * window. Pure query — does not itself record a failure.
     */
    fun isLockedOut(key: String): Boolean =
        synchronized(lock) {
            pruned(key).size >= maxFailures
        }

    /**
     * Records one failed attempt for [key] and returns whether that pushes it into
     * (or keeps it in) the locked-out state.
     */
    fun recordFailure(key: String): Boolean =
        synchronized(lock) {
            val window = pruned(key)
            window.addLast(now())
            // pruned() drops empty windows from the map; re-anchor after adding so
            // the new timestamp is actually retained.
            failures[key] = window
            window.size >= maxFailures
        }

    /** Clears [key]'s history, e.g. after a successful pairing. */
    fun reset(key: String) {
        synchronized(lock) { failures.remove(key) }
    }

    /** Drops every key's history (server teardown). */
    fun clear() {
        synchronized(lock) { failures.clear() }
    }

    /**
     * Returns [key]'s failure deque after evicting timestamps older than the
     * window; removes the key entirely when nothing recent remains. Must be called
     * while holding [lock].
     */
    private fun pruned(key: String): ArrayDeque<Long> {
        val cutoff = now() - windowMillis
        val window = failures.getOrPut(key) { ArrayDeque() }
        while (window.isNotEmpty() && window.first() <= cutoff) {
            window.removeFirst()
        }
        if (window.isEmpty()) failures.remove(key)
        return window
    }

    private companion object {
        const val DEFAULT_MAX_FAILURES = 5
        const val DEFAULT_WINDOW_MILLIS = 60_000L
    }
}
