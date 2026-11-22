package dev.communicator.helmchatrisen.jitsi

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the reconnect watchdog's pause/resume state machine with a fake clock. Offline time must
 * not consume the deadline, and a repeated network-loss callback must not extend it.
 */
class ReconnectDeadlineTest {

    @Test
    fun pauseAfterHalfTheDeadline_keepsOnlyTheRemainingTime() {
        var now = 1_000L
        val deadline = ReconnectDeadline(now = { now })

        deadline.start(timeoutMs = 60_000L)
        now += 20_000L

        assertEquals(40_000L, deadline.pause())
    }

    @Test
    fun resume_consumesTheRemainderSoAnewStartDoesNotReuseIt() {
        var now = 500L
        val deadline = ReconnectDeadline(now = { now })

        deadline.start(timeoutMs = 150_000L)
        now += 30_000L
        deadline.pause()
        now += 90_000L

        assertEquals(120_000L, deadline.resume())
        assertEquals(0L, deadline.resume())
    }

    @Test
    fun pauseAfterTheDeadline_resumesWithZeroInsteadOfANegativeDelay() {
        var now = 0L
        val deadline = ReconnectDeadline(now = { now })

        deadline.start(timeoutMs = 60_000L)
        now += 90_000L

        assertEquals(0L, deadline.pause())
    }

    @Test
    fun repeatedPauseAndOfflineScheduling_doNotExtendTheDeadline() {
        var now = 10_000L
        val deadline = ReconnectDeadline(now = { now })

        // scheduleReconnectTimeout() starts and immediately pauses when the device is offline.
        deadline.start(timeoutMs = 60_000L)
        assertEquals(60_000L, deadline.pause())
        now += 15_000L

        // A second onLost() while still offline must keep the same remainder.
        assertEquals(60_000L, deadline.pause())

        val resumed = deadline.resume()
        assertEquals(60_000L, resumed)
        deadline.start(timeoutMs = resumed)
        now += 10_000L

        assertEquals(50_000L, deadline.pause())
    }

    @Test
    fun clear_discardsBothActiveAndPausedState() {
        var now = 100L
        val deadline = ReconnectDeadline(now = { now })

        deadline.start(timeoutMs = 60_000L)
        deadline.clear()
        assertEquals(0L, deadline.pause())

        deadline.start(timeoutMs = 60_000L)
        deadline.pause()
        deadline.clear()
        assertEquals(0L, deadline.resume())
    }
}
