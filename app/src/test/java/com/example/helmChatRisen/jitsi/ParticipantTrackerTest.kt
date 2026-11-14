package com.example.helmChatRisen.jitsi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Covers the participant state machine that used to be private to [JitsiCallManager]: the
 * pending-snapshot window, the buffered events, the deadline and the local-rider accounting.
 */
class ParticipantTrackerTest {

    private lateinit var scheduler: FakeScheduler
    private lateinit var tracker: ParticipantTracker

    private val count: Int get() = tracker.count.value

    @Before
    fun setUp() {
        scheduler = FakeScheduler()
        tracker = ParticipantTracker(scheduler = scheduler)
    }

    @Test
    fun startsWithoutParticipants() {
        assertEquals(0, count)
    }

    @Test
    fun conferenceJoined_countsTheLocalRider() {
        tracker.onConferenceJoined()

        assertEquals(1, count)
    }

    @Test
    fun duplicateConferenceJoined_doesNotInflateTheCount() {
        tracker.onConferenceJoined()
        tracker.onConferenceJoined()

        assertEquals(1, count)
    }

    @Test
    fun remoteJoinAndLeave_areAppliedImmediatelyWithoutARequest() {
        tracker.onConferenceJoined()

        tracker.onJoined("rider-a")
        assertEquals(2, count)

        tracker.onLeft("rider-a")
        assertEquals(1, count)
    }

    @Test
    fun localJoinEvent_doesNotInflateTheCount() {
        tracker.onConferenceJoined()

        tracker.onJoined("me", isLocal = true)

        assertEquals(1, count)
    }

    @Test
    fun joinIsBufferedWhileSnapshotIsPending_andReplayedOnResponse() {
        tracker.onConferenceJoined()
        val request = requestSnapshot()

        tracker.onJoined("rider-a")
        assertEquals(1, count)

        tracker.onSnapshotReceived(
            request = request,
            snapshotIds = setOf("local", "rider-a"),
            localParticipantId = "local",
        )
        assertEquals(2, count)
    }

    @Test
    fun bufferedJoinIsReplayedWhenRequestFails() {
        tracker.onConferenceJoined()
        val request = requestSnapshot()

        tracker.onJoined("rider-a")
        tracker.onSnapshotFailed(request)

        assertEquals(2, count)
    }

    @Test
    fun bufferedLeaveIsReplayedWhenRequestFails() {
        tracker.onConferenceJoined()
        tracker.onJoined("rider-a")
        val request = requestSnapshot()

        tracker.onLeft("rider-a")
        assertEquals(2, count)

        tracker.onSnapshotFailed(request)
        assertEquals(1, count)
    }

    @Test
    fun snapshotIncludingLocalRider_isNotDoubleCounted() {
        tracker.onConferenceJoined()
        val request = requestSnapshot()

        tracker.onSnapshotReceived(
            request = request,
            snapshotIds = setOf("local", "rider-a"),
            localParticipantId = "local",
        )

        assertEquals(2, count)
    }

    @Test
    fun snapshotWithoutLocalId_isCountedAsAWhole() {
        tracker.onConferenceJoined()
        val request = requestSnapshot()

        tracker.onSnapshotReceived(
            request = request,
            snapshotIds = setOf("local", "rider-a"),
            localParticipantId = null,
        )

        assertEquals(2, count)
    }

    @Test
    fun emptySnapshot_keepsTheCurrentBaseline() {
        tracker.onConferenceJoined()
        tracker.onJoined("rider-a")
        val request = requestSnapshot()

        tracker.onSnapshotReceived(request = request, snapshotIds = emptySet())

        assertEquals(2, count)
    }

    @Test
    fun snapshotWithOnlyTheLocalRider_clearsStaleRemotes() {
        tracker.onConferenceJoined()
        tracker.onJoined("rider-a")
        val request = requestSnapshot()

        tracker.onSnapshotReceived(
            request = request,
            snapshotIds = setOf("local"),
            localParticipantId = "local",
        )

        assertEquals(1, count)
    }

