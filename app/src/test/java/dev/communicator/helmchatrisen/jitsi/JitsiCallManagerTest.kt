package dev.communicator.helmchatrisen.jitsi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.jitsi.meet.sdk.BroadcastEvent

class JitsiCallManagerTest {

    private lateinit var callManager: JitsiCallManager

    @Before
    fun setUp() {
        callManager = JitsiCallManager.getInstance()
        callManager.resetMuteState()
    }

    @Test
    fun defaultServerUrl_isMeetFfmucNet() {
        assertEquals("https://meet.ffmuc.net", JitsiCallManager.DEFAULT_SERVER_URL)
    }

    @Test
    fun isReconnectWatchdogState_coversBothTransientStates() {
        assertTrue(isReconnectWatchdogState(CallState.Reconnecting("room", "https://server")))
        assertTrue(isReconnectWatchdogState(CallState.Connecting("room", "https://server")))
        assertFalse(isReconnectWatchdogState(CallState.Connected("room", "https://server")))
        assertFalse(isReconnectWatchdogState(CallState.Idle))
        assertFalse(isReconnectWatchdogState(CallState.Error(messageResId = 0)))
        assertFalse(isReconnectWatchdogState(CallState.Ending))
    }

    /**
     * A stalled reconnect may replace the view repeatedly while the same room is still live. Only
     * a hang-up, terminal state, different room or missing view stops the retry chain.
     */
    @Test
    fun shouldForceFreshJitsiView_allowsUnboundedRetriesWhileLive() {
        val room = "ride"
        val url = "https://meet.ffmuc.net"

        assertTrue(
            shouldForceFreshJitsiView(
                timedOutRoom = room,
                currentRoomName = room,
                callState = CallState.Reconnecting(room, url),
                isLeaving = false,
                hasActiveView = true,
            ),
        )
        assertTrue(
            shouldForceFreshJitsiView(
                timedOutRoom = room,
                currentRoomName = room,
                callState = CallState.Connecting(room, url),
                isLeaving = false,
                hasActiveView = true,
            ),
        )

        assertFalse(
            shouldForceFreshJitsiView(
                timedOutRoom = room,
                currentRoomName = room,
                callState = CallState.Reconnecting(room, url),
                isLeaving = true,
                hasActiveView = true,
            ),
        )
        assertFalse(
            shouldForceFreshJitsiView(
                timedOutRoom = room,
                currentRoomName = "other",
                callState = CallState.Reconnecting(room, url),
                isLeaving = false,
                hasActiveView = true,
            ),
        )
        assertFalse(
            shouldForceFreshJitsiView(
                timedOutRoom = room,
                currentRoomName = room,
                callState = CallState.Reconnecting(room, url),
                isLeaving = false,
                hasActiveView = false,
            ),
        )
        assertFalse(
            shouldForceFreshJitsiView(
                timedOutRoom = room,
                currentRoomName = room,
                callState = CallState.Connected(room, url),
                isLeaving = false,
                hasActiveView = true,
            ),
        )
    }

    /** A live call remains eligible for repeated view replacements while it keeps stalling. */
    @Test
    fun shouldForceFreshJitsiView_remainsEligibleAcrossRepeatedTimeouts() {
        repeat(times = 5) {
            assertTrue(
                shouldForceFreshJitsiView(
                    timedOutRoom = "ride",
                    currentRoomName = "ride",
                    callState = CallState.Reconnecting("ride", "https://meet.ffmuc.net"),
                    isLeaving = false,
                    hasActiveView = true,
                ),
            )
        }
    }

    /** Offline first joins poll cheaply instead of recreating the native view. */
    @Test
    fun shouldPostponeJoinRetry_waitsOnlyWhileOffline() {
        assertTrue(shouldPostponeJoinRetry(isNetworkAvailable = false))
        assertFalse(shouldPostponeJoinRetry(isNetworkAvailable = true))
    }

    /** Repeated eligible timeouts increment the retry count; settled calls clear it. */
    @Test
    fun reconnectAttemptTracker_recordsOnlyEligibleAttemptsAndResets() {
        val tracker = ReconnectAttemptTracker()
        assertEquals(0, tracker.attempts)

        assertFalse(tracker.record(eligible = false))
        assertEquals(0, tracker.attempts)

        repeat(times = 3) { assertTrue(tracker.record(eligible = true)) }
        assertEquals(3, tracker.attempts)

        tracker.reset()
        assertEquals(0, tracker.attempts)
    }

