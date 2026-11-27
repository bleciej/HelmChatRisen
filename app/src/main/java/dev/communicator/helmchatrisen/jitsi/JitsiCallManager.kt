package dev.communicator.helmchatrisen.jitsi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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

    /** The rider asked to leave and the app is waiting for Jitsi to finish the conference teardown. */
    object Ending : CallState()

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

/** Sentinel used when no conference has produced a start timestamp yet. */
internal const val NO_CALL_STARTED_MILLIS = 0L

/**
 * Holds the monotonic start timestamp of the current call.
 *
 * @param nowMillis supplies the current elapsed-realtime value.
 */
internal class CallStartTracker(
    private val nowMillis: () -> Long,
) {
    private val _startedAtMillis = MutableStateFlow(NO_CALL_STARTED_MILLIS)

    /** Start timestamp of the current call, or [NO_CALL_STARTED_MILLIS] before the first join. */
    val startedAtMillis: StateFlow<Long> = _startedAtMillis.asStateFlow()

    /** Records the first confirmed join; later joins and reconnects keep the original timestamp. */
    fun onConferenceJoined() {
        if (_startedAtMillis.value == NO_CALL_STARTED_MILLIS) {
            _startedAtMillis.value = nowMillis()
        }
    }

    /** Clears the timestamp so the next call starts a fresh timer. */
    fun reset() {
        _startedAtMillis.value = NO_CALL_STARTED_MILLIS
    }
}

/** Counts forced view replacements for one call so the retry loop stays testable and observable. */
internal class ReconnectAttemptTracker {
    private var count = 0

    /** Number of forced view replacements requested for the current call. */
    val attempts: Int
        get() = count

    /**
     * Records one forced view replacement.
     *
     * @param eligible `true` when the manager accepted the replacement request.
     * @return `true` when the attempt was recorded.
     */
    fun record(eligible: Boolean): Boolean {
        if (!eligible) return false
        count += 1
        return true
    }

    /** Clears the counter so the next call starts from zero. */
    fun reset() {
        count = 0
    }
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
         * How often the first-join watchdog checks again while the device is offline. No new view
         * is created during an outage; the retry starts as soon as a default network is back.
         */
        internal const val CONNECT_RETRY_CHECK_MS = 10_000L

        /** Delay used to retry a stalled first join as soon as a default network returns. */
        private const val FIRST_JOIN_RETRY_ON_NETWORK_MS = 0L

        /**
         * How long a failed call stays on screen before the manager publishes [CallState.Idle] and
         * the call route is dismissed. Native cleanup happens immediately; only the error message
         * is kept visible for this short window.
         */
        internal const val ERROR_DISPLAY_TIMEOUT_MS = 4_000L

        /**
         * How long the app waits for Jitsi to acknowledge a hang-up before it falls back to
         * resetting the embedded view. Jitsi's external API is asynchronous, so disposing the view
         * immediately would drop the HANG_UP event and leave the microphone capture alive.
         */
        private const val HANG_UP_CONFIRMATION_TIMEOUT_MS = 3_000L

        /**
         * Additional grace period after the fallback view reset before the UI is released even if
         * Jitsi still sends no termination event.
         */
        private const val HANG_UP_ABORT_TIMEOUT_MS = 2_000L

        /**
         * How long the app gives Jitsi's own reload dialog before creating a fresh view. The bundled
         * countdown is patched to about six seconds, so this is only a safety net for a conference
         * that never reloads at all. Expiry triggers another attempt, never a terminal error.
         */
        internal const val RECONNECT_RELOAD_TIMEOUT_MS = 60_000L

        /**
         * How long a freshly created view may spend joining the room. Its conference-request backoff
         * can reach 120 seconds ("Not ready yet, will retry in ... ms"), so the watchdog must outlast
         * that instead of restarting the view too early. Expiry triggers another attempt.
         */
        internal const val REJOIN_COMPLETION_TIMEOUT_MS = 150_000L

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

    private val callStartTracker = CallStartTracker(nowMillis = SystemClock::elapsedRealtime)
    /**
     * Elapsed-realtime timestamp of the first confirmed join in the current call, or `0` before the
     * conference has joined. The UI derives the visible call duration from this value so a
     * reconnect or activity recreation does not restart the timer.
     */
    val callStartedAtMillis: StateFlow<Long> = callStartTracker.startedAtMillis

