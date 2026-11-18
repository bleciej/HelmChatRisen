package dev.communicator.helmchatrisen.jitsi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import timber.log.Timber
import dev.communicator.helmchatrisen.R
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
import org.jitsi.meet.sdk.ParticipantsService
import java.net.MalformedURLException
import java.net.URL

/**
 * State representing current Jitsi call status.
 */
sealed class CallState {
    /** No call is active. */
    object Idle : CallState()

    /**
     * The conference is being joined.
     *
     * @param roomName the Jitsi room being joined.
     * @param serverUrl the Jitsi server hosting the room.
     */
    data class Connecting(val roomName: String, val serverUrl: String) : CallState()

    /**
     * The conference is joined and the intercom is live.
     *
     * @param roomName the Jitsi room that was joined.
     * @param serverUrl the Jitsi server hosting the room.
     */
    data class Connected(val roomName: String, val serverUrl: String) : CallState()

    /**
     * The conference dropped unexpectedly (network loss, server restart). Jitsi keeps its view
     * alive and reloads the conference on its own; the app must not navigate away while this is
     * in progress.
     *
     * @param roomName the Jitsi room that is being restored.
     * @param serverUrl the Jitsi server hosting the room.
     */
    data class Reconnecting(val roomName: String, val serverUrl: String) : CallState()

    /**
     * The call failed.
     *
     * @param message a raw error message, or `null` when only a localized resource is known.
     * @param messageResId the string resource describing the failure, or `null` when only a raw
     *   message is known.
     */
    data class Error(val message: String? = null, val messageResId: Int? = null) : CallState()
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
        /** Jitsi server used when the rider has not configured a custom one. */
        const val DEFAULT_SERVER_URL = "https://meet.ffmuc.net"
        private const val OPUS_MAX_AVERAGE_BITRATE = 24_000
        private const val CONNECT_TIMEOUT_MS = 30_000L

        /**
         * How long Jitsi may spend trying to restore a dropped conference before the app gives
         * up and shows an error. The Jitsi reload dialog is patched to retry after 5 seconds,
         * so this is only a safety net for unrecoverable failures.
         */
        private const val RECONNECT_TIMEOUT_MS = 45_000L

        /**
         * Used when the rider leaves the optional name field empty. Supplying a name avoids
         * Jitsi's hidden "What's your name?" prompt, which would otherwise steal focus and open
         * the soft keyboard behind the custom call overlay.
         */
        const val DEFAULT_DISPLAY_NAME = "Motocyklista"

        @Volatile
        private var instance: JitsiCallManager? = null

