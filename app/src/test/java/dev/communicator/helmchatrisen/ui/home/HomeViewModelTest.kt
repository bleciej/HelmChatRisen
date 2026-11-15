package dev.communicator.helmchatrisen.ui.home

import dev.communicator.helmchatrisen.data.RecentRoom
import dev.communicator.helmchatrisen.jitsi.JitsiServers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HomeViewModelTest {

    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        viewModel = HomeViewModel()
    }

    @Test
    fun initialState_usesDefaultPreset() {
        val state = viewModel.uiState.value
        assertFalse(state.useCustomServer)
        assertEquals("https://meet.ffmuc.net", state.effectiveServerUrl)
        assertEquals("meet.ffmuc.net", state.selectedServer.host)
        assertFalse(state.canJoin)
    }

    @Test
    fun canJoin_requiresRoomName() {
        viewModel.onRoomNameChange("   ")
        assertFalse(viewModel.uiState.value.canJoin)

        viewModel.onRoomNameChange("niedzielna-przejezdzajka")
        assertTrue(viewModel.uiState.value.canJoin)
    }

    @Test
    fun canInvite_requiresRoomName() {
        assertFalse(viewModel.uiState.value.canInvite)

        viewModel.onRoomNameChange("   ")
        assertFalse(viewModel.uiState.value.canInvite)

        viewModel.onRoomNameChange("niedzielna-przejezdzajka")
        assertTrue(viewModel.uiState.value.canInvite)
    }

    @Test
    fun customServer_withWhitespace_isInvalid() {
        viewModel.onRoomNameChange("ride")
        viewModel.onUseCustomServerChange(true)
        viewModel.onCustomServerUrlChange("https://moj serwer.pl")

        assertFalse(viewModel.uiState.value.isServerUrlValid)
        assertFalse(viewModel.uiState.value.canJoin)
    }

    @Test
    fun createCallRequest_usesDefaultServerAndTrimsValues() {
        viewModel.onRoomNameChange("  ride  ")
        viewModel.onDisplayNameChange("  Burt  ")

        val request = viewModel.createCallRequest()

        assertEquals("ride", request?.roomName)
        assertEquals("https://meet.ffmuc.net", request?.serverUrl)
        assertEquals("Burt", request?.displayName)
    }

    @Test
    fun createCallRequest_sanitizesCustomServer() {
        viewModel.onRoomNameChange("ride")
        viewModel.onUseCustomServerChange(true)
        viewModel.onCustomServerUrlChange("moj-serwer.pl/")

        val request = viewModel.createCallRequest()

        assertEquals("https://moj-serwer.pl", request?.serverUrl)
    }

    @Test
    fun createCallRequest_returnsNullForIncompleteForm() {
        assertNull(viewModel.createCallRequest())
    }

    @Test
    fun applyRecentRoom_fillsFormWithDefaultServer() {
        viewModel.applyRecentRoom(
            RecentRoom(
                roomName = "niedzielna-przejezdzajka",
                serverUrl = "https://meet.ffmuc.net",
                displayName = "Burt",
            ),
        )

        val state = viewModel.uiState.value
        assertEquals("niedzielna-przejezdzajka", state.roomName)
        assertEquals("Burt", state.displayName)
        assertFalse(state.useCustomServer)
        assertEquals("https://meet.ffmuc.net", state.selectedServerUrl)
        assertEquals("", state.customServerUrl)
        assertTrue(state.canJoin)
    }

    @Test
    fun applyRecentRoom_usesCustomServerWhenDifferentFromDefault() {
        viewModel.applyRecentRoom(
            RecentRoom(
                roomName = "ride",
                serverUrl = "https://moj-serwer.pl/",
            ),
        )

        val state = viewModel.uiState.value
        assertTrue(state.useCustomServer)
        assertEquals("https://moj-serwer.pl", state.customServerUrl)
        assertEquals("https://moj-serwer.pl", state.effectiveServerUrl)
    }

    @Test
    fun applyRecentRoom_clearsJwtToken() {
        viewModel.onUseCustomServerChange(true)
        viewModel.onJwtTokenChange("secret-token")

        viewModel.applyRecentRoom(
            RecentRoom(roomName = "ride", serverUrl = "https://moj-serwer.pl"),
        )

        assertEquals("", viewModel.uiState.value.jwtToken)
    }

    @Test
    fun createCallRequest_includesJwtTokenForCustomServer() {
        viewModel.onRoomNameChange("ride")
        viewModel.onUseCustomServerChange(true)
        viewModel.onCustomServerUrlChange("https://jitsi.example.com")
        viewModel.onJwtTokenChange("  abc.def.ghi  ")

        val request = viewModel.createCallRequest()

        assertEquals("https://jitsi.example.com", request?.serverUrl)
        assertEquals("abc.def.ghi", request?.token)
    }

    @Test
    fun createCallRequest_ignoresJwtTokenForDefaultServer() {
        viewModel.onRoomNameChange("ride")
        viewModel.onJwtTokenChange("should-not-be-sent")

        assertNull(viewModel.createCallRequest()?.token)
    }

    @Test
    fun createCallRequest_defaultsToParticipantMode() {
        viewModel.onRoomNameChange("ride")

        assertFalse(viewModel.createCallRequest()!!.hostSetup)
    }

    @Test
    fun createCallRequest_marksHostSetupWhenRequested() {
        viewModel.onRoomNameChange("ride")

        assertTrue(viewModel.createCallRequest(hostSetup = true)!!.hostSetup)
    }

    @Test
    fun meetJitSiServer_isDetected() {
        viewModel.onUseCustomServerChange(true)
        viewModel.onCustomServerUrlChange("meet.jit.si")

        assertTrue(viewModel.uiState.value.isMeetJitSiServer)

        viewModel.onCustomServerUrlChange("https://meet.ffmuc.net")
        assertFalse(viewModel.uiState.value.isMeetJitSiServer)
    }

    @Test
    fun selectingPreset_updatesEffectiveServer() {
        val systemli = JitsiServers.byUrl("https://meet.systemli.org")
        assertTrue(systemli != null)

        viewModel.onPresetServerSelected(systemli!!)

        val state = viewModel.uiState.value
        assertFalse(state.useCustomServer)
        assertEquals("https://meet.systemli.org", state.effectiveServerUrl)
        assertEquals("meet.systemli.org", state.selectedServer.host)
    }

    @Test
    fun selectingPreset_clearsCustomServerAndToken() {
        viewModel.onUseCustomServerChange(true)
        viewModel.onCustomServerUrlChange("https://moj-serwer.pl")
        viewModel.onJwtTokenChange("secret-token")

        viewModel.onPresetServerSelected(JitsiServers.byUrl("https://fairmeeting.net")!!)

        val state = viewModel.uiState.value
        assertEquals("", state.customServerUrl)
        assertEquals("", state.jwtToken)
        assertEquals("https://fairmeeting.net", state.effectiveServerUrl)
    }

    @Test
    fun applyRecentRoom_selectsMatchingPreset() {
        viewModel.applyRecentRoom(
            RecentRoom(roomName = "ride", serverUrl = "https://meet.systemli.org/"),
        )

        val state = viewModel.uiState.value
        assertFalse(state.useCustomServer)
        assertEquals("https://meet.systemli.org", state.selectedServerUrl)
        assertEquals("", state.customServerUrl)
    }

    @Test
    fun applyRecentRoom_mapsHttpPresetEntryOntoPreset() {
        viewModel.applyRecentRoom(
            RecentRoom(roomName = "ride", serverUrl = "http://meet.ffmuc.net"),
        )

        val state = viewModel.uiState.value
        assertFalse(state.useCustomServer)
        assertEquals("https://meet.ffmuc.net", state.selectedServerUrl)
        assertEquals("https://meet.ffmuc.net", state.effectiveServerUrl)
        assertEquals("", state.customServerUrl)
    }

    @Test
    fun createCallRequest_usesSelectedPreset() {
        viewModel.onRoomNameChange("ride")
        viewModel.onPresetServerSelected(JitsiServers.byUrl("https://webex.muc.ccc.de")!!)

        val request = viewModel.createCallRequest()

        assertEquals("https://webex.muc.ccc.de", request?.serverUrl)
        assertNull(request?.token)
    }

    @Test
    fun presets_containAllAdvertisedServers() {
        val hosts = JitsiServers.presets.map { it.host }

        assertEquals(
            listOf(
                "meet.ffmuc.net",
                "freejitsi01.netcup.net",
                "webex.muc.ccc.de",
                "fairmeeting.net",
                "meet.systemli.org",
                "meet.jit.si",
            ),
            hosts,
        )
        assertEquals("meet.ffmuc.net", JitsiServers.default.host)
    }
}