    /** Callbacks queued by a replaced view must not affect the fresh view. */
    @Test
    fun isCurrentViewEpoch_rejectsCallbacksFromReplacedView() {
        assertTrue(isCurrentViewEpoch(callbackEpoch = 4L, currentEpoch = 4L))
        assertFalse(isCurrentViewEpoch(callbackEpoch = 4L, currentEpoch = 5L))
    }

    /** Only the matching replacement join may keep the timer and connection history. */
    @Test
    fun shouldPreserveCallSessionOnJoin_matchesOnlyThePendingRoom() {
        assertTrue(
            shouldPreserveCallSessionOnJoin(
                isAwaitingRestoredView = true,
                currentRoomName = "ride",
                joiningRoomName = "ride",
            ),
        )
        assertFalse(
            shouldPreserveCallSessionOnJoin(
                isAwaitingRestoredView = false,
                currentRoomName = "ride",
                joiningRoomName = "ride",
            ),
        )
        assertFalse(
            shouldPreserveCallSessionOnJoin(
                isAwaitingRestoredView = true,
                currentRoomName = "ride",
                joiningRoomName = "other",
            ),
        )
        assertFalse(
            shouldPreserveCallSessionOnJoin(
                isAwaitingRestoredView = true,
                currentRoomName = null,
                joiningRoomName = "ride",
            ),
        )
    }

    /** Restoring a default network retries only a still-pending first join. */
    @Test
    fun shouldRetryFirstJoinOnNetworkRestore_onlyForPendingFirstJoin() {
        val connecting = CallState.Connecting("ride", "https://server")

        assertTrue(
            shouldRetryFirstJoinOnNetworkRestore(
                wasOffline = true,
                callState = connecting,
                hasConnectedConference = false,
                hasPendingConnectTimeout = true,
            ),
        )
        assertFalse(
            shouldRetryFirstJoinOnNetworkRestore(
                wasOffline = false,
                callState = connecting,
                hasConnectedConference = false,
                hasPendingConnectTimeout = true,
            ),
        )
        assertFalse(
            shouldRetryFirstJoinOnNetworkRestore(
                wasOffline = true,
                callState = connecting,
                hasConnectedConference = true,
                hasPendingConnectTimeout = true,
            ),
        )
        assertFalse(
            shouldRetryFirstJoinOnNetworkRestore(
                wasOffline = true,
                callState = connecting,
                hasConnectedConference = false,
                hasPendingConnectTimeout = false,
            ),
        )
        assertFalse(
            shouldRetryFirstJoinOnNetworkRestore(
                wasOffline = true,
                callState = CallState.Connected("ride", "https://server"),
                hasConnectedConference = true,
                hasPendingConnectTimeout = false,
            ),
        )
    }

    /** Releasing the pending old view stops normal teardown but only cleans native state when owned. */
    @Test
    fun classifyForcedRejoinViewRelease_keepsTheCallAlive() {
        val ownedPendingRelease = classifyForcedRejoinViewRelease(
            isPendingView = true,
            isActiveView = true,
        )
        assertTrue(ownedPendingRelease.consumeRelease)
        assertTrue(ownedPendingRelease.releaseNativeView)

        val alreadyReplacedRelease = classifyForcedRejoinViewRelease(
            isPendingView = true,
            isActiveView = false,
        )
        assertTrue(alreadyReplacedRelease.consumeRelease)
        assertFalse(alreadyReplacedRelease.releaseNativeView)

        val ordinaryRelease = classifyForcedRejoinViewRelease(
            isPendingView = false,
            isActiveView = true,
        )
        assertFalse(ordinaryRelease.consumeRelease)
        assertFalse(ordinaryRelease.releaseNativeView)
    }