        /**
         * Returns the process-wide manager, creating it on first use.
         *
         * @return the shared [JitsiCallManager] instance.
         */
        fun getInstance(): JitsiCallManager {
            return instance ?: synchronized(this) {
                instance ?: JitsiCallManager().also { instance = it }
            }
        }
    }

    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    /** Current call status, observed by the Compose UI. */
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    /** Whether the local microphone is currently muted. */
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    /**
     * `ParticipantsService` holds its callbacks through [java.lang.ref.WeakReference], so the
     * manager keeps a strong reference until the snapshot is applied, fails or times out.
     */
    private var participantsInfoCallback: ParticipantsService.ParticipantsInfoCallback? = null

    /**
     * Owns the participant ids, the pending-snapshot window, the buffered join/leave events and
     * the local-rider accounting, so the count can be covered by JVM tests.
     */
    private val participantTracker = ParticipantTracker(
        scheduler = { delayMs, action ->
            val runnable = Runnable { action() }
            mainHandler.postDelayed(runnable, delayMs)
            ScheduledTimeout { mainHandler.removeCallbacks(runnable) }
        },
        onSnapshotSettled = { timedOut ->
            participantsInfoCallback = null
            if (timedOut) {
                Timber.w("Participant snapshot timed out, using broadcast events instead")
            }
        },
    )

    /** Number of riders in the current call, including the local rider. */
    val participantCount: StateFlow<Int> = participantTracker.count

    /**
     * `false` while the device has no default network. Jitsi pauses the conference audio during
     * an outage and resumes its XMPP connection on its own; the UI uses this flow to tell the
     * rider why the intercom went quiet instead of silently appearing connected.
     */
    private val _isNetworkAvailable = MutableStateFlow(true)
    /** Whether the device currently has a default network. */
    val isNetworkAvailable: StateFlow<Boolean> = _isNetworkAvailable.asStateFlow()

    private var currentServerUrl: String = DEFAULT_SERVER_URL
    private var currentRoomName: String? = null

    private var localBroadcastManager: LocalBroadcastManager? = null
    private var broadcastReceiver: BroadcastReceiver? = null

    private var connectTimeout: Runnable? = null
    private var reconnectTimeout: Runnable? = null
    private var reconnectPaused = false
    private var isLeaving = false
    private val mainHandler: Handler by lazy { Handler(Looper.getMainLooper()) }

    private var connectivityManager: ConnectivityManager? = null
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        /**
         * Marks the network as available and, when a reconnect was paused, restarts its timeout.
         *
         * @param network the network that became available.
         */
        override fun onAvailable(network: Network) {
            _isNetworkAvailable.value = true
            if (reconnectPaused) {
                reconnectPaused = false
                currentRoomName?.let { room ->
                    if (_callState.value is CallState.Reconnecting) {
                        Timber.i("Network restored, restarting reconnect timeout for '$room'")
                        scheduleReconnectTimeout(room)
                    }
                }
            }
        }

        /**
         * Marks the network as unavailable and pauses the reconnect timeout while offline.
         *
         * @param network the network that was lost.
         */
        override fun onLost(network: Network) {
            _isNetworkAvailable.value = false
            // Do not count offline time against the reconnect timeout: Jitsi cannot restore the
            // conference until a network is back.
            if (reconnectTimeout != null) {
                cancelReconnectTimeout()
                reconnectPaused = true
            }
            Timber.w("Default network lost")
        }

        /**
         * Marks the network as unavailable when no default network can be provided.
         */
        override fun onUnavailable() {
            _isNetworkAvailable.value = false
        }
    }

    /**
     * Joins [roomName] using the already created [view]. Must be called on the main thread
     * (normally from the Compose `AndroidView` factory).
     *
     * @param view the embedded view that hosts the conference.
     * @param roomName the Jitsi room to join.
     * @param serverUrl the Jitsi server hosting the room.
     * @param displayName the rider's display name, or `null` to use [DEFAULT_DISPLAY_NAME].
     * @param isMuted `true` to join with the microphone muted.
     * @param token an optional JWT for token-auth servers.
     * @throws IllegalArgumentException when [roomName] is blank.
     */
    fun join(
        view: JitsiMeetView,
        roomName: String,
        serverUrl: String = DEFAULT_SERVER_URL,
        displayName: String? = null,
        isMuted: Boolean = false,
        token: String? = null,
    ) {
        require(roomName.isNotBlank()) { "Room name cannot be empty" }

        val activeServerUrl = sanitizeServerUrl(serverUrl)
        currentServerUrl = activeServerUrl
        currentRoomName = roomName
        isLeaving = false
        _isMuted.value = isMuted
        resetParticipantSession()

        unregisterBroadcastReceiver()
        registerBroadcastReceiver(view.context.applicationContext)
        registerNetworkCallback(view.context.applicationContext)

        cancelReconnectTimeout()
        reconnectPaused = false
        _callState.value = CallState.Connecting(roomName, activeServerUrl)

        try {
            val options = buildConferenceOptions(
                roomName = roomName,
                serverUrl = activeServerUrl,
                displayName = displayName,
                isMuted = isMuted,
                token = token,
            )

            Timber.i("Joining embedded audio-only room '$roomName' on '$activeServerUrl'")
            view.join(options)
            scheduleConnectTimeout(roomName)
        } catch (e: Exception) {
            Timber.e(e, "Failed to join room '$roomName'")
            cancelConnectTimeout()
            _callState.value = CallState.Error(e.message ?: "Failed to join room")
        }
    }

    /**
     * Called when the embedded view is released / removed from the UI hierarchy.
     */
    fun onViewReleased() {
        cancelConnectTimeout()
        cancelReconnectTimeout()
        reconnectPaused = false
        resetMuteState()
        unregisterBroadcastReceiver()
        unregisterNetworkCallback()
        currentRoomName = null
        resetParticipantSession()
        if (_callState.value !is CallState.Idle) {
            _callState.value = CallState.Idle
        }
    }

    /**
     * Flips the local mute state and pushes it to Jitsi.
     *
     * @return the new muted state.
     */
    fun toggleMute(): Boolean {
        val newMuted = !_isMuted.value
        setMuted(newMuted)
        return newMuted
    }

    /**
     * Sets the local mute state and pushes it to Jitsi.
     *
     * @param muted `true` to mute the microphone, `false` to unmute it.
     */
    fun setMuted(muted: Boolean) {
        _isMuted.value = muted
        sendBroadcast(BroadcastIntentHelper.buildSetAudioMutedIntent(muted))
    }

    /**
     * Clears a mute chosen in a previous call so the next conference starts with the microphone
     * live. Jitsi receives the join-time mute state, so leaving this set would silently join the
     * next room with the microphone muted while audio playback still works.
     */
    internal fun resetMuteState() {
        _isMuted.value = false
    }

    /**
     * Asks Jitsi to leave the conference and resets the local state immediately so the UI can
     * navigate away without waiting for the SDK round-trip.
     */
    fun hangUp() {
        Timber.i("Leaving Jitsi call for room '$currentRoomName'")
        isLeaving = true
        resetMuteState()
        cancelConnectTimeout()
        cancelReconnectTimeout()
        reconnectPaused = false
        unregisterNetworkCallback()
        sendBroadcast(BroadcastIntentHelper.buildHangUpIntent())
        // Stop reacting to queued Jitsi events: a CONFERENCE_JOINED posted just before the
        // hang-up would otherwise re-arm participant tracking for a finished call.
        unregisterBroadcastReceiver()
        currentRoomName = null
        resetParticipantSession()
        _callState.value = CallState.Idle
    }

    /**
     * Builds [JitsiMeetConferenceOptions] configured for a low-bandwidth, audio-only
     * "car mode" intercom call. Video is disabled both locally and remotely (`lastN = 0`).
     *
     * @param roomName the Jitsi room to join.
     * @param serverUrl the Jitsi server hosting the room; sanitized before use.
     * @param displayName the rider's display name, or `null` to use [DEFAULT_DISPLAY_NAME].
     * @param isMuted `true` to start with the microphone muted.
     * @param token an optional JWT for token-auth servers.
     * @return the options passed to [JitsiMeetView.join].
     */
    fun buildConferenceOptions(
        roomName: String,
        serverUrl: String = DEFAULT_SERVER_URL,
        displayName: String? = null,
        isMuted: Boolean = false,
        token: String? = null,
    ): JitsiMeetConferenceOptions {
        val sanitizedServerUrl = sanitizeServerUrl(serverUrl)
        val url = try {
            URL(sanitizedServerUrl)
        } catch (e: MalformedURLException) {
            Timber.e(e, "Invalid server URL: $sanitizedServerUrl, falling back to default $DEFAULT_SERVER_URL")
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
            .setConfigOverride("noiseSuppression", true)
            .setConfigOverride("echoCancellation", true)
            .setConfigOverride("autoGainControl", true)
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
            // HelmChat owns audio focus/mode/routing through BluetoothAudioRouter so music can
            // keep playing (ducked) and navigation prompts do not end the call.
            .setFeatureFlag("audio-focus.disabled", true)

        token?.trim()?.takeIf { it.isNotEmpty() }?.let { builder.setToken(it) }

        val userInfo = JitsiMeetUserInfo().apply {
            this.displayName = displayName?.takeIf { it.isNotBlank() } ?: DEFAULT_DISPLAY_NAME
        }
        builder.setUserInfo(userInfo)

        return builder.build()
    }

    /**
     * Set the default conference options globally on JitsiMeet.
     *
     * @param serverUrl the Jitsi server used as the default for every conference.
     */
    fun configureDefaultOptions(serverUrl: String = DEFAULT_SERVER_URL) {
        val sanitized = sanitizeServerUrl(serverUrl)
        val url = try {
            URL(sanitized)
        } catch (e: MalformedURLException) {
            Timber.i("Error: '$e'; Invalid server URL: $sanitized, falling back to default $DEFAULT_SERVER_URL")
            URL(DEFAULT_SERVER_URL)
        }

        val defaultOptions = JitsiMeetConferenceOptions.Builder()
            .setServerURL(url)
            .setAudioOnly(true)
            .setVideoMuted(true)
            .setConfigOverride("startLastN", 0)
            .setConfigOverride("channelLastN", 0)
            .setConfigOverride("noiseSuppression", true)
            .setConfigOverride("echoCancellation", true)
            .setConfigOverride("autoGainControl", true)
            .setFeatureFlag("welcomepage.enabled", false)
            .setFeatureFlag("prejoinpage.enabled", false)
            .setFeatureFlag("audio-focus.disabled", true)
            .build()

        JitsiMeet.setDefaultConferenceOptions(defaultOptions)
    }

    /**
     * Utility method to sanitize server URL string.
     *
     * Blank input falls back to [DEFAULT_SERVER_URL]. A known preset host is always resolved to
     * its canonical `https` URL, so a recent room saved as `http://meet.ffmuc.net` is never
     * joined over an insecure connection. Unknown servers keep the scheme the rider typed.
     *
     * @param url the server URL entered by the rider or loaded from a recent room.
     * @return the sanitized URL, or [DEFAULT_SERVER_URL] when [url] is blank.
     */
    fun sanitizeServerUrl(url: String): String {
        val normalized = normalizeServerUrl(url) ?: return DEFAULT_SERVER_URL
        val secureCandidate = if (normalized.startsWith(HTTP_SCHEME, ignoreCase = true)) {
            HTTPS_SCHEME + normalized.substringAfter("://")
        } else {
            normalized
        }
        return JitsiServers.byUrl(secureCandidate)?.url ?: normalized
    }

    /**
     * Registers the local broadcast receiver that turns Jitsi SDK events into call state
     * updates.
     *
     * @param context any context; only its [LocalBroadcastManager] is used.
     */
    private fun registerBroadcastReceiver(context: Context) {
        val manager = LocalBroadcastManager.getInstance(context)
        val filter = IntentFilter().apply {
            BroadcastEvent.Type.entries.forEach { addAction(it.action) }
        }

        val receiver = object : BroadcastReceiver() {
            /**
             * Forwards a Jitsi broadcast to [handleBroadcastEvent], ignoring malformed payloads.
             *
             * @param receiverContext the context the receiver was registered with.
             * @param intent the broadcast being delivered, or `null` when it carries no data.
             */
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent == null) return
                try {
                    handleBroadcastEvent(BroadcastEvent(intent))
                } catch (e: Exception) {
                    Timber.w(e, "Ignoring malformed Jitsi broadcast event")
                }
            }
        }

        manager.registerReceiver(receiver, filter)
        localBroadcastManager = manager
        broadcastReceiver = receiver
    }

    /**
     * Unregisters the Jitsi broadcast receiver, ignoring failures when it was never registered.
     */
    private fun unregisterBroadcastReceiver() {
        val manager = localBroadcastManager
        val receiver = broadcastReceiver
        if (manager != null && receiver != null) {
            try {
                manager.unregisterReceiver(receiver)
            } catch (e: Exception) {
                Timber.w(e, "Failed to unregister Jitsi broadcast receiver")
            }
        }
        broadcastReceiver = null
    }

    /**
     * Registers the default-network callback that drives [isNetworkAvailable] and pauses the
     * reconnect timeout while the device is offline.
     *
     * @param context any context; its [ConnectivityManager] is used.
     */
    private fun registerNetworkCallback(context: Context) {
        unregisterNetworkCallback()
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return
        connectivityManager = manager
        _isNetworkAvailable.value = manager.activeNetwork != null
        try {
            manager.registerDefaultNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Timber.w(e, "Failed to register network callback")
            connectivityManager = null
        }
    }

    /**
     * Unregisters the network callback, ignoring failures when none was registered.
     */
    private fun unregisterNetworkCallback() {
        val manager = connectivityManager ?: return
        connectivityManager = null
        try {
            manager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Timber.w(e, "Failed to unregister network callback")
        }
    }

    /**
     * Sends an external-API intent to Jitsi when the local broadcast manager is registered.
     *
     * @param intent the Jitsi external-API intent to deliver.
     */
    private fun sendBroadcast(intent: Intent) {
        localBroadcastManager?.sendBroadcast(intent)
    }

    /**
     * Fails the call if Jitsi has not reported [BroadcastEvent.Type.CONFERENCE_JOINED] within
     * [CONNECT_TIMEOUT_MS]. This matters for unreachable or misconfigured servers, where Jitsi
     * would otherwise keep retrying its hidden UI forever behind the custom overlay.
     *
     * @param roomName the room the timeout applies to; a stale timeout is ignored when the
     *   active room changed.
     */
    private fun scheduleConnectTimeout(roomName: String) {
        cancelConnectTimeout()
        val runnable = Runnable {
            if (_callState.value is CallState.Connecting && currentRoomName == roomName) {
                Timber.w("Connection to room '$roomName' timed out")
                _callState.value = CallState.Error(messageResId = R.string.call_error_connect_timeout)
            }
        }
        connectTimeout = runnable
        mainHandler.postDelayed(runnable, CONNECT_TIMEOUT_MS)
    }

    /**
     * Cancels a pending connect timeout, if any.
     */
    private fun cancelConnectTimeout() {
        connectTimeout?.let { mainHandler.removeCallbacks(it) }
        connectTimeout = null
    }

    /**
     * Keeps the call screen alive while Jitsi restores a dropped conference. Without this the
     * app would navigate back home on the transient [BroadcastEvent.Type.CONFERENCE_TERMINATED]
     * that Jitsi emits before its own automatic reload kicks in.
     *
     * @param roomName the room the timeout applies to; a stale timeout is ignored when the
     *   active room changed.
     */
    private fun scheduleReconnectTimeout(roomName: String) {
        cancelReconnectTimeout()
        val runnable = Runnable {
            if (_callState.value is CallState.Reconnecting && currentRoomName == roomName) {
                Timber.w("Reconnect to room '$roomName' timed out")
                _callState.value = CallState.Error(messageResId = R.string.call_error_reconnect_timeout)
            }
        }
        reconnectTimeout = runnable
        mainHandler.postDelayed(runnable, RECONNECT_TIMEOUT_MS)
    }

    /**
     * Cancels a pending reconnect timeout, if any.
     */
    private fun cancelReconnectTimeout() {
        reconnectTimeout?.let { mainHandler.removeCallbacks(it) }
        reconnectTimeout = null
    }

    /**
     * Requests an authoritative participant snapshot from the SDK once the conference is joined.
     *
     * The [BroadcastEvent.Type.PARTICIPANT_JOINED] / [BroadcastEvent.Type.PARTICIPANT_LEFT]
     * stream stays the primary source, but the snapshot also includes riders who were already in
     * the room and repairs any event the app might have missed. The request is fire-and-forget,
     * so [ParticipantTracker] bounds it with a deadline and falls back to the event stream when
     * the SDK never answers. The service only exists once the Jitsi React context has been
     * created, so it is treated as optional.
     */
    private fun refreshParticipantCount() {
        val service = ParticipantsService.getInstance() ?: return
        val request = participantTracker.onSnapshotRequested() ?: return

        val callback = ParticipantsService.ParticipantsInfoCallback { participants ->
            participantTracker.onSnapshotReceived(
                request = request,
                snapshotIds = sanitizeParticipantIds(participants.map { it.id }),
                localParticipantId = participants.firstOrNull { it.isLocal }?.id,
            )
        }

        participantsInfoCallback = callback
        try {
            service.retrieveParticipantsInfo(callback)
        } catch (e: Exception) {
            participantTracker.onSnapshotFailed(request)
            Timber.w(e, "Failed to retrieve participants info")
        }
    }

    /**
     * Invalidates all participant state for the current join attempt.
     *
     * Every teardown path must call this so a late snapshot or broadcast cannot repopulate the
     * count of a finished call, and so events buffered for an aborted snapshot are discarded.
     */
    private fun resetParticipantSession() {
        participantTracker.reset()
        participantsInfoCallback = null
    }

    /**
     * Translates a Jitsi broadcast event into call-state, mute-state and participant updates.
     *
     * @param event the event to apply.
     */
    private fun handleBroadcastEvent(event: BroadcastEvent) {
        when (event.type) {
            BroadcastEvent.Type.CONFERENCE_WILL_JOIN -> {
                // A new join attempt (including Jitsi's own automatic reload) supersedes any
                // pending reconnect timeout.
                cancelReconnectTimeout()
                currentRoomName?.let { room ->
                    if (_callState.value !is CallState.Connected) {
                        _callState.value = CallState.Connecting(room, currentServerUrl)
                    }
                }
            }

            BroadcastEvent.Type.CONFERENCE_JOINED -> {
                cancelConnectTimeout()
                cancelReconnectTimeout()
                if (!isParticipantSessionLive(currentRoomName, isLeaving, _callState.value)) {
                    Timber.i("Ignoring CONFERENCE_JOINED for a session that is no longer live")
                    return
                }
                val room = currentRoomName ?: return
                _callState.value = CallState.Connected(room, currentServerUrl)
                participantTracker.onConferenceJoined()
                refreshParticipantCount()
            }

            BroadcastEvent.Type.CONFERENCE_TERMINATED,
            BroadcastEvent.Type.READY_TO_CLOSE -> {
                cancelConnectTimeout()
                resetParticipantSession()
                val room = currentRoomName
                if (!isLeaving && room != null) {
                    // Jitsi reloads the conference on its own after a transient drop; stay on
                    // the call screen and show the reconnecting state instead of navigating home.
                    Timber.i("Conference for room '$room' ended unexpectedly, waiting for reload")
                    _callState.value = CallState.Reconnecting(room, currentServerUrl)
                    scheduleReconnectTimeout(room)
                } else {
                    cancelReconnectTimeout()
                    _callState.value = CallState.Idle
                }
            }

            BroadcastEvent.Type.AUDIO_MUTED_CHANGED -> {
                event.booleanValue("muted")?.let { _isMuted.value = it }
            }

            BroadcastEvent.Type.PARTICIPANT_JOINED -> {
                event.participantId()?.let { id ->
                    participantTracker.onJoined(id, isLocal = event.booleanValue("isLocal") == true)
                }
            }

            BroadcastEvent.Type.PARTICIPANT_LEFT -> {
                event.participantId()?.let { id ->
                    participantTracker.onLeft(id, isLocal = event.booleanValue("isLocal") == true)
                }
            }

            else -> Unit
        }
    }

    /**
     * Reads a boolean extra from the broadcast payload, accepting the stringified form the bundle
     * transport produces.
     *
     * @param key the extra key to read.
     * @return the boolean value, or `null` when the extra is missing or not boolean-like.
     */
    private fun BroadcastEvent.booleanValue(key: String): Boolean? {
        return when (val value = data?.get(key)) {
            is Boolean -> value
            is String -> value.toBooleanStrictOrNull()
            else -> null
        }
    }

    /**
     * Extracts the participant id from this event's payload.
     *
     * @return the participant id, or `null` when the payload has no usable id.
     */
    private fun BroadcastEvent.participantId(): String? = participantIdFrom(data)
}

