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
}