    /** WILL_JOIN keeps the first-join deadline and only hands it over for an actual rejoin. */
    @Test
    fun handsOverJoinDeadlineToRejoin_onlyAfterTheConferenceHasConnected() {
        assertFalse(
            handsOverJoinDeadlineToRejoin(
                type = BroadcastEvent.Type.CONFERENCE_WILL_JOIN,
                callState = CallState.Connecting("ride", "https://server"),
                hasConnectedConference = false,
            ),
        )
        assertTrue(
            handsOverJoinDeadlineToRejoin(
                type = BroadcastEvent.Type.CONFERENCE_WILL_JOIN,
                callState = CallState.Connecting("ride", "https://server"),
                hasConnectedConference = true,
            ),
        )
        assertTrue(
            handsOverJoinDeadlineToRejoin(
                type = BroadcastEvent.Type.CONFERENCE_WILL_JOIN,
                callState = CallState.Reconnecting("ride", "https://server"),
                hasConnectedConference = false,
            ),
        )
        assertFalse(
            handsOverJoinDeadlineToRejoin(
                type = BroadcastEvent.Type.CONFERENCE_JOINED,
                callState = CallState.Connecting("ride", "https://server"),
                hasConnectedConference = true,
            ),
        )
        assertFalse(
            handsOverJoinDeadlineToRejoin(
                type = BroadcastEvent.Type.CONFERENCE_WILL_JOIN,
                callState = CallState.Connected("ride", "https://server"),
                hasConnectedConference = true,
            ),
        )
        assertFalse(
            handsOverJoinDeadlineToRejoin(
                type = BroadcastEvent.Type.CONFERENCE_WILL_JOIN,
                callState = CallState.Idle,
                hasConnectedConference = false,
            ),
        )
    }

    @Test
    fun reconnectTimeOutConstants_outlastJitsiBackoff() {
        assertTrue(JitsiCallManager.RECONNECT_RELOAD_TIMEOUT_MS >= 60_000L)
        assertTrue(JitsiCallManager.REJOIN_COMPLETION_TIMEOUT_MS >= 120_000L)
        assertTrue(JitsiCallManager.CONNECT_RETRY_CHECK_MS in 1_000L..30_000L)
        assertTrue(
            JitsiCallManager.REJOIN_COMPLETION_TIMEOUT_MS >
                JitsiCallManager.RECONNECT_RELOAD_TIMEOUT_MS,
        )
    }

    @Test
    fun resetMuteState_clearsMuteFromPreviousCall() {
        callManager.setMuted(muted = true)
        assertTrue(callManager.isMuted.value)

        callManager.resetMuteState()

        assertFalse(callManager.isMuted.value)
    }

    @Test
    fun sanitizeServerUrl_addsHttpsProtocol_whenMissing() {
        val sanitized = callManager.sanitizeServerUrl("meet.ffmuc.net")
        assertEquals("https://meet.ffmuc.net", sanitized)
    }

    @Test
    fun sanitizeServerUrl_removesTrailingSlash() {
        val sanitized = callManager.sanitizeServerUrl("https://meet.ffmuc.net/")
        assertEquals("https://meet.ffmuc.net", sanitized)
    }

    @Test
    fun sanitizeServerUrl_returnsDefault_whenBlank() {
        val sanitized = callManager.sanitizeServerUrl("   ")
        assertEquals("https://meet.ffmuc.net", sanitized)
    }

    @Test
    fun sanitizeServerUrl_returnsDefault_whenOnlySchemeProvided() {
        assertEquals(JitsiCallManager.DEFAULT_SERVER_URL, callManager.sanitizeServerUrl("https://"))
        assertEquals(JitsiCallManager.DEFAULT_SERVER_URL, callManager.sanitizeServerUrl("http://"))
        assertEquals(JitsiCallManager.DEFAULT_SERVER_URL, callManager.sanitizeServerUrl("https:///"))
    }

    @Test
    fun sanitizeServerUrl_normalizesUppercaseHttpsScheme() {
        val sanitized = callManager.sanitizeServerUrl("HTTPS://MEET.FFMUC.NET/")
        assertEquals("https://meet.ffmuc.net", sanitized)
    }

    @Test
    fun sanitizeServerUrl_upgradesHttpPresetUrlToHttps() {
        assertEquals(
            "https://meet.ffmuc.net",
            callManager.sanitizeServerUrl("http://meet.ffmuc.net"),
        )
        assertEquals(
            "https://meet.ffmuc.net",
            callManager.sanitizeServerUrl("HTTP://MEET.FFMUC.NET/"),
        )
    }

    @Test
    fun sanitizeServerUrl_keepsSchemeOfUnknownServer() {
        assertEquals(
            "http://jitsi.example.com",
            callManager.sanitizeServerUrl("http://jitsi.example.com/"),
        )
        assertEquals(
            "https://moj-serwer.pl",
            callManager.sanitizeServerUrl("https://moj-serwer.pl"),
        )
    }