/**
 * Extracts a participant id from a Jitsi broadcast payload.
 *
 * The SDK flattens the participant info into the event extras and names the id
 * `participantId` (see the bundled `participantToParticipantInfo` helper). The bundle transport
 * stringifies every value, so `"null"` / `"undefined"` must be rejected as well. Earlier SDK
 * payloads used `id`, so that stays supported as a fallback. Only string-like extras can become
 * an id: anything else would stringify into a key such as `"Bundle[{...}]"`.
 *
 * @param data the broadcast payload, or `null` when the event carried no extras.
 * @return the first valid participant id, or `null` when none is present.
 */
internal fun participantIdFrom(data: Map<String, Any?>?): String? {
    if (data == null) return null

    return sequenceOf(data["participantId"], data["id"])
        .firstNotNullOfOrNull { candidate -> (candidate as? CharSequence)?.toString().asValidParticipantId() }
}

/**
 * Merges an authoritative snapshot with the participant events that arrived while it was in
 * flight.
 *
 * The snapshot describes the room at request time, so buffered events are replayed on top of it.
 * An empty snapshot means the SDK returned no usable ids; the current baseline is kept instead
 * of clearing the count.
 *
 * @param current the baseline id set held before the snapshot was requested.
 * @param snapshot the ids returned by the SDK; an empty set keeps [current].
 * @param pendingEvents the join/leave events buffered while the snapshot was in flight, as
 *   `id to joined` pairs.
 * @return the merged id set.
 */
