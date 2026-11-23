package dev.communicator.helmchatrisen.audio

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import timber.log.Timber

/**
 * Minimal platform surface used by [AudioFocusController].
 *
 * Kept separate from Android's `AudioManager` so the call lifecycle can be tested on the JVM.
 */
internal interface AudioFocusOperations {

    /**
     * Requests a duckable transient focus for voice communication.
     *
     * @param listener called with `AudioManager.AUDIOFOCUS_*` change constants.
     * @return one of the `AudioManager.AUDIOFOCUS_REQUEST_*` result codes.
     */
    fun request(listener: (Int) -> Unit): Int

    /** Abandons a previously granted request. */
    fun abandon()
}

/**
 * Android implementation of [AudioFocusOperations] using the API 26 focus request.
 *
 * @param audioManager the system audio manager used for the request.
 */
private class PlatformAudioFocusOperations(
    private val audioManager: AudioManager,
) : AudioFocusOperations {

    /** Request retained so teardown can abandon exactly the same instance. */
    private var request: AudioFocusRequest? = null

    /**
     * Requests ducking focus with voice-communication attributes. Listener callbacks are delivered
     * on the main thread, where the router also handles its other audio events.
     *
     * @param listener called with `AudioManager.AUDIOFOCUS_*` change constants.
     * @return one of the `AudioManager.AUDIOFOCUS_REQUEST_*` result codes, or a failed result when
     *   the platform request throws.
     */
    override fun request(listener: (Int) -> Unit): Int {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(
                AudioManager.OnAudioFocusChangeListener { focusChange -> listener(focusChange) },
                Handler(Looper.getMainLooper()),
            )
            .build()
        return try {
            val result = audioManager.requestAudioFocus(focusRequest)
            request = if (result == AudioManager.AUDIOFOCUS_REQUEST_FAILED) null else focusRequest
            result
        } catch (e: RuntimeException) {
            Timber.w(e, "Could not request intercom audio focus")
            request = null
            AudioManager.AUDIOFOCUS_REQUEST_FAILED
        }
    }

    /**
     * Abandons the retained request, ignoring failures so call teardown cannot be interrupted.
     */
    override fun abandon() {
        val focusRequest = request ?: return
        request = null
        try {
            audioManager.abandonAudioFocusRequest(focusRequest)
        } catch (e: RuntimeException) {
            Timber.w(e, "Could not abandon intercom audio focus")
        }
    }
}

/**
 * Owns the app's transient audio focus for one intercom call.
 *
 * The request asks other apps to duck rather than pause, so the rider's radio stays audible while
 * the intercom is in use. A denied or lost focus never tears down the call: WebRTC call audio and
 * the helmet route remain under [BluetoothAudioRouter]'s control.
 *
 * @param operations the platform focus operations, injected for tests.
 */
internal class AudioFocusController(
    private val operations: AudioFocusOperations,
) {

    /** Whether a request is currently held, pending or should still be abandoned at teardown. */
    var isHeld: Boolean = false
        private set

    /**
     * Requests ducking focus once per call. Repeated calls while focus is held are ignored.
     */
    fun request() {
        if (isHeld) {
            Timber.d("Intercom audio focus is already requested")
            return
        }
        val result = operations.request(::handleFocusChange)
        isHeld = result != AudioManager.AUDIOFOCUS_REQUEST_FAILED
        Timber.i("Intercom audio focus request result: $result")
    }

    /**
     * Releases focus at call teardown. Safe to call repeatedly or after a permanent focus loss.
     */
    fun abandon() {
        if (!isHeld) return
        isHeld = false
        operations.abandon()
        Timber.i("Released intercom audio focus")
    }

    /**
     * Logs focus changes without re-requesting or changing the call route. A permanent loss clears
     * the held flag so teardown does not abandon a focus another app now owns.
     *
     * @param focusChange an `AudioManager.AUDIOFOCUS_*` change constant.
     */
    private fun handleFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> Timber.i("Intercom audio focus granted")
            AudioManager.AUDIOFOCUS_LOSS -> {
                isHeld = false
                Timber.w("Intercom audio focus lost; keeping the call and route unchanged")
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Timber.d("Intercom audio focus suspended temporarily: $focusChange")
            }
            else -> Timber.d("Intercom audio focus change: $focusChange")
        }
    }

    companion object {

        /**
         * Creates a controller backed by the platform audio manager.
         *
         * @param audioManager the system audio manager used for focus requests.
         * @return a controller ready for one call lifecycle.
         */
        fun from(audioManager: AudioManager): AudioFocusController =
            AudioFocusController(PlatformAudioFocusOperations(audioManager))
    }
}