    /**
     * Pins the intentional difference between the two helpers: a blank server URL falls back to
     * the default preset on the join path, while preset lookup reports "no match".
     */
    @Test
    fun sanitizeServerUrl_defaultsBlankInput_whileByUrlRejectsIt() {
        assertEquals(JitsiCallManager.DEFAULT_SERVER_URL, callManager.sanitizeServerUrl(""))
        assertNull(JitsiServers.byUrl(""))
    }

    @Test
    fun buildConferenceOptions_createsOptionsSuccessfully() {
        val options = callManager.buildConferenceOptions(
            roomName = "TestRideGroup123",
            serverUrl = "https://meet.ffmuc.net",
            displayName = "RiderOne",
            isMuted = false
        )

        assertNotNull(options)
    }

    @Test
    fun buildConferenceOptions_setsJwtToken() {
        val options = callManager.buildConferenceOptions(
            roomName = "TestRideGroup123",
            serverUrl = "https://jitsi.example.com",
            token = "abc.def.ghi",
        )

        assertEquals("abc.def.ghi", options.token)
    }

    @Test
    fun participantIdFrom_readsFlatParticipantIdUsedByCurrentSdk() {
        // Jitsi sends the participant info flattened into the extras, keyed "participantId".
        val data = mapOf(
            "participantId" to "a1b2c3",
            "displayName" to "RiderOne",
            "isLocal" to false,
        )

        assertEquals("a1b2c3", participantIdFrom(data))
    }

    @Test
    fun participantIdFrom_fallsBackToLegacyIdKey() {
        assertEquals("legacy-id", participantIdFrom(mapOf("id" to "legacy-id")))
    }

    @Test
    fun participantIdFrom_ignoresNestedParticipantData() {
        assertNull(participantIdFrom(mapOf("participant" to mapOf("participantId" to "nested-id"))))
        assertEquals(
            "flat-id",
            participantIdFrom(
                mapOf(
                    "participant" to "{\"participantId\":\"nested-id\"}",
                    "id" to "flat-id",
                ),
            ),
        )
    }

    @Test
    fun participantIdFrom_fallsBackWhenPreferredKeyIsInvalid() {
        assertEquals("legacy-id", participantIdFrom(mapOf("participantId" to "", "id" to "legacy-id")))
        assertEquals("legacy-id", participantIdFrom(mapOf("participantId" to "   ", "id" to "legacy-id")))
        assertEquals("legacy-id", participantIdFrom(mapOf("participantId" to "null", "id" to "legacy-id")))
        assertEquals("legacy-id", participantIdFrom(mapOf("participantId" to "undefined", "id" to "legacy-id")))
    }

    @Test
    fun participantIdFrom_rejectsBlankAndStringifiedNulls() {
        assertNull(participantIdFrom(null))
        assertNull(participantIdFrom(emptyMap()))
        assertNull(participantIdFrom(mapOf("participantId" to "")))
        assertNull(participantIdFrom(mapOf("participantId" to "   ")))
        assertNull(participantIdFrom(mapOf("participantId" to "null")))
        assertNull(participantIdFrom(mapOf("participantId" to "undefined")))
    }

    @Test
    fun participantIdFrom_ignoresNonStringCandidates() {
        // A structured extra would stringify into a key such as "Bundle[{...}]".
        assertEquals(
            "flat-id",
            participantIdFrom(
                mapOf(
                    "participantId" to mapOf("participantId" to "nested-id"),
                    "id" to "flat-id",
                ),
            ),
        )
        assertNull(participantIdFrom(mapOf("participantId" to 42)))
    }

    @Test
    fun participantIdFrom_acceptsCharSequenceCandidates() {
        assertEquals("built-id", participantIdFrom(mapOf("participantId" to StringBuilder("built-id"))))
    }

    @Test
    fun applyParticipantSnapshot_replacesBaselineWithSnapshot() {
        assertEquals(
            setOf("a", "b"),
            applyParticipantSnapshot(
                current = setOf("stale"),
                snapshot = setOf("a", "b"),
                pendingEvents = emptyList(),
            ),
        )
    }

    @Test
    fun applyParticipantSnapshot_replaysBufferedJoin() {
        assertEquals(
            setOf("a", "late"),
            applyParticipantSnapshot(
                current = setOf("a"),
                snapshot = setOf("a"),
                pendingEvents = listOf("late" to true),
            ),
        )
    }

