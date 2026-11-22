package dev.communicator.helmchatrisen.jitsi

/**
 * Tracks how much of a reconnect watchdog deadline is left across network outages.
 *
 * Offline time must not consume the watchdog: pausing stores the remainder and resuming returns it
 * so the caller can restart the timer with exactly the time that was still available.
 *
 * @param now supplies elapsed realtime milliseconds; injectable so pause/resume can be tested with
 *   a fake clock.
 */
internal class ReconnectDeadline(
    private val now: () -> Long,
) {
    private var fullTimeoutMs = 0L
    private var deadlineAtMs = 0L
    private var pausedRemainingMs = 0L
    private var isActive = false

    /**
     * Starts a fresh countdown of [timeoutMs], discarding any previously paused remainder.
     *
     * @param timeoutMs the full watchdog duration in milliseconds.
     */
    fun start(timeoutMs: Long) {
        fullTimeoutMs = timeoutMs.coerceAtLeast(0L)
        deadlineAtMs = now() + fullTimeoutMs
        pausedRemainingMs = 0L
        isActive = true
    }

    /**
     * Pauses an active countdown. Repeated calls preserve the same remainder, so a second
     * network-loss callback cannot extend the deadline.
     *
     * @return the milliseconds left when the countdown was paused.
     */
    fun pause(): Long {
        if (isActive) {
            pausedRemainingMs = (deadlineAtMs - now()).coerceIn(0L, fullTimeoutMs)
            deadlineAtMs = 0L
            isActive = false
        }
        return pausedRemainingMs
    }

    /**
     * Consumes the paused remainder so the caller can restart the watchdog with that duration.
     *
     * @return the remaining milliseconds, or zero when no countdown was paused.
     */
    fun resume(): Long {
        val remaining = pausedRemainingMs
        pausedRemainingMs = 0L
        return remaining
    }

    /** Drops all deadline state, for example when a call ends or a new join starts. */
    fun clear() {
        fullTimeoutMs = 0L
        deadlineAtMs = 0L
        pausedRemainingMs = 0L
        isActive = false
    }
}
