package com.example.helmchatreborn.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 destinations for HelmChatReborn.
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
) : NavKey