    @Test
    fun applyParticipantSnapshot_doesNotResurrectBufferedLeave() {
        assertEquals(
            setOf("a"),
            applyParticipantSnapshot(
                current = setOf("a", "gone"),
                snapshot = setOf("a", "gone"),
                pendingEvents = listOf("gone" to false),
            ),
        )
    }

    @Test
    fun applyParticipantSnapshot_keepsBaselineWhenSnapshotIsEmpty() {
        assertEquals(
            setOf("a"),
            applyParticipantSnapshot(
                current = setOf("a"),
                snapshot = emptySet(),
                pendingEvents = emptyList(),
            ),
        )
    }

    @Test
    fun sanitizeParticipantIds_dropsNullBlankAndStringifiedNulls() {
        assertEquals(setOf("a", "b"), sanitizeParticipantIds(listOf("a", null, "", "   ", "b")))
        assertEquals(
            setOf("a"),
            sanitizeParticipantIds(listOf("a", null, "", "  ", "null", "undefined", "NULL")),
        )
        assertEquals(emptySet<String>(), sanitizeParticipantIds(emptyList()))
    }

    /**
     * Keeps the snapshot path and the broadcast path on the same definition of a valid id.
     */
    @Test
    fun sanitizeParticipantIds_matchesTheBroadcastValidation() {
        val candidates = listOf("a", null, "", " ", "null", "undefined", "id-1")

        assertEquals(
            candidates.mapNotNull { participantIdFrom(mapOf("participantId" to it)) }.toSet(),
            sanitizeParticipantIds(candidates),
        )
    }

    @Test
    fun isParticipantSessionLive_requiresAnActiveRoom() {
        assertTrue(isParticipantSessionLive("ride", isLeaving = false, callState = CallState.Connecting("ride", "url")))
        assertTrue(isParticipantSessionLive("ride", isLeaving = false, callState = CallState.Connected("ride", "url")))
        assertTrue(isParticipantSessionLive("ride", isLeaving = false, callState = CallState.Reconnecting("ride", "url")))

        assertFalse(isParticipantSessionLive(null, isLeaving = false, callState = CallState.Connected("ride", "url")))
        assertFalse(isParticipantSessionLive("ride", isLeaving = true, callState = CallState.Connected("ride", "url")))
        assertFalse(isParticipantSessionLive("ride", isLeaving = false, callState = CallState.Idle))
        assertFalse(isParticipantSessionLive("ride", isLeaving = false, callState = CallState.Ending))
        assertFalse(isParticipantSessionLive(null, isLeaving = true, callState = CallState.Idle))
    }

    @Test
    fun isHangUpConfirmation_acceptsOnlyJitsiTeardownEvents() {
        assertTrue(isHangUpConfirmation(BroadcastEvent.Type.CONFERENCE_TERMINATED))
        assertTrue(isHangUpConfirmation(BroadcastEvent.Type.READY_TO_CLOSE))

        assertFalse(isHangUpConfirmation(BroadcastEvent.Type.CONFERENCE_WILL_JOIN))
        assertFalse(isHangUpConfirmation(BroadcastEvent.Type.CONFERENCE_JOINED))
        assertFalse(isHangUpConfirmation(BroadcastEvent.Type.AUDIO_MUTED_CHANGED))
        assertFalse(isHangUpConfirmation(BroadcastEvent.Type.PARTICIPANT_JOINED))
        assertFalse(isHangUpConfirmation(BroadcastEvent.Type.PARTICIPANT_LEFT))
    }

    @Test
    fun isParticipantSnapshotCurrent_rejectsOlderSessionOrRequest() {
        assertTrue(isParticipantSnapshotCurrent(session = 1, currentSession = 1, requestId = 2, latestRequestId = 2))
        assertFalse(isParticipantSnapshotCurrent(session = 1, currentSession = 2, requestId = 2, latestRequestId = 2))
        assertFalse(isParticipantSnapshotCurrent(session = 1, currentSession = 1, requestId = 1, latestRequestId = 2))
    }

    /** A stale termination from a previous conference must not touch a fresh join attempt. */
    @Test
    fun shouldIgnoreTerminationBeforeJoin_ignoresEventsUntilCurrentConferenceJoins() {
        val termination = BroadcastEvent.Type.CONFERENCE_TERMINATED
        val joined = BroadcastEvent.Type.CONFERENCE_JOINED

        assertTrue(shouldIgnoreTerminationBeforeJoin(isLeaving = false, hasConferenceConfirmation = false, type = termination))
        assertFalse(shouldIgnoreTerminationBeforeJoin(isLeaving = false, hasConferenceConfirmation = true, type = termination))
        assertFalse(shouldIgnoreTerminationBeforeJoin(isLeaving = true, hasConferenceConfirmation = false, type = termination))
        assertFalse(shouldIgnoreTerminationBeforeJoin(isLeaving = false, hasConferenceConfirmation = false, type = joined))
    }

