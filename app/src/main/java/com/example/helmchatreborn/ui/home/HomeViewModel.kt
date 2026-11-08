package com.example.helmchatreborn.ui.home

import androidx.lifecycle.ViewModel
import com.example.helmchatreborn.jitsi.JitsiCallManager
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
    val useDefaultServer: Boolean = true,
    val customServerUrl: String = "",
) {
    /** Server URL that will actually be used when joining. */
    val effectiveServerUrl: String
        get() = if (useDefaultServer) {
            JitsiCallManager.DEFAULT_SERVER_URL
        } else {
            customServerUrl.trim()
        }

    val isServerUrlValid: Boolean
        get() = useDefaultServer ||
            (customServerUrl.isNotBlank() && customServerUrl.none(Char::isWhitespace))

    val canJoin: Boolean
        get() = roomName.isNotBlank() && isServerUrlValid
}

/**
 * Validated parameters for starting an audio-only call.
 */
data class CallRequest(
    val roomName: String,
    val serverUrl: String,
    val displayName: String?,
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

    fun onUseDefaultServerChange(enabled: Boolean) {
        _uiState.update { current ->
            current.copy(
                useDefaultServer = enabled,
                customServerUrl = if (enabled) "" else current.customServerUrl,
            )
        }
    }

    fun onCustomServerUrlChange(value: String) {
        _uiState.update { it.copy(customServerUrl = value) }
    }

    /**
     * Builds a validated [CallRequest] or returns `null` when the form is incomplete.
     */
    fun createCallRequest(): CallRequest? {
        val state = _uiState.value
        if (!state.canJoin) return null

        return CallRequest(
            roomName = state.roomName.trim(),
            serverUrl = callManager.sanitizeServerUrl(state.effectiveServerUrl),
            displayName = state.displayName.trim().ifBlank { null },
        )
    }
}
