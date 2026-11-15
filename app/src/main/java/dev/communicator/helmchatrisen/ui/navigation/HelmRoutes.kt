package dev.communicator.helmchatrisen.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 destinations for HelmChatRisen.
 *
 * Keys are [Serializable] so the back stack survives configuration changes
 * (see `rememberNavBackStack`).
 */

/** Home / join screen: room name, rider name and server configuration. */
@Serializable
data object HomeRoute : NavKey

/** Active (or connecting) audio-only call screen. */
@Serializable
data class CallRoute(
    val roomName: String,
    val serverUrl: String,
    val displayName: String? = null,
    val token: String? = null,
    /**
     * When true the embedded Jitsi UI is shown interactively so the rider can sign in and
     * start a meet.jit.si room as its moderator. Once the room is running the normal
     * motorcycle overlay can be restored.
     */
    val hostSetup: Boolean = false,
) : NavKey
