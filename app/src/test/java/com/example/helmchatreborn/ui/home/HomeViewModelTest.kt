package com.example.helmchatreborn.ui.home

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
    fun initialState_usesDefaultServer() {
        val state = viewModel.uiState.value
        assertTrue(state.useDefaultServer)
        assertEquals("https://meet.ffmuc.net", state.effectiveServerUrl)
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
    fun customServer_withWhitespace_isInvalid() {
        viewModel.onRoomNameChange("ride")
        viewModel.onUseDefaultServerChange(false)
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
        viewModel.onUseDefaultServerChange(false)
        viewModel.onCustomServerUrlChange("moj-serwer.pl/")

        val request = viewModel.createCallRequest()

        assertEquals("https://moj-serwer.pl", request?.serverUrl)
    }

    @Test
    fun createCallRequest_returnsNullForIncompleteForm() {
        assertNull(viewModel.createCallRequest())
    }
}