    @Test
    fun armedDeadline_usesTheConfiguredTimeout() {
        requestSnapshot()

        assertEquals(PARTICIPANT_SNAPSHOT_TIMEOUT_MS, scheduler.lastDelayMs)
    }

    @Test
    fun timeout_flushesBufferedEventsAndEndsTheRequest() {
        tracker.onConferenceJoined()
        requestSnapshot()

        tracker.onJoined("rider-a")
        scheduler.advance()

        assertEquals(2, count)
        assertFalse(scheduler.hasPendingTimeouts)
    }

    @Test
    fun responseAfterTimeout_isIgnored() {
        tracker.onConferenceJoined()
        val request = requestSnapshot()
        scheduler.advance()

        tracker.onSnapshotReceived(request = request, snapshotIds = setOf("late"))

        assertEquals(1, count)
    }

    @Test
    fun responseAfterReset_isIgnored() {
        tracker.onConferenceJoined()
        val request = requestSnapshot()

        tracker.reset()
        tracker.onSnapshotReceived(request = request, snapshotIds = setOf("rider-a"))

        assertEquals(0, count)
        assertFalse(scheduler.hasPendingTimeouts)
    }

    @Test
    fun resetCancelsTheDeadline() {
        requestSnapshot()

        tracker.reset()

        assertFalse(scheduler.hasPendingTimeouts)
        assertEquals(0, count)
    }

    @Test
    fun secondRequestIsRefusedWhileOneIsInFlight() {
        requestSnapshot()

        assertNull(tracker.onSnapshotRequested())
    }

    @Test
    fun repeatedResponseForTheSameRequest_isIgnored() {
        tracker.onConferenceJoined()
        val request = requestSnapshot()

        tracker.onSnapshotReceived(
            request = request,
            snapshotIds = setOf("local", "rider-a"),
            localParticipantId = "local",
        )
        assertEquals(2, count)

        tracker.onSnapshotReceived(
            request = request,
            snapshotIds = setOf("local"),
            localParticipantId = "local",
        )

        assertEquals(2, count)
    }

    @Test
    fun settledCallback_reportsWhetherTheRequestTimedOut() {
        val trackedScheduler = FakeScheduler()
        val outcomes = mutableListOf<Boolean>()
        tracker = ParticipantTracker(
            scheduler = trackedScheduler,
            onSnapshotSettled = { timedOut -> outcomes += timedOut },
        )

        val answered = requestSnapshot()
        tracker.onSnapshotReceived(request = answered, snapshotIds = emptySet())
        requestSnapshot()
        trackedScheduler.advance()

        assertEquals(listOf(false, true), outcomes)
    }

    @Test
    fun reset_forgetsTheLocalIdSoANewCallStartsFresh() {
        tracker.onConferenceJoined()
        val request = requestSnapshot()
        tracker.onSnapshotReceived(
            request = request,
            snapshotIds = setOf("me"),
            localParticipantId = "me",
        )

        tracker.reset()
        tracker.onConferenceJoined()
        tracker.onJoined("me")

        assertEquals(2, count)
    }

    @Test
    fun localRiderLeft_removesItFromTheCount() {
        tracker.onConferenceJoined()
        tracker.onJoined("rider-a")

        tracker.onLeft("me", isLocal = true)

        assertEquals(1, count)
    }

    private fun requestSnapshot(): ParticipantTracker.SnapshotRequest =
        requireNotNull(tracker.onSnapshotRequested()) { "No snapshot request was issued" }

    private class FakeScheduler : SnapshotTimeoutScheduler {
        private val timeouts = mutableListOf<Pair<Long, () -> Unit>>()

        var lastDelayMs: Long? = null
            private set

        val hasPendingTimeouts: Boolean get() = timeouts.isNotEmpty()

        override fun schedule(delayMs: Long, action: () -> Unit): ScheduledTimeout {
            lastDelayMs = delayMs
            val entry = delayMs to action
            timeouts += entry
            return ScheduledTimeout { timeouts.remove(entry) }
        }

        fun advance() {
            val due = timeouts.toList()
            timeouts.clear()
            due.forEach { (_, action) -> action() }
        }
    }
}
