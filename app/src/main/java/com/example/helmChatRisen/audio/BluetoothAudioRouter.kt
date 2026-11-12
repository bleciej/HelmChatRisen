package com.example.helmChatRisen.audio

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
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * State of audio routing to helmet headsets or device speakers.
 */
sealed class AudioRoutingState {
    object SpeakerConnected : AudioRoutingState()
    object EarpieceConnected : AudioRoutingState()
    object ConnectingBluetooth : AudioRoutingState()
    data class BluetoothConnected(val deviceName: String? = null) : AudioRoutingState()
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
        private const val TAG = "BluetoothAudioRouter"

        @Volatile
        private var instance: BluetoothAudioRouter? = null

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
                Log.d(TAG, "Audio focus GAINED")
                if (isRoutingActive) {
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                }
            }
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Keep the communication mode and microphone alive. Navigation prompts and
                // other short announcements may take focus, but must not stop the intercom.
                Log.w(TAG, "Audio focus LOST or LOST_TRANSIENT, keeping call audio alive")
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d(TAG, "Audio focus LOST_TRANSIENT_CAN_DUCK")
            }
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context?, intent: Intent?) {
            when (intent?.action) {
                AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED -> {
                    val state = intent.getIntExtra(
                        AudioManager.EXTRA_SCO_AUDIO_STATE,
                        AudioManager.SCO_AUDIO_STATE_DISCONNECTED
                    )
                    Log.d(TAG, "SCO audio state updated: $state")
                    when (state) {
                        AudioManager.SCO_AUDIO_STATE_CONNECTED -> {
                            Log.i(TAG, "Bluetooth SCO connected to helmet intercom")
                            _routingState.value = AudioRoutingState.BluetoothConnected("Helmet Headset")
                        }
                        AudioManager.SCO_AUDIO_STATE_CONNECTING -> {
                            Log.d(TAG, "Bluetooth SCO connecting...")
                            _routingState.value = AudioRoutingState.ConnectingBluetooth
                        }
                        AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> {
                            Log.d(TAG, "Bluetooth SCO disconnected")
                            if (isSpeakerphoneForced) {
                                _routingState.value = AudioRoutingState.SpeakerConnected
                            } else {
                                _routingState.value = AudioRoutingState.EarpieceConnected
                            }
                        }
                        AudioManager.SCO_AUDIO_STATE_ERROR -> {
                            Log.e(TAG, "Bluetooth SCO state error")
                            _routingState.value = AudioRoutingState.Error("Bluetooth SCO connection failed")
                        }
                    }
                }
                AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                    Log.w(TAG, "Audio becoming noisy (headset disconnected)")
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
            Log.d(TAG, "Audio routing is already active")
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

        Log.i(TAG, "Stopping audio routing and resetting audio mode")
        stopBluetoothSco()
        clearPreferredDevice()

        audioManager.isSpeakerphoneOn = false
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
        audioManager.isSpeakerphoneOn = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        }
        _routingState.value = AudioRoutingState.EarpieceConnected
    }

    /**
     * Toggles forced speakerphone output. On Android 12+ the explicit communication device is
     * used; on older releases the legacy speakerphone flag is the best available option.
     */
    fun setSpeakerphone(enable: Boolean) {
        isSpeakerphoneForced = enable

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (enable) {
                val speaker = audioManager.availableCommunicationDevices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                }
                if (speaker != null) {
                    audioManager.setCommunicationDevice(speaker)
                }
                audioManager.isSpeakerphoneOn = true
                _routingState.value = AudioRoutingState.SpeakerConnected
            } else {
                audioManager.isSpeakerphoneOn = false
                val bluetooth = if (hasBluetoothPermission()) {
                    audioManager.availableCommunicationDevices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                    }
                } else {
                    null
                }
                if (bluetooth != null) {
                    audioManager.setCommunicationDevice(bluetooth)
                    _routingState.value = AudioRoutingState.BluetoothConnected(bluetooth.productName?.toString())
                } else {
                    audioManager.clearCommunicationDevice()
                    _routingState.value = AudioRoutingState.EarpieceConnected
                }
            }
            return
        }

        if (enable) {
            stopBluetoothSco()
            audioManager.isSpeakerphoneOn = true
            _routingState.value = AudioRoutingState.SpeakerConnected
        } else {
            audioManager.isSpeakerphoneOn = false
            if (audioManager.isBluetoothScoAvailableOffCall) {
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
                _routingState.value = AudioRoutingState.ConnectingBluetooth
            } else {
                _routingState.value = AudioRoutingState.EarpieceConnected
            }
        }
    }

    fun isSpeakerphoneForced(): Boolean = isSpeakerphoneForced

    fun toggleSpeakerphone(): Boolean {
        val newSpeaker = !isSpeakerphoneForced
        setSpeakerphone(newSpeaker)
        return newSpeaker
    }

    /**
     * Check whether Bluetooth SCO audio is connected.
     */
    fun isBluetoothScoConnected(): Boolean {
        return audioManager.isBluetoothScoOn
    }

    private fun updateAudioRouting() {
        if (isSpeakerphoneForced) {
            audioManager.isSpeakerphoneOn = true
            _routingState.value = AudioRoutingState.SpeakerConnected
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            routeAudioApi31Plus()
        } else {
            routeAudioLegacy()
        }
    }

    private fun routeAudioApi31Plus() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

        if (hasBluetoothPermission()) {
            val availableDevices = audioManager.availableCommunicationDevices
            val bluetoothDevice = availableDevices.firstOrNull { device ->
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
            }

            if (bluetoothDevice != null) {
                val success = audioManager.setCommunicationDevice(bluetoothDevice)
                if (success) {
                    Log.i(TAG, "Set preferred communication device: ${bluetoothDevice.productName}")
                    _routingState.value = AudioRoutingState.BluetoothConnected(bluetoothDevice.productName.toString())
                    return
                }
            }
        }

        // Fallback to SCO or speaker/earpiece
        routeAudioLegacy()
    }

    private fun routeAudioLegacy() {
        if (audioManager.isBluetoothScoAvailableOffCall) {
            Log.i(TAG, "Starting Bluetooth SCO for helmet headset audio")
            _routingState.value = AudioRoutingState.ConnectingBluetooth
            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = true
        } else {
            Log.d(TAG, "Bluetooth SCO not available off call, using default audio output")
            _routingState.value = AudioRoutingState.EarpieceConnected
        }
    }

    private fun stopBluetoothSco() {
        try {
            if (audioManager.isBluetoothScoOn) {
                audioManager.isBluetoothScoOn = false
                audioManager.stopBluetoothSco()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Bluetooth SCO", e)
        }
    }

    private fun clearPreferredDevice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        }
    }

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
        Log.d(TAG, "Audio focus request result granted=$granted")
        return granted
    }

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

    private fun registerReceivers() {
        if (isReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        }
        context.registerReceiver(bluetoothReceiver, filter)
        isReceiverRegistered = true
    }

    private fun unregisterReceivers() {
        if (!isReceiverRegistered) return
        try {
            context.unregisterReceiver(bluetoothReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
        isReceiverRegistered = false
    }

    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
