package com.example.helmChatRisen.jitsi

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Deadline applied to a participant snapshot request that the SDK never answers. */
internal const val PARTICIPANT_SNAPSHOT_TIMEOUT_MS = 5_000L

/** Handle returned by [SnapshotTimeoutScheduler.schedule] so a pending deadline can be cancelled. */
internal fun interface ScheduledTimeout {
    fun cancel()
}

/**
 * Schedules the one-shot deadline that bounds a participant snapshot request.
 *
 * Production backs this with the main looper; tests inject a fake so no real delay is needed.
 */
internal fun interface SnapshotTimeoutScheduler {
    fun schedule(delayMs: Long, action: () -> Unit): ScheduledTimeout
}

/**
 * Owns the participant bookkeeping of the active call: the remote riders in the room, whether the
 * local rider is present, and the join/leave events observed while an authoritative snapshot is
 * in flight.
 *
 * [JitsiCallManager] forwards broadcast events and `ParticipantsService` callbacks here; all
 * methods must be called on the main thread. The count exposed through [count] is always derived
 * from that state in [publishCount], so no caller can publish a value that disagrees with it.
 *
 * A snapshot request is bounded by [snapshotTimeoutMs]: the SDK's request is fire-and-forget, so
 * an unanswered request must fall back to the broadcast stream instead of buffering events
 * forever. Only one request may be in flight; a duplicate request is refused and leaves the
 * buffered events untouched.
 */
internal class ParticipantTracker(
    private val scheduler: SnapshotTimeoutScheduler,
    private val snapshotTimeoutMs: Long = PARTICIPANT_SNAPSHOT_TIMEOUT_MS,
    private val onSnapshotSettled: (timedOut: Boolean) -> Unit = {},
) {

    /** Identifies one snapshot request. A response is only applied while it is the pending one. */
    data class SnapshotRequest(val session: Long, val requestId: Long)

    private val remoteParticipantIds = mutableSetOf<String>()
    private val pendingEvents = mutableListOf<Pair<String, Boolean>>()

    private var session = 0L
    private var latestRequestId = 0L
    private var pendingRequest: SnapshotRequest? = null
    private var pendingTimeout: ScheduledTimeout? = null
    private var localRiderPresent = false
    private var localParticipantId: String? = null

    private val _count = MutableStateFlow(0)

    /** Number of riders in the room, including the local rider. */
    val count: StateFlow<Int> = _count.asStateFlow()

    /**
     * Marks the local rider as present. Called on `CONFERENCE_JOINED`, which is the only
     * reliable signal that the local rider has entered the room, and is idempotent so a
     * duplicate `CONFERENCE_JOINED` (Jitsi reload) cannot inflate the count.
     */
    fun onConferenceJoined() {
        localRiderPresent = true
        publishCount()
    }

    /**
     * Records a `PARTICIPANT_JOINED` event.
     *
     * [isLocal] must be `true` for the local rider so presence is tracked as a flag instead of an
     * id; participants are otherwise buffered while a snapshot is in flight.
     */
    fun onJoined(id: String, isLocal: Boolean = false) {
        if (isLocal || id == localParticipantId) {
            localRiderPresent = true
            publishCount()
            return
        }
        if (pendingRequest != null) {
            pendingEvents += id to true
            return
        }
        remoteParticipantIds.add(id)
        publishCount()
    }

    /** Records a `PARTICIPANT_LEFT` event. Mirrors [onJoined]. */
    fun onLeft(id: String, isLocal: Boolean = false) {
        if (isLocal || id == localParticipantId) {
            localRiderPresent = false
            publishCount()
            return
        }
        if (pendingRequest != null) {
            pendingEvents += id to false
            return
        }
        remoteParticipantIds.remove(id)
        publishCount()
    }

    /**
     * Starts a snapshot request and arms its deadline.
     *
     * Returns `null` when a request is already in flight: the pending snapshot already covers
     * that window, and starting a second one would discard the events buffered for the first.
     */
    fun onSnapshotRequested(): SnapshotRequest? {
        if (pendingRequest != null) return null

        val request = SnapshotRequest(session = session, requestId = ++latestRequestId)
        pendingRequest = request
        pendingTimeout = scheduler.schedule(snapshotTimeoutMs) { onSnapshotTimeout(request) }
        return request
    }

    /**
     * Applies an authoritative snapshot and replays the events that arrived while it was in
     * flight. [localParticipantId] is the id of the local rider when the payload exposes it; the
     * SDK snapshot always contains that participant, so the local rider is never double counted.
     */
    fun onSnapshotReceived(
        request: SnapshotRequest,
        snapshotIds: Set<String>,
        localParticipantId: String? = null,
    ) {
        if (!isCurrent(request)) return
        settleRequest(timedOut = false)

        if (localParticipantId != null) {
            this.localParticipantId = localParticipantId
        }

        val knownLocalId = this.localParticipantId
        val authoritative = snapshotIds.isNotEmpty()
        if (authoritative) {
            // The snapshot lists everyone in the room, so the local rider is present whenever
            // its id could be separated from the remote ids below.
            localRiderPresent = knownLocalId != null
        }

        val remotes = if (knownLocalId != null) snapshotIds - knownLocalId else snapshotIds
        applySnapshot(remoteSnapshot = if (authoritative) remotes else emptySet(), authoritative)
    }

    /**
     * Ends a request that failed before it could produce a response. The buffered events are
     * replayed instead of discarded, so an observed join or leave is never lost.
     */
    fun onSnapshotFailed(request: SnapshotRequest) {
        if (!isCurrent(request)) return
        settleRequest(timedOut = false)
        applySnapshot(remoteSnapshot = emptySet(), authoritative = false)
    }

    /** Discards all participant state for the finished join attempt. */
    fun reset() {
        pendingTimeout?.cancel()
        pendingTimeout = null
        pendingRequest = null
        pendingEvents.clear()
        remoteParticipantIds.clear()
        localRiderPresent = false
        localParticipantId = null
        session += 1
        latestRequestId += 1
        publishCount()
    }

    private fun onSnapshotTimeout(request: SnapshotRequest) {
        if (!isCurrent(request)) return
        settleRequest(timedOut = true)
        applySnapshot(remoteSnapshot = emptySet(), authoritative = false)
    }

    private fun settleRequest(timedOut: Boolean) {
        pendingTimeout?.cancel()
        pendingTimeout = null
        pendingRequest = null
        onSnapshotSettled(timedOut)
    }

    /**
     * Replaces the baseline with [remoteSnapshot] when the snapshot was authoritative, and keeps
     * the current baseline otherwise, then replays the buffered events on top.
     */
    private fun applySnapshot(remoteSnapshot: Set<String>, authoritative: Boolean) {
        val baseline = if (authoritative) remoteSnapshot else remoteParticipantIds
        val merged = applyParticipantSnapshot(baseline, remoteSnapshot, pendingEvents)
        pendingEvents.clear()
        remoteParticipantIds.clear()
        remoteParticipantIds.addAll(merged)
        publishCount()
    }

    private fun isCurrent(request: SnapshotRequest): Boolean {
        if (pendingRequest != request) return false
        return isParticipantSnapshotCurrent(
            session = request.session,
            currentSession = session,
            requestId = request.requestId,
            latestRequestId = latestRequestId,
        )
    }

    private fun publishCount() {
        _count.value = remoteParticipantIds.size + if (localRiderPresent) 1 else 0
    }
}
