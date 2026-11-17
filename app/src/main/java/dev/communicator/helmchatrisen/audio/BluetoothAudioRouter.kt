package dev.communicator.helmchatrisen.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * State of audio routing to helmet headsets or device speakers.
 */
sealed class AudioRoutingState {
    /** Audio is routed to the device speaker. */
    object SpeakerConnected : AudioRoutingState()

    /** Audio is routed to the phone earpiece, the fallback when no helmet headset is connected. */
    object EarpieceConnected : AudioRoutingState()

    /** Bluetooth SCO is being negotiated with the helmet headset. */
    object ConnectingBluetooth : AudioRoutingState()

    /**
     * Audio is routed to the connected Bluetooth headset or helmet intercom.
     *
     * @param deviceName the product name of the connected device, or `null` when it is unknown.
     */
    data class BluetoothConnected(val deviceName: String? = null) : AudioRoutingState()

    /**
     * Audio routing failed.
     *
     * @param message a human-readable description of the failure.
     */
    data class Error(val message: String) : AudioRoutingState()
}

/**
 * Audio and Bluetooth SCO routing helper for helmet intercom headsets (Sena, Cardo, Packtalk, etc.)
 *
 * Jitsi's own `AudioModeModule` audio focus handling is disabled (`audio-focus.disabled`), so
 * this router owns the in-call audio mode, the helmet route and audio focus. It requests
 * transient-may-duck focus so background music keeps playing quietly instead of pausing, and
 * navigation voice prompts do not tear the call down.
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

    private val _routingState = MutableStateFlow<AudioRoutingState>(AudioRoutingState.EarpieceConnected)
    val routingState: StateFlow<AudioRoutingState> = _routingState.asStateFlow()

    private var audioFocusRequest: AudioFocusRequest? = null
    private var isRoutingActive = false
    private var isSpeakerphoneForced = false
    private var isReceiverRegistered = false

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                Timber.d("Audio focus GAINED")
                if (isRoutingActive) {
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                }
            }
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            -> {
                // Keep the communication mode and microphone alive. Navigation prompts and
                // other short announcements may take focus, but must not stop the intercom.
                Timber.w("Audio focus LOST or LOST_TRANSIENT, keeping call audio alive")
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Timber.d("Audio focus LOST_TRANSIENT_CAN_DUCK")
            }
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        // The SCO broadcast API is deprecated but is the only option below API 31.
        /**
         * Keeps the routing state in sync with SCO state changes and headset disconnects.
         *
         * @param receiverContext the context the receiver was registered with.
         * @param intent the broadcast being delivered, or `null` when it carries no data.
         */
        @Suppress("DEPRECATION")
        override fun onReceive(receiverContext: Context?, intent: Intent?) {
            when (intent?.action) {
                AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED -> {
                    val state = intent.getIntExtra(
                        AudioManager.EXTRA_SCO_AUDIO_STATE,
                        AudioManager.SCO_AUDIO_STATE_DISCONNECTED
                    )
                    Timber.d("SCO audio state updated: $state")
                    when (state) {
                        AudioManager.SCO_AUDIO_STATE_CONNECTED -> {
                            Timber.i("Bluetooth SCO connected to helmet intercom")
                            _routingState.value = AudioRoutingState.BluetoothConnected("Helmet Headset")
                        }
                        AudioManager.SCO_AUDIO_STATE_CONNECTING -> {
                            Timber.d("Bluetooth SCO connecting...")
                            _routingState.value = AudioRoutingState.ConnectingBluetooth
                        }
                        AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> {
                            Timber.d("Bluetooth SCO disconnected")
                            _routingState.value = if (isSpeakerphoneForced) {
                                AudioRoutingState.SpeakerConnected
                            } else {
                                AudioRoutingState.EarpieceConnected
                            }
                        }
                        AudioManager.SCO_AUDIO_STATE_ERROR -> {
                            Timber.e("Bluetooth SCO state error")
                            _routingState.value = AudioRoutingState.Error("Bluetooth SCO connection failed")
                        }
                    }
                }
                AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                    Timber.w("Audio becoming noisy (headset disconnected)")
                    updateAudioRouting()
                }
            }
        }
    }

    /**
     * Starts audio routing in communication mode, requests audio focus, and routes audio to Bluetooth SCO if available.
     */
    fun startAudioRouting() {
        if (isRoutingActive) {
            Timber.d("Audio routing is already active")
            return
        }

        registerReceivers()
        requestAudioFocus()

        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        isRoutingActive = true

        updateAudioRouting()
    }

    /**
     * Stops audio routing, releases Bluetooth SCO, abandons audio focus, and resets audio mode.
     */
    fun stopAudioRouting() {
        if (!isRoutingActive) return

        Timber.i("Stopping audio routing and resetting audio mode")
        stopBluetoothSco()
        clearPreferredDevice()
        setSpeakerphoneFlag(on = false)

        audioManager.mode = AudioManager.MODE_NORMAL

        abandonAudioFocus()
        unregisterReceivers()

        isRoutingActive = false
        _routingState.value = AudioRoutingState.EarpieceConnected
    }

    /**
     * Resets any manual speaker routing before a new call starts. Jitsi will then pick the
     * appropriate communication device (Bluetooth helmet, earpiece or speaker) itself.
     */
    fun resetForNewCall() {
        isSpeakerphoneForced = false
        setSpeakerphoneFlag(on = false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        }
        _routingState.value = AudioRoutingState.EarpieceConnected
    }

    /**
     * Toggles forced speakerphone output. On Android 12+ the explicit communication device is
     * used; on older releases the legacy speakerphone flag is the best available option.
     *
     * @param enable `true` to force the built-in speaker, `false` to fall back to Bluetooth or
     *   the earpiece.
     */
    fun setSpeakerphone(enable: Boolean) {
        isSpeakerphoneForced = enable
        applySpeakerphoneRouting(enable)
    }

    /**
     * Applies the forced-speakerphone choice through the API-level-specific routing path.
     *
     * @param enable `true` to force the built-in speaker, `false` to restore the default route.
     */
    private fun applySpeakerphoneRouting(enable: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            applySpeakerphoneApi31Plus(enable)
        } else {
            applySpeakerphoneLegacy(enable)
        }
    }

    /**
     * Applies the forced-speakerphone choice using the API 31+ communication-device API.
     *
     * @param enable `true` to force the built-in speaker, `false` to restore the default route.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun applySpeakerphoneApi31Plus(enable: Boolean) {
        if (enable) {
            routeToBuiltInSpeaker()
        } else {
            routeToBluetoothOrEarpiece()
        }
    }

    /**
     * Routes communication audio to the built-in speaker and publishes the new routing state.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun routeToBuiltInSpeaker() {
        audioManager.availableCommunicationDevices
            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            ?.let(audioManager::setCommunicationDevice)
        _routingState.value = AudioRoutingState.SpeakerConnected
    }

    /**
     * Routes communication audio to the Bluetooth helmet headset when one is available and
     * permitted, otherwise clears the explicit device so the earpiece is used.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun routeToBluetoothOrEarpiece() {
        val bluetooth = if (hasBluetoothPermission()) {
            audioManager.availableCommunicationDevices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            }
        } else {
            null
        }
        if (bluetooth != null && audioManager.setCommunicationDevice(bluetooth)) {
            _routingState.value = AudioRoutingState.BluetoothConnected(bluetooth.productName?.toString())
        } else {
            audioManager.clearCommunicationDevice()
            _routingState.value = AudioRoutingState.EarpieceConnected
        }
    }

    // Legacy SCO routing is only reachable below API 31.
    /**
     * Applies the forced-speakerphone choice through the legacy SCO and speakerphone flags.
     *
     * @param enable `true` to force the built-in speaker, `false` to start SCO when available.
     */
    @Suppress("DEPRECATION")
    private fun applySpeakerphoneLegacy(enable: Boolean) {
        if (enable) {
            stopBluetoothSco()
            setSpeakerphoneFlag(on = true)
            _routingState.value = AudioRoutingState.SpeakerConnected
        } else {
            setSpeakerphoneFlag(on = false)
            if (audioManager.isBluetoothScoAvailableOffCall) {
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
                _routingState.value = AudioRoutingState.ConnectingBluetooth
            } else {
                _routingState.value = AudioRoutingState.EarpieceConnected
            }
        }
    }

    /**
     * Re-evaluates the active route: a forced speaker wins, otherwise the API-level-specific path
     * picks Bluetooth or the fallback output.
     */
    private fun updateAudioRouting() {
        if (isSpeakerphoneForced) {
            applySpeakerphoneRouting(true)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            routeAudioApi31Plus()
        } else {
            routeAudioLegacy()
        }
    }

    /**
     * Routes communication audio with the API 31+ communication-device API, preferring a
     * connected Bluetooth SCO/A2DP device and falling back to the legacy SCO path.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun routeAudioApi31Plus() {
        if (hasBluetoothPermission()) {
            val availableDevices: List<AudioDeviceInfo> = audioManager.availableCommunicationDevices
            val bluetoothDevice = availableDevices.firstOrNull { device ->
                (device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
            }

            if (bluetoothDevice != null && audioManager.setCommunicationDevice(bluetoothDevice)) {
                Timber.i("Set preferred communication device: ${bluetoothDevice.productName}")
                _routingState.value = AudioRoutingState.BluetoothConnected(bluetoothDevice.productName.toString())
                return
            }
        }

        // Fallback to SCO or speaker/earpiece
        routeAudioLegacy()
    }

    // Legacy SCO routing is only reachable below API 31.
    /**
     * Starts SCO routing through the legacy API and publishes the resulting routing state.
     */
    @Suppress("DEPRECATION")
    private fun routeAudioLegacy() {
        if (audioManager.isBluetoothScoAvailableOffCall) {
            Timber.i("Starting Bluetooth SCO for helmet headset audio")
            _routingState.value = AudioRoutingState.ConnectingBluetooth
            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = true
        } else {
            Timber.d("Bluetooth SCO not available off call, using default audio output")
            _routingState.value = AudioRoutingState.EarpieceConnected
        }
    }

    // Legacy SCO routing is only reachable below API 31.
    /**
     * Stops the legacy Bluetooth SCO connection, ignoring failures reported by the audio stack.
     */
    @Suppress("DEPRECATION")
    private fun stopBluetoothSco() {
        try {
            if (audioManager.isBluetoothScoOn) {
                audioManager.isBluetoothScoOn = false
                audioManager.stopBluetoothSco()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error stopping Bluetooth SCO")
        }
    }

    /**
     * Clears the explicitly selected communication device on API 31+ so the system can pick the
     * default output again.
     */
    private fun clearPreferredDevice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        }
    }

    // The speakerphone flag is a legacy alias for setCommunicationDevice on API 31+.
    /**
     * Sets the legacy speakerphone flag, which is a no-op on API 31+ where
     * [AudioManager.setCommunicationDevice] owns routing instead.
     *
     * @param on `true` to enable the speakerphone flag, `false` to disable it.
     */
    @Suppress("DEPRECATION")
    private fun setSpeakerphoneFlag(on: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            audioManager.isSpeakerphoneOn = on
        }
    }

    /**
     * Requests transient-may-duck voice-communication audio focus so background music keeps
     * playing quietly instead of pausing.
     *
     * @return `true` when the focus request was granted.
     */
    private fun requestAudioFocus(): Boolean {
        val focusResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()

            audioFocusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }

        val granted = focusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        Timber.d("Audio focus request result granted=$granted")
        return granted
    }

    /**
     * Releases the audio focus previously taken by [requestAudioFocus].
     */
    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { request ->
                audioManager.abandonAudioFocusRequest(request)
                audioFocusRequest = null
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
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
        }
        ContextCompat.registerReceiver(
            context,
            bluetoothReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        isReceiverRegistered = true
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
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
    }
}
