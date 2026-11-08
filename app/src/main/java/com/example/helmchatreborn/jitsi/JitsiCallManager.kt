package com.example.helmchatreborn.jitsi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jitsi.meet.sdk.BroadcastEvent
import org.jitsi.meet.sdk.BroadcastIntentHelper
import org.jitsi.meet.sdk.JitsiMeet
import org.jitsi.meet.sdk.JitsiMeetConferenceOptions
import org.jitsi.meet.sdk.JitsiMeetUserInfo
import org.jitsi.meet.sdk.JitsiMeetView
import java.net.MalformedURLException
import java.net.URL

/**
 * State representing current Jitsi call status.
 */
sealed class CallState {
    object Idle : CallState()
    data class Connecting(val roomName: String, val serverUrl: String) : CallState()
    data class Connected(val roomName: String, val serverUrl: String) : CallState()
    data class Error(val message: String) : CallState()
}

/**
 * Controls an embedded [JitsiMeetView] in strictly audio-only, low-bandwidth mode.
 *
 * The manager translates the Jitsi SDK broadcast events into [StateFlow]s consumed by the
 * Compose UI. Mute / hang-up commands are sent back to the SDK through its external API
 * broadcasts. The class is Android-framework light on purpose so the option building and URL
 * helpers can be covered by JVM unit tests.
 */
class JitsiCallManager {

    companion object {
        const val DEFAULT_SERVER_URL = "https://meet.ffmuc.net"
        private const val TAG = "JitsiCallManager"
        private const val OPUS_MAX_AVERAGE_BITRATE = 24_000
        private const val CONNECT_TIMEOUT_MS = 30_000L
        private const val CONNECT_TIMEOUT_MESSAGE =
            "Nie udało się połączyć. Sprawdź adres serwera i połączenie z internetem."

        /**
         * Used when the rider leaves the optional name field empty. Supplying a name avoids
         * Jitsi's hidden "What's your name?" prompt, which would otherwise steal focus and open
         * the soft keyboard behind the custom call overlay.
         */
        const val DEFAULT_DISPLAY_NAME = "Motocyklista"

        @Volatile
        private var instance: JitsiCallManager? = null

        fun getInstance(): JitsiCallManager {
            return instance ?: synchronized(this) {
                instance ?: JitsiCallManager().also { instance = it }
            }
        }
    }

    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _participantCount = MutableStateFlow(0)
    val participantCount: StateFlow<Int> = _participantCount.asStateFlow()

    private var currentServerUrl: String = DEFAULT_SERVER_URL
    private var currentRoomName: String? = null

    private var localBroadcastManager: LocalBroadcastManager? = null
    private var broadcastReceiver: BroadcastReceiver? = null
    private val participantIds = mutableSetOf<String>()

    private var connectTimeout: Runnable? = null
    private val mainHandler: Handler by lazy { Handler(Looper.getMainLooper()) }

