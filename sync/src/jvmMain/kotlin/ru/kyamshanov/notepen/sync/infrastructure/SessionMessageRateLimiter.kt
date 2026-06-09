package ru.kyamshanov.notepen.sync.infrastructure

/**
 * Fixed-window incoming-message limiter for a **single** paired WebSocket session.
 *
 * Each accepted session owns one instance. Every inbound message calls [allow]; it
 * returns `false` once more than [maxMessages] messages arrive inside the current
 * [windowMillis] window, at which point [KtorPeerServer] closes the session with
 * `VIOLATED_POLICY`. The window is fixed, not sliding: when the clock crosses a
 * window boundary the counter resets to zero. That coarseness is deliberate — the
 * point is to cap a sustained flood, not to police precise bursts.
 *
 * Limits are intentionally generous so normal stroke/delta sync and file-chunk
 * streaming never trip them: at the defaults a peer may send [maxMessages] (2000)
 * messages per [windowMillis] (1s) — well above any legitimate drawing or transfer
 * rate (64 KiB chunks at 2000 msg/s would be ~128 MiB/s, far past LAN throughput).
 *
 * Not tied to coroutines or a real clock: the time source is injected as [now]
 * (epoch millis), mirroring [HandshakeRateLimiter], so the policy is deterministically
 * unit-testable.
 *
 * Thread-safety: all mutation happens under [lock]. A single session's receive loop
 * is single-threaded, but guarding keeps the type safe under any caller.
 *
 * @param maxMessages messages permitted within one window before [allow] returns false.
 * @param windowMillis length of the fixed counting window, in milliseconds.
 * @param now epoch-millis time source; defaults to the system wall clock.
 */
internal class SessionMessageRateLimiter(
    private val maxMessages: Int = DEFAULT_MAX_MESSAGES,
    private val windowMillis: Long = DEFAULT_WINDOW_MILLIS,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private val lock = Any()

    // Start of the window currently being counted (epoch millis), and how many
    // messages have landed in it so far.
    private var windowStart: Long = now()
    private var count: Int = 0

    /**
     * Records one inbound message and returns whether it is within budget. `false`
     * means the session has exceeded [maxMessages] in the active window and should
     * be closed for policy violation.
     */
    fun allow(): Boolean =
        synchronized(lock) {
            val nowMillis = now()
            if (nowMillis - windowStart >= windowMillis) {
                // Rolled into a fresh window: reset the counter.
                windowStart = nowMillis
                count = 0
            }
            count += 1
            count <= maxMessages
        }

    companion object {
        /** Default per-window message budget. Referenced by [KtorPeerServer]'s default. */
        const val DEFAULT_MAX_MESSAGES = 2_000
        private const val DEFAULT_WINDOW_MILLIS = 1_000L
    }
}
