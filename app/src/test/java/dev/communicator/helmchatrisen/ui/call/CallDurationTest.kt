package dev.communicator.helmchatrisen.ui.call

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the compact duration label shown above the call screen's audio-route control.
 */
class CallDurationTest {

    /** A fresh or uninitialized call shows a zero timer. */
    @Test
    fun zeroAndNegativeDurations_showZeroTimer() {
        assertEquals("00:00", formatCallDuration(0L))
        assertEquals("00:00", formatCallDuration(-5_000L))
    }

    /** Milliseconds below a full second are truncated rather than rounded up. */
    @Test
    fun subSecondDuration_isTruncated() {
        assertEquals("00:00", formatCallDuration(999L))
    }

    /** Durations under one hour use two digits for minutes and seconds. */
    @Test
    fun shortDurations_useMinutesAndSeconds() {
        assertEquals("00:59", formatCallDuration(59_000L))
        assertEquals("01:00", formatCallDuration(60_000L))
        assertEquals("01:23", formatCallDuration(83_000L))
    }

    /** The hour appears only from 60 minutes onward, without a leading zero. */
    @Test
    fun longDurations_useHoursMinutesAndSeconds() {
        assertEquals("59:59", formatCallDuration(3_599_000L))
        assertEquals("1:00:00", formatCallDuration(3_600_000L))
        assertEquals("1:02:03", formatCallDuration(3_723_000L))
    }

    /** A call that never started emits only zero and completes. */
    @Test
    fun callElapsedFlow_withoutStart_emitsOnlyZero() = runTest {
        assertEquals(
            listOf(0L),
            callElapsedFlow(startedAtMillis = 0L, nowMillis = { 42_000L }).toList(),
        )
        assertEquals(
            0L,
            callElapsedFlow(startedAtMillis = -1L, nowMillis = { 42_000L }).first(),
        )
    }

    /** An active call emits the difference between now and the monotonic start timestamp. */
    @Test
    fun callElapsedFlow_withStart_emitsElapsedTime() = runTest {
        val elapsed = callElapsedFlow(
            startedAtMillis = 4_000L,
            nowMillis = { 10_000L },
            tickMs = 10L,
        )

        assertEquals(6_000L, elapsed.first())
    }

    /** A fresh collection after a lifecycle stop recomputes instead of replaying a stale value. */
    @Test
    fun callElapsedFlow_onNewCollection_recomputesFromMonotonicTimestamp() = runTest {
        var nowMillis = 10_000L
        val elapsed = callElapsedFlow(
            startedAtMillis = 4_000L,
            nowMillis = { nowMillis },
            tickMs = 10L,
        )

        assertEquals(6_000L, elapsed.first())
        nowMillis = 17_500L
        assertEquals(13_500L, elapsed.first())
    }
}
