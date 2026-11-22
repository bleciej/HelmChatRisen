package dev.communicator.helmchatrisen.audio

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.concurrent.Executors

/**
 * State of audio routing to helmet headsets or device speakers.
 */
sealed class AudioRoutingState {
    /** Audio is routed to the device speaker. */
    object SpeakerConnected : AudioRoutingState()

    /** Idle state before routing starts or after it stops; with no headset the built-in speaker is used instead. */
    object EarpieceConnected : AudioRoutingState()

    /** Bluetooth SCO is being negotiated with the helmet headset. */
    object ConnectingBluetooth : AudioRoutingState()

    /**
     * Audio is routed to the connected Bluetooth, wired or USB headset.
     *
     * @param deviceName the product name of the connected device, or `null` when it is unknown.
     */
    data class HeadsetConnected(val deviceName: String? = null) : AudioRoutingState()

    /**
     * Audio routing failed.
     *
     * @param message a human-readable description of the failure.
     */
    data class Error(val message: String) : AudioRoutingState()
}

/** `state` extra of [Intent.ACTION_HEADSET_PLUG]: the headset was unplugged. */
private const val HEADSET_PLUG_STATE_UNPLUGGED = 0

/** `state` extra of [Intent.ACTION_HEADSET_PLUG]: the headset was plugged in. */
private const val HEADSET_PLUG_STATE_PLUGGED = 1

/** Key of the `state` extra carried by [Intent.ACTION_HEADSET_PLUG]. */
private const val EXTRA_HEADSET_PLUG_STATE = "state"

/**
 * Delays between route-selection retries after a headset broadcast. Android can publish the plug
 * or profile event before the device appears in [AudioManager.getAvailableCommunicationDevices],
 * so the router retries for a few seconds instead of getting stuck on the phone speaker.
 */
private val ROUTE_RETRY_DELAYS_MS = longArrayOf(250L, 500L, 1_000L, 1_500L, 2_000L, 3_000L)

/**
 * Translates the wired headset plug broadcast into a connection flag. Audio-device enumeration can
 * lag behind the plug broadcast, so the extra is used directly instead of re-querying the router.
 *
 * @param state the value of the broadcast's `state` extra.
 * @return `true` when plugged in, `false` when unplugged, or `null` for an unknown state.
 */
internal fun wiredHeadsetConnectionState(state: Int): Boolean? = when (state) {
    HEADSET_PLUG_STATE_PLUGGED -> true
    HEADSET_PLUG_STATE_UNPLUGGED -> false
    else -> null
}

/**
 * Reports whether a Bluetooth profile broadcast says a headset just connected. Disconnects are not
 * forced to `false` because another headset may still be connected.
 *
 * @param state the value of [BluetoothProfile.EXTRA_STATE].
 * @return `true` when the profile entered the connected state.
 */
internal fun isBluetoothHeadsetConnected(state: Int): Boolean =
    state == BluetoothProfile.STATE_CONNECTED

/**
 * Queries whether a Bluetooth HFP headset is connected. Profile queries are best-effort: a device
 * may not implement HFP, the permission may be revoked mid-call, or the adapter may be unavailable,
 * so a failed query must never crash call start. A failure is reported as `null` instead of `false`
 * because "the query failed" must not be mistaken for "no headset is connected": the router still
 * detects a helmet through the enumerated SCO device and must not latch a false disconnect.
 * A2DP-only music outputs are ignored because Android cannot use them for two-way call audio.
 *
 * @param connectedDevices returns the devices connected for a profile, or `null` when unknown.
 * @return `true` when HFP reports a headset, `false` when it reports none, or `null` when the query
 *   failed or the profile is unavailable.
 */
internal fun queryConnectedBluetoothHeadset(
    connectedDevices: (profile: Int) -> Collection<*>?,
): Boolean? {
    return try {
        connectedDevices(BluetoothProfile.HEADSET).orEmpty().isNotEmpty()
    } catch (e: RuntimeException) {
        // Expected on devices without HFP (and during a BLUETOOTH_CONNECT permission race).
        // Device callbacks re-query often, so keep the log to one line instead of a stack trace.
        Timber.w(
            "Bluetooth profile ${BluetoothProfile.HEADSET} is unavailable: ${e.message}",
        )
        null
    }
}

/**
 * Audio and Bluetooth SCO routing helper for helmet intercom headsets (Sena, Cardo, Packtalk, etc.)
 *
 * Jitsi's own `AudioModeModule` audio focus handling is disabled (`audio-focus.disabled`), so
 * this router owns the in-call audio mode and the helmet route. It deliberately does not request
 * audio focus and does not explicitly duck other apps: radio/music keeps its own focus and is not
 * paused by this intercom. Jitsi opens a `USAGE_VOICE_COMMUNICATION` playback track only when a
 * remote participant is present; some OEM audio policies use that track to move `STREAM_MUSIC`
 * with the call route. To keep the toggle identical with one rider, this router holds a silent
 * voice-communication track for the whole call. It carries no audible samples and takes no focus.
 * `MODE_IN_COMMUNICATION` remains required for WebRTC voice processing and communication-device
 * priority, but Android/OEM policies may independently attenuate `STREAM_MUSIC` in this mode.
 * Background-media level must therefore be verified on target devices rather than assumed from
 * the absence of a focus request.
 */
class BluetoothAudioRouter private constructor(private val context: Context) {

