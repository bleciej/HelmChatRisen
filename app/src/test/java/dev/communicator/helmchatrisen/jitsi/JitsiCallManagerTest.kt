package dev.communicator.helmchatrisen.jitsi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class JitsiCallManagerTest {

    private lateinit var callManager: JitsiCallManager

    @Before
    fun setUp() {
        callManager = JitsiCallManager.getInstance()
    }

    @Test
    fun defaultServerUrl_isMeetFfmucNet() {
        assertEquals("https://meet.ffmuc.net", JitsiCallManager.DEFAULT_SERVER_URL)
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
        // Jitsi 10.x sends the participant info flattened into the extras, keyed "participantId".
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
        assertFalse(isParticipantSessionLive(null, isLeaving = true, callState = CallState.Idle))
    }

    @Test
    fun isParticipantSnapshotCurrent_rejectsOlderSessionOrRequest() {
        assertTrue(isParticipantSnapshotCurrent(session = 1, currentSession = 1, requestId = 2, latestRequestId = 2))
        assertFalse(isParticipantSnapshotCurrent(session = 1, currentSession = 2, requestId = 2, latestRequestId = 2))
        assertFalse(isParticipantSnapshotCurrent(session = 1, currentSession = 1, requestId = 1, latestRequestId = 2))
    }
}
