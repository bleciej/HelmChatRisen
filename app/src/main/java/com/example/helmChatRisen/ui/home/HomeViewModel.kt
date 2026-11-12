package com.example.helmChatRisen.ui.home

import androidx.lifecycle.ViewModel
import com.example.helmChatRisen.data.RecentRoom
import com.example.helmChatRisen.jitsi.JitsiCallManager
import com.example.helmChatRisen.jitsi.JitsiServer
import com.example.helmChatRisen.jitsi.JitsiServers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Immutable state of the home / join screen.
 */
data class HomeUiState(
    val roomName: String = "",
    val displayName: String = "",
    val useCustomServer: Boolean = false,
    val selectedServerUrl: String = JitsiCallManager.DEFAULT_SERVER_URL,
    val customServerUrl: String = "",
    val jwtToken: String = "",
) {
    /** Preset currently picked in the server drop-down. */
    val selectedServer: JitsiServer
        get() = JitsiServers.byUrl(selectedServerUrl) ?: JitsiServers.default

    /** Server URL that will actually be used when joining. */
    val effectiveServerUrl: String
        get() = if (useCustomServer) {
            customServerUrl.trim()
        } else {
            selectedServerUrl
        }

    val isServerUrlValid: Boolean
        get() = !useCustomServer ||
            (customServerUrl.isNotBlank() && customServerUrl.none(Char::isWhitespace))

    /** True when the rider can share an invite link for the entered room. */
    val canInvite: Boolean
        get() = roomName.isNotBlank()

    val canJoin: Boolean
        get() = roomName.isNotBlank() && isServerUrlValid

    /**
     * meet.jit.si requires the moderator to authenticate with Google/GitHub/Facebook before
     * a room can be started. The embedded, hidden Jitsi UI cannot show that prompt, so the
     * home screen offers a browser-based workaround instead.
     */
    val isMeetJitSiServer: Boolean
        get() = effectiveServerUrl.contains("meet.jit.si", ignoreCase = true)
}

/**
 * Validated parameters for starting an audio-only call.
 */
data class CallRequest(
    val roomName: String,
    val serverUrl: String,
    val displayName: String?,
    val token: String? = null,
    /** Start with the embedded Jitsi UI visible so the rider can sign in as moderator. */
    val hostSetup: Boolean = false,
)

/**
 * Holds the join form state. The actual call lifecycle is owned by the call layer
 * (JitsiCallManager / AudioCallService) and will be wired in a later step.
 */
class HomeViewModel : ViewModel() {

    private val callManager = JitsiCallManager.getInstance()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun onRoomNameChange(value: String) {
        _uiState.update { it.copy(roomName = value) }
    }

    fun onDisplayNameChange(value: String) {
        _uiState.update { it.copy(displayName = value) }
    }

    fun onUseCustomServerChange(enabled: Boolean) {
        _uiState.update { current ->
            current.copy(
                useCustomServer = enabled,
                customServerUrl = if (enabled) current.customServerUrl else "",
                jwtToken = if (enabled) current.jwtToken else "",
            )
        }
    }

    /** Picks one of the curated public servers from the drop-down. */
    fun onPresetServerSelected(server: JitsiServer) {
        _uiState.update {
            it.copy(
                selectedServerUrl = server.url,
                useCustomServer = false,
                customServerUrl = "",
                jwtToken = "",
            )
        }
    }

    fun onCustomServerUrlChange(value: String) {
        _uiState.update { it.copy(customServerUrl = value) }
    }

    fun onJwtTokenChange(value: String) {
        _uiState.update { it.copy(jwtToken = value) }
    }

    /**
     * Pre-fills the join form with a previously used room, so starting a familiar ride
     * takes a single tap.
     */
    fun applyRecentRoom(room: RecentRoom) {
        val serverUrl = callManager.sanitizeServerUrl(room.serverUrl)
        val preset = JitsiServers.byUrl(serverUrl)
        _uiState.update { current ->
            current.copy(
                roomName = room.roomName,
                displayName = room.displayName.orEmpty(),
                useCustomServer = preset == null,
                selectedServerUrl = preset?.url ?: JitsiServers.default.url,
                customServerUrl = if (preset == null) serverUrl else "",
                jwtToken = "",
            )
        }
    }

    /**
     * Builds a validated [CallRequest] or returns `null` when the form is incomplete.
     *
     * @param hostSetup when true the call screen opens with Jitsi's own UI on top so the
     *   rider can complete the meet.jit.si moderator sign-in flow in-app.
     */
    fun createCallRequest(hostSetup: Boolean = false): CallRequest? {
        val state = _uiState.value
        if (!state.canJoin) return null

        return CallRequest(
            roomName = state.roomName.trim(),
            serverUrl = callManager.sanitizeServerUrl(state.effectiveServerUrl),
            displayName = state.displayName.trim().ifBlank { null },
            token = if (state.useCustomServer) {
                state.jwtToken.trim().ifBlank { null }
            } else {
                null
            },
            hostSetup = hostSetup,
        )
    }
}