internal fun applyParticipantSnapshot(
    current: Set<String>,
    snapshot: Set<String>,
    pendingEvents: List<Pair<String, Boolean>>,
): Set<String> {
    val merged = (snapshot.ifEmpty { current }).toMutableSet()
    pendingEvents.forEach { (id, joined) ->
        if (joined) merged.add(id) else merged.remove(id)
    }
    return merged
}

/**
 * Returns `true` when a snapshot response still belongs to the active call and to the newest
 * request, so an older response cannot overwrite a newer baseline.
 *
 * @param session the session the response belongs to.
 * @param currentSession the session of the active call.
 * @param requestId the id of the snapshot request the response belongs to.
 * @param latestRequestId the id of the newest snapshot request.
 * @return `true` when the response still belongs to the active call and newest request.
 */
internal fun isParticipantSnapshotCurrent(
    session: Long,
    currentSession: Long,
    requestId: Long,
    latestRequestId: Long,
): Boolean = session == currentSession && requestId == latestRequestId

/**
 * Drops null, blank and stringified-`null` ids from a snapshot before it is merged.
 *
 * Shares the validation used for broadcast payloads, so the snapshot path cannot count an id
 * that the event path would reject.
 *
 * @param ids the ids to validate; `null` entries are dropped.
 * @return the set of valid participant ids.
 */
internal fun sanitizeParticipantIds(ids: Iterable<String?>): Set<String> =
    ids.mapNotNullTo(mutableSetOf()) { it.asValidParticipantId() }

/**
 * Returns `true` while the current room is still an active join attempt, so a `CONFERENCE_JOINED`
 * queued before `hangUp()` / `onViewReleased()` cannot re-arm participant tracking.
 *
 * @param roomName the room of the join attempt, or `null` when no room is active.
 * @param isLeaving `true` when the rider has already left the call.
 * @param callState the current call state.
 * @return `true` while the session is still a live join attempt.
 */
internal fun isParticipantSessionLive(
    roomName: String?,
    isLeaving: Boolean,
    callState: CallState,
): Boolean = roomName != null && !isLeaving && callState !is CallState.Idle

/**
 * Returns this id when it is a real participant id, or `null` for blank and stringified
 * `null` / `undefined` values.
 *
 * @return the id unchanged when valid, otherwise `null`.
 */
private fun String?.asValidParticipantId(): String? = this?.takeIf { id ->
    id.isNotBlank() && !id.equals("null", ignoreCase = true) &&
        !id.equals("undefined", ignoreCase = true)
}