    /**
     * Joins [roomName] using the already created [view]. Must be called on the main thread
     * (normally from the Compose `AndroidView` factory).
     */
    fun join(
        view: JitsiMeetView,
        roomName: String,
        serverUrl: String = DEFAULT_SERVER_URL,
        displayName: String? = null,
        isMuted: Boolean = false
    ) {
        require(roomName.isNotBlank()) { "Room name cannot be empty" }

        val activeServerUrl = sanitizeServerUrl(if (serverUrl.isBlank()) DEFAULT_SERVER_URL else serverUrl)
        currentServerUrl = activeServerUrl
        currentRoomName = roomName
        _isMuted.value = isMuted
        _participantCount.value = 0
        participantIds.clear()

        unregisterBroadcastReceiver()
        registerBroadcastReceiver(view.context.applicationContext)

        _callState.value = CallState.Connecting(roomName, activeServerUrl)

        try {
            val options = buildConferenceOptions(
                roomName = roomName,
                serverUrl = activeServerUrl,
                displayName = displayName,
                isMuted = isMuted
            )

            Log.i(TAG, "Joining embedded audio-only room '$roomName' on '$activeServerUrl'")
            view.join(options)
            scheduleConnectTimeout(roomName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to join room '$roomName'", e)
            cancelConnectTimeout()
            _callState.value = CallState.Error(e.message ?: "Failed to join room")
        }
    }

    /**
     * Called when the embedded view is released / removed from the UI hierarchy.
     */
    fun onViewReleased() {
        cancelConnectTimeout()
        unregisterBroadcastReceiver()
        currentRoomName = null
        participantIds.clear()
        _participantCount.value = 0
        if (_callState.value !is CallState.Idle) {
            _callState.value = CallState.Idle
        }
    }

    fun toggleMute(): Boolean {
        val newMuted = !_isMuted.value
        setMuted(newMuted)
        return newMuted
    }

    fun setMuted(muted: Boolean) {
        _isMuted.value = muted
        sendBroadcast(BroadcastIntentHelper.buildSetAudioMutedIntent(muted))
    }

    /**
     * Asks Jitsi to leave the conference and resets the local state immediately so the UI can
     * navigate away without waiting for the SDK round-trip.
     */
    fun hangUp() {
        Log.i(TAG, "Leaving Jitsi call for room '$currentRoomName'")
        cancelConnectTimeout()
        sendBroadcast(BroadcastIntentHelper.buildHangUpIntent())
        currentRoomName = null
        participantIds.clear()
        _participantCount.value = 0
        _callState.value = CallState.Idle
    }

    /**
     * Builds [JitsiMeetConferenceOptions] configured for a low-bandwidth, audio-only
     * "car mode" intercom call. Video is disabled both locally and remotely (`lastN = 0`).
     */
    fun buildConferenceOptions(
        roomName: String,
        serverUrl: String = DEFAULT_SERVER_URL,
        displayName: String? = null,
        isMuted: Boolean = false
    ): JitsiMeetConferenceOptions {
        val sanitizedServerUrl = sanitizeServerUrl(serverUrl)
        val url = try {
            URL(sanitizedServerUrl)
        } catch (e: MalformedURLException) {
            Log.e(TAG, "Invalid server URL: $sanitizedServerUrl, falling back to default $DEFAULT_SERVER_URL", e)
            URL(DEFAULT_SERVER_URL)
        }

        val builder = JitsiMeetConferenceOptions.Builder()
            .setServerURL(url)
            .setRoom(roomName)
            .setAudioOnly(true)
            .setVideoMuted(true)
            .setAudioMuted(isMuted)
            // Low-bandwidth / car mode: never receive remote video and skip prejoin/welcome.
            .setConfigOverride("startLastN", 0)
            .setConfigOverride("channelLastN", 0)
            .setConfigOverride("startCarMode", true)
            .setConfigOverride("prejoinPageEnabled", false)
            .setConfigOverride("enableWelcomePage", false)
            .setConfigOverride("opusMaxAverageBitrate", OPUS_MAX_AVERAGE_BITRATE)
            .setConfigOverride("stereo", false)
            .setFeatureFlag("welcomepage.enabled", false)
            .setFeatureFlag("prejoinpage.enabled", false)
            .setFeatureFlag("tile-view.enabled", false)
            .setFeatureFlag("video-share.enabled", false)
            .setFeatureFlag("lobby-mode.enabled", false)
            .setFeatureFlag("car-mode.enabled", true)
            .setFeatureFlag("audio-only.enabled", true)
            .setFeatureFlag("filmstrip.enabled", false)
            .setFeatureFlag("chat.enabled", false)
            .setFeatureFlag("invite.enabled", false)
            .setFeatureFlag("add-people.enabled", false)
            .setFeatureFlag("calendar.enabled", false)
            .setFeatureFlag("recording.enabled", false)
            .setFeatureFlag("livestreaming.enabled", false)
            .setFeatureFlag("screen-sharing.enabled", false)
            .setFeatureFlag("android.screensharing.enabled", false)
            .setFeatureFlag("notifications.enabled", false)
            .setFeatureFlag("call-integration.enabled", false)
            .setFeatureFlag("pip.enabled", false)

        val userInfo = JitsiMeetUserInfo().apply {
            this.displayName = displayName?.takeIf { it.isNotBlank() } ?: DEFAULT_DISPLAY_NAME
        }
        builder.setUserInfo(userInfo)

        return builder.build()
    }

    /**
     * Set the default conference options globally on JitsiMeet.
     */
    fun configureDefaultOptions(serverUrl: String = DEFAULT_SERVER_URL) {
        val sanitized = sanitizeServerUrl(serverUrl)
        val url = try {
            URL(sanitized)
        } catch (e: MalformedURLException) {
            URL(DEFAULT_SERVER_URL)
        }

        val defaultOptions = JitsiMeetConferenceOptions.Builder()
            .setServerURL(url)
            .setAudioOnly(true)
            .setVideoMuted(true)
            .setConfigOverride("startLastN", 0)
            .setConfigOverride("channelLastN", 0)
            .setFeatureFlag("welcomepage.enabled", false)
            .setFeatureFlag("prejoinpage.enabled", false)
            .build()

        JitsiMeet.setDefaultConferenceOptions(defaultOptions)
    }

    /**
     * Utility method to sanitize server URL string.
     */
    fun sanitizeServerUrl(url: String): String {
        var trimmed = url.trim()
        if (trimmed.isEmpty()) {
            return DEFAULT_SERVER_URL
        }
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "https://$trimmed"
        }
        return trimmed.removeSuffix("/")
    }

