package com.example.helmChatRisen.jitsi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
}