    private val _isMuted = MutableStateFlow(value = false)
    /** Whether the local microphone is currently muted. */
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    /**
     * Whether a new conference may be started. `Ending` is deliberately excluded so a second
     * call cannot be launched while the previous conference is still tearing down.
     */
    val canStartNewCall: Boolean
        get() = (_callState.value is CallState.Idle) || (_callState.value is CallState.Error)

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
    ) { timedOut ->
        participantsInfoCallback = null
        if (timedOut) {
            Timber.w("Participant snapshot timed out, using broadcast events instead")
        }
    }

    /** Number of riders in the current call, including the local rider. */
    val participantCount: StateFlow<Int> = participantTracker.count

    /**
     * `false` while the device has no default network. Jitsi pauses the conference audio during
     * an outage and resumes its XMPP connection on its own; the UI uses this flow to tell the
     * rider why the intercom went quiet instead of silently appearing connected.
     */
    private val _isNetworkAvailable = MutableStateFlow(value = true)
    /** Whether the device currently has a default network. */
    val isNetworkAvailable: StateFlow<Boolean> = _isNetworkAvailable.asStateFlow()

    private var currentServerUrl: String = DEFAULT_SERVER_URL
    private var currentRoomName: String? = null

    private var localBroadcastManager: LocalBroadcastManager? = null
    private var broadcastReceiver: BroadcastReceiver? = null

    private var connectTimeout: Runnable? = null
    private var reconnectTimeout: Runnable? = null
    private var errorIdleTimeout: Runnable? = null
    private var reconnectPaused = false

    /** Remaining watchdog time is measured from elapsed realtime so offline time is not counted. */
    private val reconnectDeadline = ReconnectDeadline(SystemClock::elapsedRealtime)

    /** Room whose reconnect watchdog is active or paused while the device is offline. */
    private var pendingReconnectRoom: String? = null
    private var isLeaving = false
    private val mainHandler: Handler by lazy { Handler(Looper.getMainLooper()) }

    /**
     * The embedded view hosting the active conference. The manager owns it strongly from join until
     * terminal cleanup so a teardown callback cannot be dropped by garbage collection or by an
     * `AndroidView.onRelease` that runs before Jitsi confirms the hang-up.
     */
    private var activeView: JitsiMeetView? = null

    /**
     * Incremented when the app must replace the embedded view to force a real rejoin. The Compose
     * host uses this value as an `AndroidView` key; Jitsi 13.1.1 otherwise treats a reload with the
     * same room URL as a no-op.
     */
    private val _viewRecreationToken = MutableStateFlow(value = 0)
    /** Token identifying the current embedded Jitsi view instance. */
    val viewRecreationToken: StateFlow<Int> = _viewRecreationToken.asStateFlow()

    /** The old view that Compose is about to release during a forced rejoin. */
    private var pendingForcedRejoinView: JitsiMeetView? = null

    /** `true` between a forced rejoin request and the next view's [join]. */
    private var isAwaitingRestoredView = false

    /**
     * Generation of the embedded view. Broadcasts and participant snapshots capture it so callbacks
     * queued by a replaced view cannot affect the fresh view.
     */
    private var viewEpoch = 0L

    /**
     * Number of fresh views requested for the current call. Used only for diagnostics: a dropped
     * conference is retried for as long as the rider keeps the call screen open.
     */
    private val reconnectAttemptTracker = ReconnectAttemptTracker()

    /** Incremented for every join so stale teardown timers from an older call can be ignored. */
    private var sessionGeneration = 0L

    /** The generation being torn down, or [Long.MAX_VALUE] while no teardown is in progress. */
    private var teardownGeneration = Long.MAX_VALUE

    /** Whether the SDK has confirmed that the current conference actually joined at least once. */
    private var hasConferenceConfirmation = false

    /**
     * Whether [BroadcastEvent.Type.CONFERENCE_JOINED] was seen since the current [join]. Unlike
     * [hasConferenceConfirmation], a transient termination does not clear it, so the watchdog can
     * tell a first join from Jitsi's automatic rejoin.
     */
    private var hasConnectedConference = false

    private var hangUpConfirmationTimeout: Runnable? = null
    private var hangUpAbortTimeout: Runnable? = null

    private var connectivityManager: ConnectivityManager? = null
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        /**
         * Marks the network as available, resumes a stalled first join, and restarts a paused
         * reconnect timeout with only the time that was left before the outage.
         *
         * @param network the network that became available.
         */
        override fun onAvailable(network: Network) {
            val wasOffline = !_isNetworkAvailable.value
            _isNetworkAvailable.value = true
            val joinRoom = currentRoomName
            if ((joinRoom != null) && shouldRetryFirstJoinOnNetworkRestore(
                    wasOffline = wasOffline,
                    callState = _callState.value,
                    hasConnectedConference = hasConnectedConference,
                    hasPendingConnectTimeout = connectTimeout != null,
                )
            ) {
                Timber.i("Network restored, retrying the first join for '$joinRoom'")
                scheduleConnectTimeout(joinRoom, FIRST_JOIN_RETRY_ON_NETWORK_MS)
            }
            if (!reconnectPaused) return
            reconnectPaused = false
            val room = pendingReconnectRoom ?: return
            if (!isReconnectWatchdogState(_callState.value)) {
                Timber.d("Network restored for '$room' but the call is no longer reconnecting")
                return
            }
            val remainingMs = reconnectDeadline.resume()
            Timber.i("Network restored, resuming reconnect watchdog for '$room' with $remainingMs ms left")
            scheduleReconnectTimeout(room, remainingMs)
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
            pauseReconnectTimeout()
            Timber.w("Default network lost")
        }

        /**
         * Marks the network as unavailable when no default network can be provided.
         */
        override fun onUnavailable() {
            _isNetworkAvailable.value = false
            pauseReconnectTimeout()
            Timber.w("No default network available")
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

        if (_callState.value is CallState.Ending) {
            Timber.w("Ignoring join request for '$roomName' while a hang-up is still in progress")
            return
        }

        // A view created by requestForcedRejoin() must not restart the call timer or forget that
        // the conference previously connected: the watchdog handover depends on that history.
        val isRestoredView = shouldPreserveCallSessionOnJoin(
            isAwaitingRestoredView = isAwaitingRestoredView,
            currentRoomName = currentRoomName,
            joiningRoomName = roomName,
        )
        isAwaitingRestoredView = false
        pendingForcedRejoinView = null
        viewEpoch += 1

        val activeServerUrl = sanitizeServerUrl(serverUrl)
        // Detach from the old conference before disposing its view: a stale termination from the
        // previous call must not push this new join into Reconnecting.
        unregisterBroadcastReceiver()
        hasConferenceConfirmation = false
        if (!isRestoredView) {
            hasConnectedConference = false
            reconnectAttemptTracker.reset()
            callStartTracker.reset()
        }
        releaseActiveView()
        sessionGeneration += 1
        teardownGeneration = Long.MAX_VALUE
        currentServerUrl = activeServerUrl
        currentRoomName = roomName
        isLeaving = false
        activeView = view
        cancelHangUpTimeouts()
        cancelErrorIdleTimeout()
        _isMuted.value = isMuted
        resetParticipantSession()

        registerBroadcastReceiver(view.context.applicationContext, expectedEpoch = viewEpoch)
        registerNetworkCallback(view.context.applicationContext)

        cancelReconnectTimeout()
        reconnectPaused = false
        _callState.value = if (isRestoredView) {
            CallState.Reconnecting(roomName, activeServerUrl)
        } else {
            CallState.Connecting(roomName, activeServerUrl)
        }

        try {
            val options = buildConferenceOptions(
                roomName = roomName,
                serverUrl = activeServerUrl,
                displayName = displayName,
                isMuted = isMuted,
                token = token,
            )

            if (isRestoredView) {
                Timber.i("Rejoining audio-only room '$roomName' with a fresh Jitsi view")
            } else {
                Timber.i("Joining embedded audio-only room '$roomName' on '$activeServerUrl'")
            }
            view.join(options)
            if (isRestoredView) {
                scheduleReconnectTimeout(roomName, REJOIN_COMPLETION_TIMEOUT_MS)
            } else {
                scheduleConnectTimeout(roomName)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to join room '$roomName'")
            failCall(CallState.Error(messageResId = R.string.call_error_connect_timeout))
        }
    }

    /**
     * Reports whether the embedded view may join a conference. A view recreated during a hang-up
     * is left attached but is not allowed to start a new Jitsi session.
     *
     * @return `true` when the manager is not currently tearing a call down.
     */
    fun canJoinConference(): Boolean = (!isLeaving) && (_callState.value !is CallState.Ending)

    /**
     * Called when the embedded view is released / removed from the UI hierarchy.
     *
     * The manager owns native cleanup: it aborts and disposes the view before resetting local
     * state, so a late termination callback cannot be dropped by a release that ran too early.
     *
     * @param view the view being released, or `null` to release the manager's current view.
     */
    fun onViewReleased(view: JitsiMeetView? = null) {
        if ((view != null) && (activeView != null) && (activeView !== view)) {
            Timber.d("Ignoring release for a view that is no longer owned by the manager")
            return
        }
        val pendingRelease = classifyForcedRejoinViewRelease(
            isPendingView = (view != null) && (pendingForcedRejoinView === view),
            isActiveView = activeView === view,
        )
        if (pendingRelease.consumeRelease) {
            pendingForcedRejoinView = null
            if (pendingRelease.releaseNativeView) {
                // Compose disposed the old view before the replacement's factory ran. Clean up the
                // native conference but keep the app-level call session alive for the new view.
                releaseActiveView()
                resetParticipantSession()
                hasConferenceConfirmation = false
            }
            return
        }
        releaseActiveView()
        if ((isLeaving) && (_callState.value is CallState.Ending)) {
            finishHangUp()
            return
        }
        finalizeSession()
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
        if (!canChangeMute(isLeaving, _callState.value)) return _isMuted.value
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
        if (!canChangeMute(isLeaving, _callState.value)) return
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
     * Asks Jitsi to leave the conference and keeps the call screen mounted until the SDK confirms
     * the leave. Disposing the embedded view before HANG_UP is processed drops the external-API
     * event and leaves the local microphone capture alive until the whole app is killed.
     *
     * @param immediate `true` to skip the confirmation window and release everything synchronously;
     *   used only when the hosting Activity is being destroyed for real.
     * @return `true` when a teardown is in progress and the caller must wait for [CallState.Idle]
     *   before removing the call screen, `false` when no call was active.
     */
    fun hangUp(immediate: Boolean = false): Boolean {
        if ((isLeaving) && (_callState.value is CallState.Ending)) {
            Timber.d("Hang-up already in progress, ignoring duplicate request")
            return true
        }
        val room = currentRoomName
        if (room == null &&
            (_callState.value is CallState.Idle || _callState.value is CallState.Error)
        ) {
            Timber.d("No active Jitsi call to leave")
            return false
        }

        Timber.i("Leaving Jitsi call for room '$room'")
        isLeaving = true
        teardownGeneration = sessionGeneration
        // Show the microphone as muted for the whole teardown; the SDK is asked to stop capture
        // below, but its acknowledgement can arrive after the UI has already been released.
        _isMuted.value = true
        resetParticipantSession()
        cancelConnectTimeout()
        cancelReconnectTimeout()
        cancelHangUpTimeouts()
        cancelErrorIdleTimeout()
        reconnectPaused = false
        pendingForcedRejoinView = null
        isAwaitingRestoredView = false
        reconnectAttemptTracker.reset()
        unregisterNetworkCallback()
        // Publish Ending before sending the external API events so a fast acknowledgement cannot
        // be applied to a state that still looks like an active call.
        _callState.value = CallState.Ending
        sendBroadcast(BroadcastIntentHelper.buildSetAudioMutedIntent(true))
        sendBroadcast(BroadcastIntentHelper.buildHangUpIntent())
        if (immediate) {
            Timber.i("Releasing Jitsi view immediately while the host is being destroyed")
            finishHangUp()
        } else {
            scheduleHangUpConfirmationTimeout(teardownGeneration)
        }
        return true
    }

    /**
     * Finalizes a hang-up after Jitsi emitted a termination event or the fallback deadline elapsed.
     *
     * Receivers and timeouts are released here rather than in [hangUp]: the SDK still needs a
     * chance to publish its termination event while the embedded view remains attached.
     */
    private fun finishHangUp() {
        if (!isLeaving && _callState.value !is CallState.Ending) return
        cancelHangUpTimeouts()
        releaseActiveView()
        finalizeSession()
        _callState.value = CallState.Idle
    }

    /**
     * Tears down a failed call while keeping its error message visible briefly.
     *
     * The Jitsi view, broadcast receiver and network callback are released immediately so a failed
     * connection cannot keep the microphone, audio route or foreground service alive. The state
     * changes to [CallState.Idle] after [ERROR_DISPLAY_TIMEOUT_MS] so the rider can read the error
     * before Navigation 3 removes the call route.
     *
     * @param error the error state carrying the message for the rider.
     */
    private fun failCall(error: CallState.Error) {
        if (_callState.value is CallState.Error) return
        Timber.e("Call failed: ${error.message ?: error.messageResId}")
        isLeaving = true
        cancelConnectTimeout()
        cancelReconnectTimeout()
        cancelHangUpTimeouts()
        cancelErrorIdleTimeout()
        reconnectPaused = false
        pendingForcedRejoinView = null
        isAwaitingRestoredView = false
        reconnectAttemptTracker.reset()
        unregisterNetworkCallback()
        sendBroadcast(BroadcastIntentHelper.buildSetAudioMutedIntent(true))
        sendBroadcast(BroadcastIntentHelper.buildHangUpIntent())
        releaseActiveView()
        finalizeSession()
        _callState.value = error
        scheduleIdleAfterError()
    }

    /**
     * Publishes [CallState.Idle] after a failed call so the call route is dismissed only once the
     * error message has been shown.
     */
    private fun scheduleIdleAfterError() {
        cancelErrorIdleTimeout()
        val runnable = Runnable {
            errorIdleTimeout = null
            if (_callState.value is CallState.Error) {
                _callState.value = CallState.Idle
            }
        }
        errorIdleTimeout = runnable
        mainHandler.postDelayed(runnable, ERROR_DISPLAY_TIMEOUT_MS)
    }

    /** Cancels a pending transition from a failed call back to [CallState.Idle]. */
    private fun cancelErrorIdleTimeout() {
        errorIdleTimeout?.let(mainHandler::removeCallbacks)
        errorIdleTimeout = null
    }

    /**
     * Releases the embedded Jitsi view exactly once.
     *
     * [JitsiMeetView.abort] clears the React props and [JitsiMeetView.dispose] frees the native
     * resources; both are needed before the manager forgets the view.
     */
    private fun releaseActiveView() {
        val view = activeView ?: return
        activeView = null
        try {
            view.abort()
        } catch (e: Exception) {
            Timber.w(e, "Failed to abort the Jitsi view")
        }
        try {
            view.dispose()
        } catch (e: Exception) {
            Timber.w(e, "Failed to dispose the Jitsi view")
        }
    }

    /**
     * Clears per-call state that must not leak into the next conference.
     *
     * Native view cleanup is handled separately by [releaseActiveView].
     */
    private fun finalizeSession() {
        cancelHangUpTimeouts()
        cancelConnectTimeout()
        cancelReconnectTimeout()
        cancelErrorIdleTimeout()
        reconnectPaused = false
        isLeaving = false
        teardownGeneration = Long.MAX_VALUE
        pendingForcedRejoinView = null
        isAwaitingRestoredView = false
        reconnectAttemptTracker.reset()
        hasConferenceConfirmation = false
        hasConnectedConference = false
        callStartTracker.reset()
        resetMuteState()
        unregisterBroadcastReceiver()
        unregisterNetworkCallback()
        currentRoomName = null
        resetParticipantSession()
    }

    /**
     * Bounds the asynchronous hang-up with two deadlines. The first resets the React props when
     * Jitsi sends no termination event; the second finally releases the UI if even that produced
     * no acknowledgement.
     *
     * @param token the generation the timeout belongs to; stale timers are ignored.
     */
    private fun scheduleHangUpConfirmationTimeout(token: Long) {
        cancelHangUpTimeouts()
        val confirmationTimeout = Runnable {
            if (!isCurrentHangUp(token)) return@Runnable
            Timber.w("Jitsi did not confirm hang-up, resetting the conference view")
            activeView?.abort()
            val abortTimeout = Runnable {
                if (isCurrentHangUp(token)) {
                    Timber.w("Forcing local call teardown after hang-up timeout")
                    finishHangUp()
                }
            }
            hangUpAbortTimeout = abortTimeout
            mainHandler.postDelayed(abortTimeout, HANG_UP_ABORT_TIMEOUT_MS)
        }
        hangUpConfirmationTimeout = confirmationTimeout
        mainHandler.postDelayed(confirmationTimeout, HANG_UP_CONFIRMATION_TIMEOUT_MS)
    }

    /**
     * Reports whether a timeout still belongs to the hang-up in progress.
     *
     * @param token the generation captured by the timer.
     * @return `true` while no newer call has superseded that teardown.
     */
    private fun isCurrentHangUp(token: Long): Boolean =
        isCurrentTeardown(token, teardownGeneration, isLeaving, _callState.value)

    /** Cancels both hang-up deadlines, ignoring timers that have already fired. */
    private fun cancelHangUpTimeouts() {
        hangUpConfirmationTimeout?.let(mainHandler::removeCallbacks)
        hangUpConfirmationTimeout = null
        hangUpAbortTimeout?.let(mainHandler::removeCallbacks)
        hangUpAbortTimeout = null
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
            // HelmChat owns audio mode, routing and the ducking focus request through
            // BluetoothAudioRouter. Jitsi must not take a second, conflicting focus.
            // Hiding Jitsi's audio-device picker prevents a route choice the native layer cannot apply.
            .setFeatureFlag("audio-focus.disabled", true)
            .setFeatureFlag("audio-device-button.enabled", false)

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
            .setFeatureFlag("audio-device-button.enabled", false)
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
     * updates. Events queued for an older view epoch are ignored.
     *
     * @param context any context; only its [LocalBroadcastManager] is used.
     * @param expectedEpoch the view generation this receiver belongs to.
     */
    private fun registerBroadcastReceiver(context: Context, expectedEpoch: Long) {
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
                    handleBroadcastEvent(BroadcastEvent(intent), expectedEpoch)
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
     * Keeps a first join alive until Jitsi reports [BroadcastEvent.Type.CONFERENCE_JOINED].
     * A stalled attempt creates a fresh view just like a dropped conference, because a rider with
     * no coverage may wait several minutes before the room becomes reachable. While offline the
     * deadline only polls cheaply and does not churn native views.
     *
     * @param roomName the room the timeout applies to; a stale timeout is ignored when the
     *   active room changed.
     * @param delayMs the delay before this check; shorter for the offline polling loop.
     */
    private fun scheduleConnectTimeout(roomName: String, delayMs: Long = CONNECT_TIMEOUT_MS) {
        cancelConnectTimeout()
        val runnable = Runnable {
            connectTimeout = null
            if (_callState.value !is CallState.Connecting || currentRoomName != roomName) return@Runnable
            if (shouldPostponeJoinRetry(isNetworkAvailable = _isNetworkAvailable.value)) {
                Timber.d("Still offline, postponing the first join retry for '$roomName'")
                scheduleConnectTimeout(roomName, CONNECT_RETRY_CHECK_MS)
                return@Runnable
            }
            Timber.w("Connection to room '$roomName' timed out, retrying with a fresh view")
            requestForcedRejoin(roomName)
        }
        connectTimeout = runnable
        mainHandler.postDelayed(runnable, delayMs)
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
     * that Jitsi emits before its own automatic reload kicks in. Offline time does not count
     * against the deadline; the watchdog resumes when the network returns.
     *
     * @param roomName the room the timeout applies to; a stale timeout is ignored when the
     *   active room changed.
     * @param timeoutMs the deadline, longer for a rejoin that is already in progress because
     *   Jitsi's conference-request backoff can reach two minutes.
     */
    private fun scheduleReconnectTimeout(
        roomName: String,
        timeoutMs: Long = RECONNECT_RELOAD_TIMEOUT_MS,
    ) {
        cancelReconnectTimeout()
        pendingReconnectRoom = roomName
        reconnectDeadline.start(timeoutMs)
        if (!_isNetworkAvailable.value) {
            val remainingMs = reconnectDeadline.pause()
            reconnectPaused = true
            Timber.i("Reconnect timeout for '$roomName' paused while offline ($remainingMs ms left)")
            return
        }
        reconnectPaused = false
        val runnable = Runnable {
            reconnectTimeout = null
            pendingReconnectRoom = null
            reconnectDeadline.clear()
            if (isReconnectWatchdogState(_callState.value) && currentRoomName == roomName) {
                handleReconnectTimeout(roomName)
            }
        }
        reconnectTimeout = runnable
        mainHandler.postDelayed(runnable, timeoutMs)
    }

    /**
     * Replaces the embedded Jitsi view so the SDK cannot treat the recovery as a no-op reload.
     *
     * Jitsi 13.1.1's `reloadNow()` navigates with the same room URL and returns early while the
     * conference object still exists, so a dropped call can wait forever behind the app's overlay.
     * Requesting a new `AndroidView` makes the SDK build a fresh conference. The call timer, room
     * and "has connected" history survive this replacement. The view epoch is advanced first so
     * callbacks already queued by the old view are ignored. This may be repeated indefinitely while
     * the rider keeps the call screen open.
     *
     * @param roomName the room whose watchdog expired.
     * @return `true` when a fresh view was requested; `false` when the call is no longer eligible.
     */
    internal fun requestForcedRejoin(roomName: String): Boolean {
        val view = activeView
        val shouldForce = shouldForceFreshJitsiView(
            timedOutRoom = roomName,
            currentRoomName = currentRoomName,
            callState = _callState.value,
            isLeaving = isLeaving,
            hasActiveView = view != null,
        )
        if (!shouldForce) return false

        reconnectAttemptTracker.record(eligible = shouldForce)
        Timber.w(
            "Reconnect to '$roomName' stalled, forcing fresh Jitsi view " +
                "(attempt ${reconnectAttemptTracker.attempts})",
        )
        viewEpoch += 1
        hasConferenceConfirmation = false
        resetParticipantSession()
        isAwaitingRestoredView = true
        pendingForcedRejoinView = view
        // Ignore teardown events and snapshots from the old view while it is being disposed.
        unregisterBroadcastReceiver()
        cancelConnectTimeout()
        cancelReconnectTimeout()
        reconnectPaused = false
        _callState.value = CallState.Reconnecting(roomName, currentServerUrl)
        // Guard against a Compose host that never recreates the view (for example the app is in the
        // background). The new join cancels this deadline and re-arms it for its own attempt.
        scheduleReconnectTimeout(roomName, REJOIN_COMPLETION_TIMEOUT_MS)
        _viewRecreationToken.value += 1
        return true
    }

    /**
     * Applies an expired reconnect watchdog by scheduling another attempt, never by failing the
     * call. The rider can stay disconnected for minutes; only the leave button ends the session.
     *
     * @param roomName the room whose reconnect attempt timed out.
     */
    private fun handleReconnectTimeout(roomName: String) {
        if (requestForcedRejoin(roomName)) {
            Timber.i("Continued reconnect to '$roomName' with a fresh Jitsi view")
            return
        }
        // The state may have changed or the view may be temporarily unavailable. Keep the call
        // screen and watchdog alive instead of turning a long outage into a terminal failure.
        Timber.w("Could not replace the Jitsi view for '$roomName' yet, checking again shortly")
        scheduleReconnectTimeout(roomName, CONNECT_RETRY_CHECK_MS)
    }

    /**
     * Cancels a pending reconnect timeout, forgets its room and drops any paused remainder.
     */
    private fun cancelReconnectTimeout() {
        removeReconnectTimeoutRunnable()
        reconnectDeadline.clear()
    }

    /**
     * Removes only the posted timeout runnable and its room, leaving the deadline object untouched.
     * Used when pausing so the remaining time can be read after the runnable is removed.
     */
    private fun removeReconnectTimeoutRunnable() {
        reconnectTimeout?.let(mainHandler::removeCallbacks)
        reconnectTimeout = null
        pendingReconnectRoom = null
    }

    /**
     * Stops the reconnect countdown without losing the room being tracked, so the deadline can
     * restart for its remaining duration once connectivity is back.
     */
    private fun pauseReconnectTimeout() {
        val room = pendingReconnectRoom
        val remainingMs = reconnectDeadline.pause()
        removeReconnectTimeoutRunnable()
        if (room == null) {
            reconnectDeadline.clear()
            return
        }
        reconnectPaused = true
        pendingReconnectRoom = room
        Timber.i("Reconnect timeout paused with $remainingMs ms left while offline")
    }

    /**
     * Requests an authoritative participant snapshot from the SDK once the conference is joined.
     *
     * The [BroadcastEvent.Type.PARTICIPANT_JOINED] / [BroadcastEvent.Type.PARTICIPANT_LEFT]
     * stream stays the primary source, but the snapshot also includes riders who were already in
     * the room and repairs any event the app might have missed. The request is fire-and-forget,
     * so [ParticipantTracker] bounds it with a deadline and falls back to the event stream when
     * the SDK never answers. The service only exists once the Jitsi React context has been
     * created, so it is treated as optional. A response from a replaced view epoch is ignored.
     */
    private fun refreshParticipantCount() {
        val service = ParticipantsService.getInstance() ?: return
        val request = participantTracker.onSnapshotRequested() ?: return
        val expectedEpoch = viewEpoch

        val callback = ParticipantsService.ParticipantsInfoCallback { participants ->
            if (!isCurrentViewEpoch(expectedEpoch, viewEpoch)) {
                Timber.d("Ignoring participant snapshot from an obsolete Jitsi view epoch")
                return@ParticipantsInfoCallback
            }
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
     * Events from a replaced view or after a failed call are discarded so a late termination cannot
     * revive a call or overwrite the fresh view's state.
     *
     * @param event the event to apply.
     * @param eventEpoch the view generation that delivered the event.
     */
    private fun handleBroadcastEvent(event: BroadcastEvent, eventEpoch: Long) {
        if (!isCurrentViewEpoch(eventEpoch, viewEpoch)) {
            Timber.d("Ignoring ${event.type} from an obsolete Jitsi view epoch")
            return
        }
        if (_callState.value is CallState.Error) {
            Timber.d("Ignoring ${event.type} after the call failed")
            return
        }
        if (shouldIgnoreEventWhileLeaving(event)) return
        when (event.type) {
            BroadcastEvent.Type.CONFERENCE_WILL_JOIN -> handleConferenceWillJoin()
            BroadcastEvent.Type.CONFERENCE_JOINED -> handleConferenceJoined()
            BroadcastEvent.Type.CONFERENCE_TERMINATED,
            BroadcastEvent.Type.READY_TO_CLOSE,
            -> {
                handleConferenceTerminated()
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
     * Reports whether an event belongs to a teardown or to a stale pre-join conference and must be
     * ignored. The confirmation event is still delivered so hang-up can finish.
     *
     * @param event the Jitsi event to classify.
     * @return `true` when the event must be dropped.
     */
    private fun shouldIgnoreEventWhileLeaving(event: BroadcastEvent): Boolean {
        // Keep the receiver registered during hang-up so the confirmation can arrive, but ignore
        // every other queued event from the conference that is being torn down.
        if (isLeaving && !isHangUpConfirmation(event.type)) {
            Timber.d("Ignoring ${event.type} while leaving the conference")
            return true
        }
        if (shouldIgnoreTerminationBeforeJoin(isLeaving, hasConferenceConfirmation, event.type)) {
            Timber.d("Ignoring stale ${event.type} before the new conference joined")
            return true
        }
        return false
    }

    /**
     * Handles Jitsi's `WILL_JOIN` event, keeping the short connect timeout for a first join and
     * handing over to the longer rejoin watchdog only after a confirmed conference reloads.
     */
    private fun handleConferenceWillJoin() {
        val handsOverDeadline = handsOverJoinDeadlineToRejoin(
            type = BroadcastEvent.Type.CONFERENCE_WILL_JOIN,
            callState = _callState.value,
            hasConnectedConference = hasConnectedConference,
        )
        if (handsOverDeadline) cancelConnectTimeout()
        hasConferenceConfirmation = true
        cancelReconnectTimeout()
        val room = currentRoomName ?: return
        if (_callState.value is CallState.Connected) return
        _callState.value = CallState.Connecting(room, currentServerUrl)
        if (handsOverDeadline) {
            scheduleReconnectTimeout(room, REJOIN_COMPLETION_TIMEOUT_MS)
        }
    }

    /**
     * Applies a confirmed conference join and refreshes the authoritative participant snapshot.
     */
    private fun handleConferenceJoined() {
        cancelConnectTimeout()
        cancelReconnectTimeout()
        if (!isParticipantSessionLive(currentRoomName, isLeaving, _callState.value)) {
            Timber.i("Ignoring CONFERENCE_JOINED for a session that is no longer live")
            return
        }
        val room = currentRoomName ?: return
        hasConferenceConfirmation = true
        hasConnectedConference = true
        reconnectAttemptTracker.reset()
        callStartTracker.onConferenceJoined()
        _callState.value = CallState.Connected(room, currentServerUrl)
        participantTracker.onConferenceJoined()
        refreshParticipantCount()
    }

    /**
     * Handles an unexpected conference termination, either finishing a hang-up or arming the
     * reload watchdog while Jitsi restores the conference on its own.
     */
    private fun handleConferenceTerminated() {
        if (isLeaving) {
            Timber.i("Jitsi confirmed the hang-up")
            finishHangUp()
            return
        }
        if (isTerminalCallState(_callState.value)) {
            Timber.d("Ignoring termination in terminal call state ${_callState.value::class.simpleName}")
            return
        }
        cancelConnectTimeout()
        resetParticipantSession()
        hasConferenceConfirmation = false
        val room = currentRoomName
        if (room == null) {
            cancelReconnectTimeout()
            callStartTracker.reset()
            _callState.value = CallState.Idle
            return
        }
        // Jitsi reloads the conference on its own after a transient drop; stay on the call screen
        // and show the reconnecting state instead of navigating home.
        Timber.i("Conference for room '$room' ended unexpectedly, waiting for reload")
        _callState.value = CallState.Reconnecting(room, currentServerUrl)
        scheduleReconnectTimeout(room)
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
): Boolean = roomName != null && !isLeaving &&
    callState !is CallState.Idle && callState !is CallState.Ending

/**
 * Reports whether a Jitsi broadcast is the acknowledgement that a requested hang-up completed.
 *
 * Only these two events are allowed through while the manager is in [CallState.Ending]; all other
 * events from the dying conference are discarded by the manager's event handler.
 *
 * @param type the Jitsi event type to classify.
 * @return `true` for a conference-termination or ready-to-close event.
 */
internal fun isHangUpConfirmation(type: BroadcastEvent.Type): Boolean =
    type == BroadcastEvent.Type.CONFERENCE_TERMINATED ||
        type == BroadcastEvent.Type.READY_TO_CLOSE

/**
 * Reports whether a termination event arrived before the current conference confirmed its join.
 *
 * Such an event belongs to a previous session and must not push a freshly started call into
 * `Reconnecting`.
 *
 * @param isLeaving `true` when the rider requested a hang-up.
 * @param hasConferenceConfirmation `true` after this session reported `CONFERENCE_WILL_JOIN` or
 *   `CONFERENCE_JOINED`.
 * @param type the Jitsi event type to classify.
 * @return `true` when the event should be ignored.
 */
internal fun shouldIgnoreTerminationBeforeJoin(
    isLeaving: Boolean,
    hasConferenceConfirmation: Boolean,
    type: BroadcastEvent.Type,
): Boolean = !isLeaving && !hasConferenceConfirmation && isHangUpConfirmation(type)

/**
 * Reports whether a state still needs the reconnect watchdog to be armed.
 *
 * Jitsi reports `Connecting` when its own reload calls `CONFERENCE_WILL_JOIN`, so both transient
 * states must be covered; otherwise a reload that stalls before `CONFERENCE_JOINED` could keep the
 * rider on the call screen forever.
 *
 * @param callState the current call state.
 * @return `true` for [CallState.Reconnecting] and [CallState.Connecting].
 */
internal fun isReconnectWatchdogState(callState: CallState): Boolean =
    callState is CallState.Reconnecting || callState is CallState.Connecting

/**
 * Reports whether a call state is terminal and must not be revived by a late Jitsi event.
 *
 * [CallState.Ending] is terminal for ordinary events; only the explicit hang-up confirmation is
 * allowed through by the manager while it is active.
 *
 * @param callState the state to classify.
 * @return `true` for [CallState.Idle], [CallState.Ending] and [CallState.Error].
 */
internal fun isTerminalCallState(callState: CallState): Boolean =
    callState is CallState.Idle || callState is CallState.Ending || callState is CallState.Error

/**
 * Reports whether a WILL_JOIN event transfers the join deadline from the short connect timeout to
 * the longer rejoin watchdog. Jitsi emits WILL_JOIN on a plain first join as well, so only a reload
 * after a conference has actually connected may cancel the 30-second connect timeout.
 *
 * @param type the Jitsi event type to classify.
 * @param callState the state at the moment the event arrived.
 * @param hasConnectedConference whether this join attempt has already reported CONFERENCE_JOINED.
 * @return `true` when the connect timeout must be cancelled because the watchdog takes over.
 */
internal fun handsOverJoinDeadlineToRejoin(
    type: BroadcastEvent.Type,
    callState: CallState,
    hasConnectedConference: Boolean,
): Boolean = type == BroadcastEvent.Type.CONFERENCE_WILL_JOIN &&
    (callState is CallState.Reconnecting ||
        (callState is CallState.Connecting && hasConnectedConference))

/**
 * Reports whether a first-join retry must wait for a network instead of replacing the Jitsi view.
 *
 * @param isNetworkAvailable whether the device currently has a default network.
 * @return `true` while offline, so the retry loop only polls until coverage returns.
 */
internal fun shouldPostponeJoinRetry(isNetworkAvailable: Boolean): Boolean = !isNetworkAvailable

/**
 * Reports whether a callback still belongs to the embedded view that produced it.
 *
 * @param callbackEpoch the view generation captured when the callback was registered.
 * @param currentEpoch the manager's current view generation.
 * @return `true` while the callback may still affect the active view.
 */
internal fun isCurrentViewEpoch(callbackEpoch: Long, currentEpoch: Long): Boolean =
    callbackEpoch == currentEpoch

/**
 * Reports whether a join uses the replacement view requested by a forced rejoin.
 *
 * @param isAwaitingRestoredView `true` between a forced rejoin request and the next join.
 * @param currentRoomName the room still owned by the manager, or `null` when none is active.
 * @param joiningRoomName the room the new view is joining.
 * @return `true` when the call timer and connection history must be preserved.
 */
internal fun shouldPreserveCallSessionOnJoin(
    isAwaitingRestoredView: Boolean,
    currentRoomName: String?,
    joiningRoomName: String,
): Boolean = isAwaitingRestoredView && currentRoomName == joiningRoomName

/**
 * Reports whether restoring a default network should retry a first join immediately.
 *
 * @param wasOffline `true` when the previous network state was unavailable.
 * @param callState the current call state.
 * @param hasConnectedConference whether this call already confirmed a conference join.
 * @param hasPendingConnectTimeout whether a first-join deadline is still scheduled.
 * @return `true` when the stalled first join must be retried now.
 */
internal fun shouldRetryFirstJoinOnNetworkRestore(
    wasOffline: Boolean,
    callState: CallState,
    hasConnectedConference: Boolean,
    hasPendingConnectTimeout: Boolean,
): Boolean = wasOffline && hasPendingConnectTimeout &&
    callState is CallState.Connecting && !hasConnectedConference

/** How the manager must handle a view release observed during a forced rejoin. */
internal data class ForcedRejoinViewRelease(
    /** Whether the release belongs to the pending old view and must stop normal teardown. */
    val consumeRelease: Boolean,
    /** Whether the pending old view is still the manager's active view and needs native cleanup. */
    val releaseNativeView: Boolean,
)

/**
 * Classifies a view release relative to a pending forced rejoin.
 *
 * @param isPendingView `true` when the released view is the one awaiting replacement.
 * @param isActiveView `true` when the released view is still the manager's active view.
 * @return the actions required for this release.
 */
internal fun classifyForcedRejoinViewRelease(
    isPendingView: Boolean,
    isActiveView: Boolean,
): ForcedRejoinViewRelease = ForcedRejoinViewRelease(
    consumeRelease = isPendingView,
    releaseNativeView = isPendingView && isActiveView,
)

/**
 * Reports whether an expired watchdog should replace the embedded Jitsi view.
 *
 * Retries are intentionally unbounded while the same room is still active: a motorcycle intercom
 * may stay offline for many minutes. Only an explicit hang-up, a terminal state or a missing view
 * stops the retry chain.
 *
 * @param timedOutRoom the room whose watchdog expired.
 * @param currentRoomName the room still owned by the manager, or `null` when none is active.
 * @param callState the current call state.
 * @param isLeaving `true` when the rider requested a hang-up.
 * @param hasActiveView whether there is a view the manager can dispose.
 * @return `true` when a fresh view must be requested.
 */
internal fun shouldForceFreshJitsiView(
    timedOutRoom: String,
    currentRoomName: String?,
    callState: CallState,
    isLeaving: Boolean,
    hasActiveView: Boolean,
): Boolean = !isLeaving && hasActiveView &&
    timedOutRoom == currentRoomName && isReconnectWatchdogState(callState)

/**
 * Reports whether a hang-up timeout still belongs to the teardown in progress.
 *
 * @param token the generation captured when the timeout was scheduled.
 * @param currentToken the generation of the active teardown.
 * @param isLeaving `true` while the rider is leaving.
 * @param callState the current call state.
 * @return `true` when the timeout may still finalize the call.
 */
internal fun isCurrentTeardown(
    token: Long,
    currentToken: Long,
    isLeaving: Boolean,
    callState: CallState,
): Boolean = token == currentToken && isLeaving && callState is CallState.Ending

/**
 * Reports whether a mute command may still be applied to the current call.
 *
 * Commands issued during a hang-up are ignored so a late tap cannot overwrite the forced mute or
 * re-enable the microphone after teardown has begun.
 *
 * @param isLeaving `true` while the rider is leaving.
 * @param callState the current call state.
 * @return `true` when mute changes are accepted.
 */
internal fun canChangeMute(isLeaving: Boolean, callState: CallState): Boolean =
    !isLeaving && callState !is CallState.Ending

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