    /** Timers and callbacks from an older teardown generation must be inert. */
    @Test
    fun isCurrentTeardown_acceptsOnlyTheActiveHangUp() {
        val ending = CallState.Ending
        val idle = CallState.Idle

        assertTrue(isCurrentTeardown(token = 7, currentToken = 7, isLeaving = true, callState = ending))
        assertFalse(isCurrentTeardown(token = 6, currentToken = 7, isLeaving = true, callState = ending))
        assertFalse(isCurrentTeardown(token = 7, currentToken = 7, isLeaving = false, callState = ending))
        assertFalse(isCurrentTeardown(token = 7, currentToken = 7, isLeaving = true, callState = idle))
    }

    /** Mute commands are accepted in normal calls and ignored during the ending phase. */
    @Test
    fun canChangeMute_blocksOnlyDuringEnding() {
        val room = "ride"
        val url = "https://meet.ffmuc.net"

        assertTrue(canChangeMute(isLeaving = false, callState = CallState.Connecting(room, url)))
        assertTrue(canChangeMute(isLeaving = false, callState = CallState.Connected(room, url)))
        assertTrue(canChangeMute(isLeaving = false, callState = CallState.Idle))
        assertFalse(canChangeMute(isLeaving = false, callState = CallState.Ending))
        assertFalse(canChangeMute(isLeaving = true, callState = CallState.Connected(room, url)))
    }

    /** A failed or finished call must never be revived by a late Jitsi termination event. */
    @Test
    fun terminalCallState_coversFailureAndFinishedStates() {
        assertTrue(isTerminalCallState(CallState.Idle))
        assertTrue(isTerminalCallState(CallState.Ending))
        assertTrue(isTerminalCallState(CallState.Error(messageResId = 0)))

        assertFalse(isTerminalCallState(CallState.Connecting("ride", "https://server")))
        assertFalse(isTerminalCallState(CallState.Connected("ride", "https://server")))
        assertFalse(isTerminalCallState(CallState.Reconnecting("ride", "https://server")))
    }

    /** The error must stay visible long enough to read, but not block a new call indefinitely. */
    @Test
    fun errorDisplayTimeout_isBounded() {
        assertTrue(JitsiCallManager.ERROR_DISPLAY_TIMEOUT_MS >= 2_000L)
        assertTrue(JitsiCallManager.ERROR_DISPLAY_TIMEOUT_MS <= 10_000L)
    }

    /** A manager that has not joined a conference exposes no start timestamp. */
    @Test
    fun callStartedAtMillis_isUnsetBeforeJoin() {
        assertEquals(NO_CALL_STARTED_MILLIS, callManager.callStartedAtMillis.value)
    }

    /** The timestamp is absent until the first confirmed join. */
    @Test
    fun callStartTracker_startsWithoutTimestamp() {
        val tracker = CallStartTracker { 1_000L }

        assertEquals(NO_CALL_STARTED_MILLIS, tracker.startedAtMillis.value)
    }

    /** Only the first confirmed join is recorded; a reconnect keeps the original timestamp. */
    @Test
    fun callStartTracker_recordsFirstJoinAndPreservesReconnects() {
        var nowMillis = 1_000L
        val tracker = CallStartTracker { nowMillis }

        tracker.onConferenceJoined()
        assertEquals(1_000L, tracker.startedAtMillis.value)

        nowMillis = 9_000L
        tracker.onConferenceJoined()
        assertEquals(1_000L, tracker.startedAtMillis.value)
    }

    /** Join, teardown and error paths reuse the same reset so a later call starts fresh. */
    @Test
    fun callStartTracker_resetClearsTimestampForNextCall() {
        val tracker = CallStartTracker { 5_000L }

        tracker.onConferenceJoined()
        tracker.reset()
        assertEquals(NO_CALL_STARTED_MILLIS, tracker.startedAtMillis.value)

        tracker.onConferenceJoined()
        assertEquals(5_000L, tracker.startedAtMillis.value)
    }
}
