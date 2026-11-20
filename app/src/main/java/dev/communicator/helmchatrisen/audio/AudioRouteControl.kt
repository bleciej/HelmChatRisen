package dev.communicator.helmchatrisen.audio

import android.media.AudioDeviceInfo

/** Audio output types that can serve as the rider's helmet intercom or headset. */
private val HEADSET_DEVICE_TYPES = setOf(
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_USB_HEADSET,
)

/**
 * Presentation flags for the in-call audio route control.
 *
 * @property isSpeaker whether the control presents the phone speaker as the active route.
 * @property canSwitch whether tapping the control may change the active audio route.
 */
data class AudioRouteControlState(
    val isSpeaker: Boolean,
    val canSwitch: Boolean,
)

/**
 * Reports whether an audio device type can be used as a helmet intercom or headset output.
 *
 * @param type an [AudioDeviceInfo] type constant.
 * @return `true` for Bluetooth SCO, wired and USB headsets; `false` otherwise. A2DP-only music
 *   outputs are excluded because they cannot carry two-way call audio.
 */
internal fun isHeadsetDeviceType(type: Int): Boolean = type in HEADSET_DEVICE_TYPES

/**
 * Derives the in-call audio route control from headset availability and the active route. Without
 * a headset the phone speaker is the only usable output, so the control shows the speaker and
 * cannot be switched. With a headset it mirrors the active route and lets the rider switch between
 * the headset and the speaker.
 *
 * @param isHeadsetConnected whether a helmet intercom or headset is currently available.
 * @param routingState the router's currently active output.
 * @return the flags used to render the route button.
 */
fun resolveAudioRouteControl(
    isHeadsetConnected: Boolean,
    routingState: AudioRoutingState,
): AudioRouteControlState {
    if (!isHeadsetConnected) {
        return AudioRouteControlState(isSpeaker = true, canSwitch = false)
    }
    return AudioRouteControlState(
        isSpeaker = routingState is AudioRoutingState.SpeakerConnected,
        canSwitch = true,
    )
}