    private fun registerBroadcastReceiver(context: Context) {
        val manager = LocalBroadcastManager.getInstance(context)
        val filter = IntentFilter().apply {
            BroadcastEvent.Type.values().forEach { addAction(it.action) }
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent == null) return
                try {
                    handleBroadcastEvent(BroadcastEvent(intent))
                } catch (e: Exception) {
                    Log.w(TAG, "Ignoring malformed Jitsi broadcast event", e)
                }
            }
        }

        manager.registerReceiver(receiver, filter)
        localBroadcastManager = manager
        broadcastReceiver = receiver
    }

    private fun unregisterBroadcastReceiver() {
        val manager = localBroadcastManager
        val receiver = broadcastReceiver
        if (manager != null && receiver != null) {
            try {
                manager.unregisterReceiver(receiver)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister Jitsi broadcast receiver", e)
            }
        }
        broadcastReceiver = null
    }

    private fun sendBroadcast(intent: Intent) {
        localBroadcastManager?.sendBroadcast(intent)
    }

    /**
     * Fails the call if Jitsi has not reported [BroadcastEvent.Type.CONFERENCE_JOINED] within
     * [CONNECT_TIMEOUT_MS]. This matters for unreachable or misconfigured servers, where Jitsi
     * would otherwise keep retrying its hidden UI forever behind the custom overlay.
     */
    private fun scheduleConnectTimeout(roomName: String) {
        cancelConnectTimeout()
        val runnable = Runnable {
            if (_callState.value is CallState.Connecting && currentRoomName == roomName) {
                Log.w(TAG, "Connection to room '$roomName' timed out")
                _callState.value = CallState.Error(CONNECT_TIMEOUT_MESSAGE)
            }
        }
        connectTimeout = runnable
        mainHandler.postDelayed(runnable, CONNECT_TIMEOUT_MS)
    }

    private fun cancelConnectTimeout() {
        connectTimeout?.let { mainHandler.removeCallbacks(it) }
        connectTimeout = null
    }

    private fun handleBroadcastEvent(event: BroadcastEvent) {
        when (event.type) {
            BroadcastEvent.Type.CONFERENCE_WILL_JOIN -> {
                currentRoomName?.let { room ->
                    _callState.value = CallState.Connecting(room, currentServerUrl)
                }
            }

            BroadcastEvent.Type.CONFERENCE_JOINED -> {
                cancelConnectTimeout()
                if (_participantCount.value == 0) {
                    _participantCount.value = 1
                }
                currentRoomName?.let { room ->
                    _callState.value = CallState.Connected(room, currentServerUrl)
                }
            }

            BroadcastEvent.Type.CONFERENCE_TERMINATED,
            BroadcastEvent.Type.READY_TO_CLOSE -> {
                cancelConnectTimeout()
                participantIds.clear()
                _participantCount.value = 0
                _callState.value = CallState.Idle
            }

            BroadcastEvent.Type.AUDIO_MUTED_CHANGED -> {
                event.booleanValue("muted")?.let { _isMuted.value = it }
            }

            BroadcastEvent.Type.PARTICIPANT_JOINED -> {
                event.participantId()?.let { id ->
                    participantIds.add(id)
                    _participantCount.value = participantIds.size
                }
            }

            BroadcastEvent.Type.PARTICIPANT_LEFT -> {
                event.participantId()?.let { id ->
                    participantIds.remove(id)
                    _participantCount.value = participantIds.size
                }
            }

            else -> Unit
        }
    }

    private fun BroadcastEvent.booleanValue(key: String): Boolean? {
        return when (val value = data?.get(key)) {
            is Boolean -> value
            is String -> value.toBooleanStrictOrNull()
            else -> null
        }
    }

    private fun BroadcastEvent.participantId(): String? {
        val value = data?.get("id") ?: return null
        return value.toString().takeIf { it.isNotBlank() }
    }
}