    companion object {

        // Only the application context is retained, so the singleton cannot leak an Activity.
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: BluetoothAudioRouter? = null

        /**
         * Returns the process-wide router, creating it on first use.
         *
         * @param context any context; only its application context is retained.
         * @return the shared [BluetoothAudioRouter] instance.
         */
        fun getInstance(context: Context): BluetoothAudioRouter {
            return instance ?: synchronized(this) {
                instance ?: BluetoothAudioRouter(context.applicationContext).also { instance = it }
            }
        }
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Profile queries are binder calls and must not run on the main thread. A dedicated daemon
     * thread keeps the cache warm while `detectHeadsetKind()` stays allocation-free and synchronous.
     */
    private val profileQueryExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "helm-bluetooth-profile").apply { isDaemon = true }
    }

    /** Last HFP availability observed on the main thread; never written by the query thread. */
    @Volatile
    private var cachedBluetoothHeadsetConnected = false

    /** Whether a background profile query is already in flight, so broadcasts cannot flood it. */
    private var profileRefreshPending = false

    /** Invalidates profile results captured before a newer headset event or call transition. */
    private var profileRefreshGeneration = 0L

    /** Whether a disconnect broadcast still needs to be applied to the next query result. */
    private var pendingBluetoothDisconnect = false

    private val _routingState = MutableStateFlow<AudioRoutingState>(AudioRoutingState.EarpieceConnected)
    val routingState: StateFlow<AudioRoutingState> = _routingState.asStateFlow()

    private val _isHeadsetConnected = MutableStateFlow(value = false)

    /** Whether a helmet intercom or headset is currently available for call audio. */
    val isHeadsetConnected: StateFlow<Boolean> = _isHeadsetConnected.asStateFlow()

    private var isRoutingActive = false
    private val routePreference = RoutePreference()

    /**
     * Latched decision from a confirmed HFP disconnect. It must survive the stale SCO broadcasts
     * produced while stopping the channel, and is cleared only by a fresh positive connection.
     */
    private var ignoreStaleBluetoothSignals = false

    private var isReceiverRegistered = false
    private var isAudioDeviceCallbackRegistered = false

    // The broadcast often arrives before the device is visible to AudioManager. Remembering the
    // family keeps the route button correct while the retry loop waits for enumeration to catch up.
    private var currentHeadsetKind = HeadsetKind.NONE

    /**
     * Device name from the last successful headset route. A transient SCO error while music keeps
     * A2DP busy must not replace the named route with the generic intercom label.
     */
    private var lastConnectedHeadsetName: String? = null
    private var bluetoothScoStartRequested = false

    /**
     * Silent voice-communication playback that keeps OEM call-route policy active even when the
     * rider is alone in the Jitsi room and no remote audio track exists.
     */
    private var silentCommunicationTrack: AudioTrack? = null

    /** Daemon thread that keeps the silent voice-communication track supplied with zero samples. */
    private var silentCommunicationWriter: Thread? = null

    /**
     * Set immediately before this router stops SCO itself so the resulting DISCONNECTED broadcast is
     * not mistaken for the headset going away. Without it the speaker toggle triggers an HFP refresh
     * with a disconnect flag; an empty lookup then drops the still-connected headset from the app.
     */
    private var isScoStopIntentional = false
    private var routeRetryRunnable: Runnable? = null
    private var routeRetryAttempt = 0

    /** Invalidates retry callbacks belonging to an earlier headset event or call. */
    private var routingGeneration = 0L

    /**
     * Tracks audio-device enumeration directly. Headset plug broadcasts are not emitted for every
     * USB audio device, and the device list can change slightly after a Bluetooth profile connects.
     */
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        /**
         * Re-evaluates the active route when the platform publishes newly connected devices.
         *
         * @param addedDevices the devices that just became available.
         */
        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
            if (!isRoutingActive) return
            Timber.d("Audio devices added: ${addedDevices.map { it.type }}")
            val addedKind = headsetKindFromAudioDevices(addedDevices.map(AudioDeviceInfo::toCandidate))
            if (addedKind == HeadsetKind.BLUETOOTH) {
                // A published SCO device is a new channel; an older local-stop token is obsolete.
                isScoStopIntentional = false
            }
            if (!shouldReevaluateAfterDevicesAdded(
                    currentKind = currentHeadsetKind,
                    addedKind = addedKind,
                )
            ) {
                return
            }
            // The batch may contain only one family. Pass it as a hint so a late Bluetooth SCO
            // device still wins, while updateAudioRouting() runs the full query and preserves the
            // retry attempt index instead of restarting the Bluetooth timeout on every callback.
            updateAudioRouting(
                forcedHeadsetKind = addedKind,
                resetRouteRetryAttempt = false,
            )
        }

        /**
         * Re-evaluates the active route when a device disappears.
         *
         * @param removedDevices the devices that are no longer available.
         */
        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
            if (!isRoutingActive) return
            Timber.d("Audio devices removed: ${removedDevices.map { it.type }}")
            val removed = removedDevices.map(AudioDeviceInfo::toCandidate)
            val remaining = currentOutputDevices()
            if (isExpectedLocalScoStopRemoval(
                    removedDevices = removed,
                    remainingDevices = remaining,
                    scoStopRequestedLocally = isScoStopIntentional,
                )
            ) {
                Timber.d("Ignoring the SCO device removal caused by the local speaker switch")
                return
            }
            if (shouldConfirmBluetoothDisconnectFromDevices(removed, remaining)) {
                confirmBluetoothDisconnectFromDevices()
            }
            updateAudioRouting(
                headsetDisconnected = headsetKindFromAudioDevices(removed) != null,
            )
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        // The SCO broadcast API is deprecated but is the only option below API 31.
        /**
         * Keeps headset availability and the routing state in sync with SCO, Bluetooth and wired changes.
         * Broadcasts that arrive while routing is inactive (during teardown or between calls) are
         * ignored so a late SCO disconnect cannot restart a retry loop for a finished call.
         *
         * @param receiverContext the context the receiver was registered with.
         * @param intent the broadcast being delivered, or `null` when it carries no data.
         */
        @Suppress("DEPRECATION")
        override fun onReceive(receiverContext: Context?, intent: Intent?) {
            if (!isRoutingActive) return
            when (intent?.action) {
                AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED -> handleScoStateChanged(intent)
                AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                    // Keep the disconnect hint for route detection: this broadcast can report a real
                    // headset removal. The exception is the noisy event emitted when this router
                    // stops SCO for a speaker switch; the still-connected HFP profile is the route
                    // authority in that case.
                    val hasScoOutput = hasBluetoothScoDevice(currentOutputDevices())
                    if (shouldIgnoreNoisyAudioAfterLocalScoStop(
                            scoStopRequestedLocally = isScoStopIntentional,
                            hasScoOutput = hasScoOutput,
                        )
                    ) {
                        Timber.d("Ignoring noisy audio caused by the local SCO stop")
                        return
                    }
                    Timber.w("Audio becoming noisy (headset disconnected)")
                    val bluetoothHeadsetLost = ((currentHeadsetKind == HeadsetKind.BLUETOOTH) &&
                        !hasScoOutput)
                    if (bluetoothHeadsetLost) {
                        confirmBluetoothDisconnectFromDevices()
                    }
                    updateAudioRouting(headsetDisconnected = true)
                }
                Intent.ACTION_HEADSET_PLUG -> {
                    val state = intent.getIntExtra(EXTRA_HEADSET_PLUG_STATE, -1)
                    Timber.d("Wired headset plug state changed: $state")
                    // A plug event is an authoritative physical change, not a SCO stop side effect.
                    isScoStopIntentional = false
                    updateAudioRouting(
                        forcedHeadsetKind = headsetKindFromWiredPlug(state),
                        headsetDisconnected = wiredHeadsetConnectionState(state) == false,
                    )
                }
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(
                        BluetoothProfile.EXTRA_STATE,
                        BluetoothProfile.STATE_DISCONNECTED,
                    )
                    Timber.d("Bluetooth headset profile state changed: $state")
                    // HFP profile changes are authoritative physical events. They must not be
                    // absorbed as side effects of an earlier local SCO stop.
                    isScoStopIntentional = false
                    if (shouldRefreshBluetoothProfileAfterEvent(state)) {
                        // A disconnect may leave another HFP headset attached. Refresh off the main
                        // thread and apply the disconnect only when the new lookup confirms it.
                        refreshBluetoothHeadsetCache(
                            headsetDisconnected = state == BluetoothProfile.STATE_DISCONNECTED,
                        )
                    } else {
                        // A connected event is authoritative; drop any lookup started before it and
                        // let Bluetooth signals select the route again for the returning helmet.
                        invalidateProfileLookup()
                        ignoreStaleBluetoothSignals = false
                        cachedBluetoothHeadsetConnected = true
                        updateAudioRouting(
                            forcedHeadsetKind = headsetKindFromBluetoothProfile(state),
                        )
                    }
                }
            }
        }
    }

    /**
     * Applies an SCO state change only when a headset is genuinely connected. Without one the
     * phone speaker stays active and SCO chatter must not show a "connecting to intercom" state.
     * A confirmed SCO connection is itself proof of a Bluetooth headset, even if profile lookups
     * are stale or unsupported.
     *
     * @param intent the SCO broadcast carrying the new state.
     */
    @Suppress("DEPRECATION")
    private fun handleScoStateChanged(intent: Intent) {
        val state = intent.getIntExtra(
            AudioManager.EXTRA_SCO_AUDIO_STATE,
            AudioManager.SCO_AUDIO_STATE_DISCONNECTED,
        )
        val expectedDisconnect = isExpectedScoDisconnect(
            state = state,
            scoStopRequestedLocally = isScoStopIntentional,
        )
        // Intermediate SCO states must not consume the local-stop token: the DISCONNECTED broadcast
        // generated by stopBluetoothSco() can arrive after CONNECTING/CONNECTED chatter.
        if (shouldRetireScoStopToken(state)) {
            isScoStopIntentional = false
        }
        Timber.d("SCO audio state updated: $state (appInitiated=$expectedDisconnect)")
        routingGeneration += 1
        cancelRouteRetry()
        if (!expectedDisconnect) {
            refreshProfileAfterScoDisconnect(state)
        }
        // Keep the last known family when this router closed SCO on purpose. A fresh detection can
        // legitimately return NONE at that instant even though the headset is still paired, which
        // would otherwise hide the route button until the rider restarts Bluetooth.
        val detectedKind = if (expectedDisconnect && (currentHeadsetKind != HeadsetKind.NONE)) {
            currentHeadsetKind
        } else {
            detectKindForScoState(state)
        }
        if (!expectedDisconnect) {
            clearSpeakerOverrideAfterScoEvent(state, detectedKind)
        }
        publishDetectedHeadset(detectedKind)
        applyRouteForScoState(state, detectedKind)
    }

    /**
     * Re-checks the HFP profile after SCO disconnects so a stale cache cannot mark a helmet as
     * available. Other SCO states leave the cached profile untouched.
     *
     * @param state the new SCO audio state.
     */
    @Suppress("DEPRECATION")
    private fun refreshProfileAfterScoDisconnect(state: Int) {
        if (state == AudioManager.SCO_AUDIO_STATE_DISCONNECTED) {
            refreshBluetoothHeadsetCache(headsetDisconnected = true)
        }
    }

    /**
     * Derives the headset family from an SCO broadcast. A confirmed SCO connection proves that a
     * Bluetooth headset exists even when the profile cache is stale or unsupported, and clears any
     * stale-signal suppression left by an earlier disconnect.
     *
     * @param state the new SCO audio state.
     * @return the headset family to publish.
     */
    @Suppress("DEPRECATION")
    private fun detectKindForScoState(state: Int): HeadsetKind {
        if (state != AudioManager.SCO_AUDIO_STATE_CONNECTED) return detectHeadsetKind()
        invalidateProfileLookup()
        ignoreStaleBluetoothSignals = false
        cachedBluetoothHeadsetConnected = true
        return HeadsetKind.BLUETOOTH
    }

    /**
     * Clears a latched speaker choice only when an SCO disconnect leaves no headset attached. A
     * CONNECTING, CONNECTED or ERROR broadcast must never reset the rider's route choice; it can be
     * part of a channel still being negotiated while the phone speaker remains authoritative.
     *
     * @param state the new SCO audio state.
     * @param kind the headset family still detected after the SCO event.
     */
    private fun clearSpeakerOverrideAfterScoEvent(state: Int, kind: HeadsetKind) {
        if (!isScoDisconnectState(state)) return
        if (routePreference.clearOnDisconnect(headsetDisconnected = true, kind = kind)) {
            stopBluetoothSco()
        }
    }

    /** Returns the output devices currently available to the platform. */
    private fun currentOutputDevices(): List<AudioDeviceCandidate> =
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .map(AudioDeviceInfo::toCandidate)

    /**
     * Drops cached Bluetooth state when the platform device list proves the last SCO output is gone.
     * On devices where the HFP query always fails, this is the only way to stop the router from
     * treating a physically disconnected helmet as still attached. The real connection broadcasts
     * clear [ignoreStaleBluetoothSignals] again when the intercom returns.
     */
    private fun confirmBluetoothDisconnectFromDevices() {
        if (cachedBluetoothHeadsetConnected || !ignoreStaleBluetoothSignals) {
            Timber.i("Bluetooth SCO output removed; confirming helmet disconnect")
        }
        cachedBluetoothHeadsetConnected = false
        ignoreStaleBluetoothSignals = true
    }

    /**
     * Publishes the headset family that will drive the current route.
     *
     * @param kind the detected headset family.
     */
    private fun publishDetectedHeadset(kind: HeadsetKind) {
        currentHeadsetKind = kind
        _isHeadsetConnected.value = kind != HeadsetKind.NONE
    }

    /**
     * Publishes a named headset route and remembers the name for transient SCO errors. The control
     * then keeps showing the helmet instead of the generic intercom label while the route retries.
     *
     * @param name the product name reported for the selected headset, or `null` when unknown.
     */
    private fun publishHeadsetRoute(name: String?) {
        name?.let { lastConnectedHeadsetName = it }
        _routingState.value = AudioRoutingState.HeadsetConnected(name)
    }

    /**
     * Selects the output after an SCO state change, preserving a rider's explicit speaker choice.
     *
     * @param state the new SCO audio state.
     * @param kind the detected headset family.
     */
    private fun applyRouteForScoState(state: Int, kind: HeadsetKind) {
        when (resolveRouteTarget(kind, routePreference.isSpeakerForced)) {
            RouteTarget.SPEAKER -> {
                // A rider who chose the speaker must not be pulled back to a headset by a late SCO
                // broadcast, including the disconnect caused by stopping SCO in that toggle.
                stopBluetoothSco()
                routeToBuiltInSpeakerOutput()
            }
            RouteTarget.HEADSET -> applyHeadsetRouteForScoState(state, kind)
        }
    }

    /**
     * Applies the headset route appropriate for an SCO state, reporting errors through the routing
     * state flow while keeping the watchdog retry bounded.
     *
     * @param state the new SCO audio state.
     * @param kind the detected headset family.
     */
    @Suppress("DEPRECATION")
    private fun applyHeadsetRouteForScoState(state: Int, kind: HeadsetKind) {
        when (state) {
            AudioManager.SCO_AUDIO_STATE_CONNECTED -> applyConnectedScoRoute()
            AudioManager.SCO_AUDIO_STATE_ERROR -> {
                // A2DP and SCO cannot coexist on many helmets, so a transient error while music is
                // playing must keep the named route visible while the retry restores the channel.
                Timber.w("Bluetooth SCO error with $kind attached; retrying the intercom route")
                _routingState.value = resolveScoErrorRoutingState(
                    kind = kind,
                    lastConnectedName = lastConnectedHeadsetName,
                )
                scheduleRouteRetry(kind)
            }
            else -> routeToHeadset(kind)
        }
    }

    /**
     * Applies a confirmed SCO connection without restarting an already active channel.
     */
    @Suppress("DEPRECATION")
    private fun applyConnectedScoRoute() {
        Timber.i("Bluetooth SCO connected to helmet intercom")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // A speaker device may have been selected explicitly while no headset was connected.
            // Keeping the flag set prevents a second startBluetoothSco() on a live channel.
            bluetoothScoStartRequested = true
            routeToHeadset(HeadsetKind.BLUETOOTH)
        } else {
            // Legacy routing follows the SCO channel itself; there is no communication device to
            // select and no retry to keep alive.
            cancelRouteRetry()
            bluetoothScoStartRequested = false
            publishHeadsetRoute("Helmet Headset")
        }
    }

    /**
     * Starts audio routing, enters communication mode once and starts the silent voice-communication
     * keep-alive. This app takes no audio focus and therefore does not explicitly duck or pause
     * background radio/music; platform call policies may still attenuate `STREAM_MUSIC` in
     * communication mode. The mode is not reasserted while the call runs, because repeated
     * transitions keep OEM audio policies ducking or pausing the media stream. The initial device
     * family is detected here so a headset connected before the call is routed without waiting for
     * another broadcast.
     */
    fun startAudioRouting() {
        if (isRoutingActive) {
            Timber.d("Audio routing is already active")
            return
        }

        // Publish the active flag before registering listeners so a broadcast delivered during
        // registration is processed instead of being mistaken for an event from a finished call.
        isRoutingActive = true
        isScoStopIntentional = false
        ignoreStaleBluetoothSignals = false
        registerReceivers()
        registerAudioDeviceCallback()

        enterCommunicationMode()
        if (shouldRestoreNormalAudioMode(audioManager.mode)) {
            startSilentCommunicationTrack()
        }
        currentHeadsetKind = detectHeadsetKind()

        refreshBluetoothHeadsetCache()
        updateAudioRouting()
    }

    /**
     * Enters communication mode once at call start. Telephony-owned modes and an already-correct
     * mode are left untouched, and no watchdog reasserts the mode later: repeated transitions make
     * OEM audio policies duck or pause background media during the intercom call.
     */
    private fun enterCommunicationMode() {
        if (!shouldEnterCommunicationMode(
                isRoutingActive = isRoutingActive,
                currentMode = audioManager.mode,
            )
        ) {
            return
        }
        Timber.i("Entering communication audio mode for the intercom call")
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
    }

    /**
     * Starts a silent `USAGE_VOICE_COMMUNICATION` playback track for the call. Jitsi only creates
     * that kind of playback track after another rider joins. Without it, OEM policies keep
     * `STREAM_MUSIC` on A2DP when the rider switches this call to the phone speaker. A daemon thread
     * streams only zero samples into the track, so it is inaudible and takes no audio focus.
     */
    private fun startSilentCommunicationTrack() {
        if (silentCommunicationTrack != null) return
        val track = try {
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SILENT_COMMUNICATION_SAMPLE_RATE_HZ)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val minBufferSize = AudioTrack.getMinBufferSize(
                SILENT_COMMUNICATION_SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(maxOf(silentCommunicationBufferSize(), minBufferSize))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) {
            Timber.w(e, "Could not create the silent voice-communication keep-alive")
            return
        }

        silentCommunicationTrack = track
        val silence = ByteArray(track.bufferSizeInFrames * BYTES_PER_SILENCE_SAMPLE)
        try {
            track.play()
            silentCommunicationWriter = Thread(
                {
                    try {
                        while (!Thread.currentThread().isInterrupted) {
                            if (track.write(silence, 0, silence.size, AudioTrack.WRITE_BLOCKING) <= 0) {
                                break
                            }
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Silent voice-communication keep-alive stopped writing")
                    }
                },
                "helm-silent-communication",
            ).apply {
                isDaemon = true
                start()
            }
        } catch (e: Exception) {
            Timber.w(e, "Could not start the silent voice-communication keep-alive")
            stopSilentCommunicationTrack()
            return
        }
        Timber.i("Started silent voice-communication keep-alive for one-rider route parity")
    }

    /**
     * Releases the silent voice-communication track and its writer. Teardown always releases this
     * app-owned stream, even when a telephony call owns the platform's route and audio mode.
     */
    private fun stopSilentCommunicationTrack() {
        silentCommunicationWriter?.interrupt()
        silentCommunicationWriter = null
        val track = silentCommunicationTrack ?: return
        silentCommunicationTrack = null
        try {
            if (track.state != AudioTrack.STATE_UNINITIALIZED) {
                track.stop()
            }
        } catch (e: Exception) {
            Timber.w(e, "Could not stop the silent voice-communication keep-alive")
        }
        try {
            track.release()
        } catch (e: Exception) {
            Timber.w(e, "Could not release the silent voice-communication keep-alive")
        }
    }

    /**
     * Stops audio routing, releases Bluetooth SCO and resets audio mode unless an active or ringing
     * GSM call owns them. No audio focus was taken by this router, so there is nothing to abandon.
     */
    fun stopAudioRouting() {
        if (!isRoutingActive) return

        Timber.i("Stopping audio routing and resetting audio mode")
        // Ignore broadcasts generated by the cleanup itself (notably the SCO disconnect) before
        // any AudioManager state is changed.
        isRoutingActive = false
        cancelRouteRetry()
        isScoStopIntentional = false
        // This stream belongs to the app, not to telephony, so always release it.
        stopSilentCommunicationTrack()
        val restoreNormalRoute = shouldRestoreNormalAudioMode(audioManager.mode)
        if (restoreNormalRoute) {
            stopBluetoothSco()
            clearPreferredDevice()
            setSpeakerphoneFlag(on = false)
            audioManager.mode = AudioManager.MODE_NORMAL
        } else {
            // The modem owns the mode and the route. Keep both the platform route and the pending
            // SCO flag untouched so the next call can still release a channel started by this app.
            Timber.i("Leaving telephony-owned audio mode and route untouched during teardown")
        }

        unregisterReceivers()
        unregisterAudioDeviceCallback()

        invalidateProfileLookup()
        cachedBluetoothHeadsetConnected = false
        ignoreStaleBluetoothSignals = false
        currentHeadsetKind = HeadsetKind.NONE
        lastConnectedHeadsetName = null
        routePreference.setForced(enable = false)
        _routingState.value = AudioRoutingState.EarpieceConnected
        _isHeadsetConnected.value = false
    }

    /**
     * Clears manual speaker routing before a new call starts. The output itself is chosen by
     * [startAudioRouting]. Headset availability is published only while routing is active; after
     * routing has stopped the router has unregistered its listeners, so re-detection is deferred
     * to [startAudioRouting] instead of publishing a stale value here.
     */
    fun resetForNewCall() {
        cancelRouteRetry()
        stopSilentCommunicationTrack()
        routePreference.setForced(enable = false)
        isScoStopIntentional = false
        ignoreStaleBluetoothSignals = false
        if (shouldRestoreNormalAudioMode(audioManager.mode)) {
            bluetoothScoStartRequested = false
            setSpeakerphoneFlag(on = false)
            clearPreferredDevice()
        } else {
            // Match stopAudioRouting(): a ringing or active GSM call must keep its own speaker and
            // communication device. Only the app's internal route preference is reset here.
            Timber.i("Leaving telephony-owned audio route untouched before the new call")
        }
        _routingState.value = AudioRoutingState.EarpieceConnected
        if (isRoutingActive) {
            currentHeadsetKind = detectHeadsetKind()
            _isHeadsetConnected.value = currentHeadsetKind != HeadsetKind.NONE
        } else {
            invalidateProfileLookup()
            cachedBluetoothHeadsetConnected = false
            currentHeadsetKind = HeadsetKind.NONE
            lastConnectedHeadsetName = null
            _isHeadsetConnected.value = false
        }
    }

    /**
     * Toggles forced speakerphone output. The remembered headset family is used instead of a fresh
     * device query so a tap immediately after a plug broadcast cannot be lost to slow enumeration.
     * Switching is ignored when no headset is connected, because the speaker is the only output.
     *
     * @param enable `true` to force the built-in speaker, `false` to fall back to the headset.
     */
    fun setSpeakerphone(enable: Boolean) {
        routingGeneration += 1
        cancelRouteRetry()
        val headsetKind = headsetKindForSpeakerToggle(enableSpeaker = enable)
        if (headsetKind == HeadsetKind.NONE) {
            routePreference.setForced(enable = false)
            routeToBuiltInSpeakerOutput()
            return
        }
        routePreference.setForced(enable = enable)
        applySpeakerphoneRouting(enable, headsetKind)
    }

    /**
     * Resolves the route to act on for a rider toggle. Switching back to the helmet re-checks a
     * remembered [HeadsetKind.NONE], so a stale disconnect state cannot leave the rider stuck on the
     * speaker with the route button disabled.
     *
     * @param enableSpeaker whether the rider is switching to the built-in speaker.
     * @return the headset family the toggle must apply.
     */
    private fun headsetKindForSpeakerToggle(enableSpeaker: Boolean): HeadsetKind {
        if (enableSpeaker || (currentHeadsetKind != HeadsetKind.NONE)) return currentHeadsetKind
        val detected = detectHeadsetKind()
        publishDetectedHeadset(detected)
        return detected
    }

    /**
     * Routes to the built-in speaker or back to the connected headset after a rider toggle.
     *
     * @param enable `true` to force the built-in speaker, `false` to restore the headset route.
     * @param kind the headset family that must be restored when switching back to the helmet.
     */
    private fun applySpeakerphoneRouting(enable: Boolean, kind: HeadsetKind) {
        if (enable) {
            cancelRouteRetry()
            stopBluetoothSco()
            bluetoothScoStartRequested = false
            routeToBuiltInSpeakerOutput()
        } else {
            routeToHeadset(kind)
        }
    }

    /**
     * Routes communication audio to the built-in speaker and publishes the new routing state.
     * The legacy speakerphone flag is kept as a fallback because some devices reject an explicit
     * built-in-speaker device while a USB headset is still enumerated.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun routeToBuiltInSpeaker() {
        val speaker = audioManager.availableCommunicationDevices
            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        val selected = try {
            (speaker != null) && audioManager.setCommunicationDevice(speaker)
        } catch (e: Exception) {
            Timber.w(e, "Could not select the built-in speaker as communication device")
            false
        }
        if (!selected) {
            Timber.w("Built-in speaker device was rejected; using the speakerphone flag")
            clearPreferredDevice()
        }
        setSpeakerphoneFlag(on = true)
        _routingState.value = AudioRoutingState.SpeakerConnected
    }

    /**
     * Re-evaluates headset availability and the active route. A headset is used unless the rider
     * explicitly chose the speaker; only a real disconnect clears that choice, so a transient
     * re-detection with no enumerated headset cannot bounce audio back to the headset. Bluetooth
     * signals stay excluded while [ignoreStaleBluetoothSignals] is latched by a confirmed HFP
     * disconnect.
     *
     * @param forcedHeadsetKind the family from a broadcast, or `null` to re-detect.
     * @param headsetDisconnected whether a plug, profile or device-removal event reported that a
     *   headset was disconnected; rider state is cleared only when no headset remains.
     * @param resetRouteRetryAttempt whether to restart the retry sequence; device-callback updates
     *   preserve it so repeated enumeration batches cannot starve the Bluetooth timeout.
     */
    private fun updateAudioRouting(
        forcedHeadsetKind: HeadsetKind? = null,
        headsetDisconnected: Boolean = false,
        resetRouteRetryAttempt: Boolean = true,
    ) {
        routingGeneration += 1
        cancelRouteRetry(resetAttempt = resetRouteRetryAttempt)
        // Events generated by the router's own SCO stop are not headset activity: they must not
        // release a timeout latch, clear stale-signal suppression or reset the rider's choice.
        if (!isScoStopIntentional &&
            shouldClearBluetoothSuppressionOnReconnect(
                forcedKind = forcedHeadsetKind,
                hasScoDevice = hasBluetoothScoDevice(currentOutputDevices()),
            )
        ) {
            Timber.i("Bluetooth SCO device re-enumerated; clearing stale-signal suppression")
            ignoreStaleBluetoothSignals = false
        }
        val kind = resolveBroadcastHeadsetKind(
            detectedKind = detectHeadsetKind(),
            forcedKind = forcedHeadsetKind,
        )
        // A timeout latch is not a rider choice: a real headset connection restores automatic
        // routing. A rider who deliberately chose the speaker keeps that choice.
        if (!isScoStopIntentional &&
            routePreference.clearTimeoutLatchOnHeadsetConnected(forcedHeadsetKind)
        ) {
            Timber.i("Headset reconnected after speaker fallback; restoring automatic route")
        }
        // Only a confirmed full disconnect clears rider state. A plug or profile event from one
        // of several connected headsets must not tear down the active SCO route or the rider's
        // speaker choice while another headset is still available.
        if (!isScoStopIntentional && routePreference.clearOnDisconnect(headsetDisconnected, kind)) {
            stopBluetoothSco()
        }
        currentHeadsetKind = kind
        _isHeadsetConnected.value = kind != HeadsetKind.NONE

        when (resolveRouteTarget(kind, routePreference.isSpeakerForced)) {
            RouteTarget.SPEAKER -> routeToBuiltInSpeakerOutput()
            RouteTarget.HEADSET -> routeToHeadset(kind)
        }
    }

    /**
     * Detects the connected headset family from cheap audio state and the cached HFP availability.
     * Bluetooth wins when both families are present because helmet intercoms need the HFP/SCO path;
     * an open SCO channel counts as a headset even when the profile cache has not caught up yet.
     * A confirmed HFP disconnect latches [ignoreStaleBluetoothSignals], so lingering SCO and profile
     * signals cannot keep the intercom route alive after the headset is gone.
     *
     * @return the current [HeadsetKind], or [HeadsetKind.NONE] when no headset is connected.
     */
    @Suppress("DEPRECATION")
    private fun detectHeadsetKind(): HeadsetKind {
        val connectedDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .map(AudioDeviceInfo::toCandidate)
        return resolveHeadsetKind(
            hasScoAudioDevice = connectedDevices.any { (deviceType, _) ->
                deviceType == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            },
            hasHeadsetAudioDevice = hasNonBluetoothHeadsetDevice(connectedDevices),
            isBluetoothScoOn = audioManager.isBluetoothScoOn,
            hasBluetoothHeadsetProfile = cachedBluetoothHeadsetConnected,
            isWiredHeadsetOn = (
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
                    audioManager.isWiredHeadsetOn
                ),
            includeBluetooth = !ignoreStaleBluetoothSignals,
        )
    }

    /**
     * Refreshes HFP availability on a background thread so route decisions never block on a
     * Bluetooth binder call. The result is applied on the main thread; a pending disconnect is
     * forwarded to `updateAudioRouting()` only when the fresh lookup confirms it.
     *
     * @param headsetDisconnected whether a profile or SCO event reported a disconnect.
     */
    @SuppressLint("MissingPermission")
    private fun refreshBluetoothHeadsetCache(headsetDisconnected: Boolean = false) {
        val manager = bluetoothManager
        if ((manager == null || !hasBluetoothPermission())) {
            applyBluetoothProfileCache(connected = false)
            return
        }
        if (profileRefreshPending) invalidateProfileLookup()
        if (headsetDisconnected) pendingBluetoothDisconnect = true
        val token = ++profileRefreshGeneration
        profileRefreshPending = true
        profileQueryExecutor.execute {
            val connected = queryConnectedBluetoothHeadset(
                connectedDevices = manager::getConnectedDevices,
            )
            mainHandler.post {
                if (token != profileRefreshGeneration) {
                    Timber.d("Ignoring a Bluetooth profile lookup superseded by a newer event")
                    return@post
                }
                profileRefreshPending = false
                val disconnectEvent = pendingBluetoothDisconnect
                pendingBluetoothDisconnect = false
                applyBluetoothProfileLookup(
                    connected = connected,
                    headsetDisconnected = disconnectEvent,
                )
            }
        }
    }

    /**
     * Applies a profile lookup only when the HFP query actually succeeded. Devices that reject
     * `getConnectedDevices(BluetoothProfile.HEADSET)` report `null`; treating that as a negative
     * lookup would latch stale-signal suppression on every SCO disconnect and hide a helmet that is
     * still enumerated as a Bluetooth SCO output. Real removals are still detected from the device
     * callback, noisy-audio and Bluetooth connection broadcasts.
     *
     * @param connected whether HFP reported a headset, or `null` when the lookup was unavailable.
     * @param headsetDisconnected whether a disconnect broadcast is waiting for this result.
     */
    private fun applyBluetoothProfileLookup(connected: Boolean?, headsetDisconnected: Boolean) {
        if (connected == null) {
            if (headsetDisconnected) {
                Timber.i("HFP lookup unavailable; keeping the enumerated headset route")
            }
            return
        }
        applyBluetoothProfileCache(connected = connected, headsetDisconnected = headsetDisconnected)
    }

    /**
     * Applies a completed profile lookup on the main thread and re-evaluates the route when the
     * result changes or a disconnect event still needs to be confirmed. A confirmed disconnect
     * latches stale-signal suppression until a fresh lookup or broadcast proves that a headset is
     * connected again, so the SCO shutdown broadcast cannot resurrect a disconnected helmet.
     *
     * @param connected whether an HFP headset is connected according to the fresh lookup.
     * @param headsetDisconnected whether a disconnect broadcast is waiting for this result.
     */
    private fun applyBluetoothProfileCache(
        connected: Boolean,
        headsetDisconnected: Boolean = false,
    ) {
        val wasConnected = cachedBluetoothHeadsetConnected
        cachedBluetoothHeadsetConnected = connected
        val disconnectConfirmedByLookup = shouldIgnoreStaleBluetoothSignals(
            disconnectReported = headsetDisconnected,
            profileStillConnected = connected,
        )
        ignoreStaleBluetoothSignals = advanceStaleBluetoothSignalSuppression(
            profileStillConnected = connected,
            disconnectConfirmedByLookup = disconnectConfirmedByLookup,
            previouslyIgnoring = ignoreStaleBluetoothSignals,
        )
        val confirmedDisconnect = headsetDisconnected || (wasConnected && !connected)
        if (isRoutingActive && (wasConnected != connected || confirmedDisconnect)) {
            updateAudioRouting(headsetDisconnected = confirmedDisconnect)
        }
    }

    /** Invalidates an in-flight profile lookup and any unconfirmed disconnect event. */
    private fun invalidateProfileLookup() {
        profileRefreshGeneration += 1
        profileRefreshPending = false
        pendingBluetoothDisconnect = false
    }

    /**
     * Routes call audio to the built-in speaker on every API level and publishes that state.
     */
    private fun routeToBuiltInSpeakerOutput() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            routeToBuiltInSpeaker()
        } else {
            setSpeakerphoneFlag(on = true)
            _routingState.value = AudioRoutingState.SpeakerConnected
        }
    }

    /**
     * Routes communication audio to the connected headset family using the best API available.
     *
     * @param kind the connected headset family.
     */
    private fun routeToHeadset(kind: HeadsetKind) {
        if (kind == HeadsetKind.NONE) {
            routeToBuiltInSpeakerOutput()
            return
        }
        // A wired/USB handover must close the old HFP channel so returning to Bluetooth later
        // starts a fresh SCO connection instead of reusing a session the platform tore down.
        if (shouldReleaseScoForHeadsetKind(kind)) {
            stopBluetoothSco()
        }
        // Clear any speakerphone override before selecting the headset so the legacy flag cannot
        // keep fighting an explicitly selected communication device.
        setSpeakerphoneFlag(on = false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            routeToHeadsetApi31Plus(kind)
        } else {
            routeToHeadsetLegacy(kind)
        }
    }

    /**
     * Selects the matching communication device on Android 12+. When the platform has not published
     * the device yet, Bluetooth SCO is requested and a short retry loop waits for enumeration.
     * Wired and USB routes rely on the platform device callback instead: a Bluetooth SCO timeout
     * must never send the rider to the speaker while their headset is still connected.
     *
     * @param kind the connected headset family.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun routeToHeadsetApi31Plus(kind: HeadsetKind) {
        val devices = audioManager.availableCommunicationDevices.asSequence()
        val selected = selectHeadsetDevice(
            devices = devices.map(AudioDeviceInfo::toCandidate).asIterable(),
            kind = kind,
        )
        if (selected != null) {
            val device = devices.firstOrNull { it.type == selected.type }
            if (device != null && audioManager.setCommunicationDevice(device)) {
                Timber.i("Set preferred communication device: ${selected.name}")
                cancelRouteRetry()
                bluetoothScoStartRequested = false
                publishHeadsetRoute(selected.name)
                return
            }
        }

        if (kind == HeadsetKind.BLUETOOTH) {
            if (!bluetoothScoStartRequested) startBluetoothSco()
            _routingState.value = AudioRoutingState.ConnectingBluetooth
            scheduleRouteRetry(kind)
        } else {
            // Let the platform choose the wired/USB headset until the device callback publishes it.
            // There is no SCO handshake to time out, so no speaker fallback is scheduled here.
            clearPreferredDevice()
            publishHeadsetRoute(selected?.name)
        }
    }

    // Legacy SCO routing is only reachable below API 31.
    /**
     * Routes communication audio to a wired headset by disabling the speakerphone flag, or starts
     * Bluetooth SCO for a helmet intercom. Wired headsets never enter the SCO connecting state.
     *
     * @param kind the connected headset family.
     */
    @Suppress("DEPRECATION")
    private fun routeToHeadsetLegacy(kind: HeadsetKind) {
        if (kind == HeadsetKind.BLUETOOTH) {
            if (!bluetoothScoStartRequested) startBluetoothSco()
            _routingState.value = if (bluetoothScoStartRequested) {
                AudioRoutingState.ConnectingBluetooth
            } else {
                AudioRoutingState.EarpieceConnected
            }
            scheduleRouteRetry(kind)
            return
        }

        cancelRouteRetry()
        val connectedDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .map(AudioDeviceInfo::toCandidate)
        publishHeadsetRoute(selectHeadsetDevice(connectedDevices, kind)?.name)
    }

    // Legacy SCO APIs are deprecated from API 31, but still used below it.
    /**
     * Requests an SCO connection once per headset session. The sticky SCO broadcast confirms the
     * result; the retry loop handles a profile that appears before SCO becomes available.
     */
    @Suppress("DEPRECATION")
    private fun startBluetoothSco() {
        bluetoothScoStartRequested = true
        try {
            Timber.i("Starting Bluetooth SCO for helmet headset audio")
            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = true
        } catch (e: Exception) {
            Timber.w(e, "Could not start Bluetooth SCO")
        }
    }

    /**
     * Retries communication-device selection while the platform catches up with a new Bluetooth
     * headset. Wired and USB routes are never retried or timed out into the speaker.
     *
     * @param kind the headset family that triggered the retry.
     */
    private fun scheduleRouteRetry(kind: HeadsetKind) {
        if (!shouldScheduleBluetoothRouteRetry(
                kind = kind,
                isRoutingActive = isRoutingActive,
                isSpeakerphoneForced = routePreference.isSpeakerForced,
            )
        ) {
            return
        }
        val delay = ROUTE_RETRY_DELAYS_MS.getOrNull(routeRetryAttempt)
        if (delay == null) {
            Timber.w("Headset route selection timed out for $kind, falling back to speaker")
            cancelRouteRetry()
            // Latch the rider's speaker choice before stopping SCO. The resulting SCO DISCONNECTED
            // broadcast would otherwise re-detect the still-paired headset and restart the retry
            // loop forever instead of staying on the built-in speaker.
            routePreference.latchSpeakerAfterTimeout()
            stopBluetoothSco()
            routeToBuiltInSpeakerOutput()
            return
        }
        cancelRouteRetry(resetAttempt = false)
        val attempt = routeRetryAttempt + 1
        val generation = routingGeneration
        val runnable = Runnable {
            routeRetryRunnable = null
            if ((
                !isRoutingActive || generation != routingGeneration ||
                    currentHeadsetKind != kind || routePreference.isSpeakerForced
                )) {
                return@Runnable
            }
            routeRetryAttempt = attempt
            routeToHeadset(kind)
        }
        routeRetryAttempt = attempt
        routeRetryRunnable = runnable
        mainHandler.postDelayed(runnable, delay)
    }

    /**
     * Removes a pending route-selection retry.
     *
     * @param resetAttempt whether to also restart the retry sequence; `false` is used while
     *   scheduling the next retry so the existing attempt index is preserved.
     */
    private fun cancelRouteRetry(resetAttempt: Boolean = true) {
        routeRetryRunnable?.let(mainHandler::removeCallbacks)
        routeRetryRunnable = null
        if (resetAttempt) {
            routeRetryAttempt = 0
        }
    }

    // Legacy SCO routing is only reachable below API 31.
    /**
     * Stops the legacy Bluetooth SCO connection, ignoring failures reported by the audio stack.
     */
    @Suppress("DEPRECATION")
    private fun stopBluetoothSco() {
        try {
            if (audioManager.isBluetoothScoOn || bluetoothScoStartRequested) {
                // Tag the resulting broadcast before touching the platform state. The receiver runs
                // on the main thread, same as this method, so it cannot observe a half-set flag.
                isScoStopIntentional = true
                audioManager.isBluetoothScoOn = false
                audioManager.stopBluetoothSco()
            }
        } catch (e: Exception) {
            isScoStopIntentional = false
            Timber.e(e, "Error stopping Bluetooth SCO")
        } finally {
            bluetoothScoStartRequested = false
        }
    }

    /**
     * Clears the explicitly selected communication device on API 31+ so the system can pick the
     * default output again. Devices that reject the call are logged and ignored so call teardown
     * can still finish.
     */
    private fun clearPreferredDevice() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        try {
            audioManager.clearCommunicationDevice()
        } catch (e: Exception) {
            Timber.w(e, "Could not clear the selected communication device")
        }
    }

    // The speakerphone flag is still useful on API 31+ as a fallback when setCommunicationDevice()
    // rejects the built-in speaker device, for example while a USB headset is enumerated.
    /**
     * Sets the platform speakerphone flag on every API level. An explicit communication-device
     * selection wins while one is active, but the flag keeps the speaker reachable when that
     * selection fails. Unsupported or rejected writes are logged and ignored instead of crashing
     * a call on custom audio stacks.
     *
     * @param on `true` to enable the speakerphone flag, `false` to disable it.
     */
    @Suppress("DEPRECATION")
    private fun setSpeakerphoneFlag(on: Boolean) {
        try {
            audioManager.isSpeakerphoneOn = on
        } catch (e: Exception) {
            Timber.w(e, "Could not set the speakerphone flag to $on")
        }
    }

    // The SCO action is deprecated but is the only option below API 31.
    /**
     * Registers the receiver that tracks SCO state changes and headset disconnects. Does nothing
     * when the receiver is already registered.
     */
    @Suppress("DEPRECATION")
    private fun registerReceivers() {
        if (isReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(
            context,
            bluetoothReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        isReceiverRegistered = true
    }

    /**
     * Registers the audio-device callback so USB headsets and late Bluetooth SCO enumeration are
     * detected even when no sticky plug broadcast is emitted.
     */
    private fun registerAudioDeviceCallback() {
        if (isAudioDeviceCallbackRegistered) return
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, mainHandler)
        isAudioDeviceCallbackRegistered = true
    }

    /**
     * Unregisters the audio-device callback, ignoring failures when it was never registered.
     */
    private fun unregisterAudioDeviceCallback() {
        if (!isAudioDeviceCallbackRegistered) return
        try {
            audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        } catch (e: Exception) {
            Timber.w(e, "Error unregistering audio device callback")
        }
        isAudioDeviceCallbackRegistered = false
    }

    /**
     * Unregisters the SCO/headset receiver, ignoring failures when it was never registered.
     */
    private fun unregisterReceivers() {
        if (!isReceiverRegistered) return
        try {
            context.unregisterReceiver(bluetoothReceiver)
        } catch (e: Exception) {
            Timber.e(e, "Error unregistering receiver")
        }
        isReceiverRegistered = false
    }

    /**
     * Reports whether Bluetooth routing may be used on this API level.
     *
     * @return `true` on releases below Android 12 (no runtime permission) or when
     *   [Manifest.permission.BLUETOOTH_CONNECT] has been granted.
     */
    private fun hasBluetoothPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) == PackageManager.PERMISSION_GRANTED
    }
}
