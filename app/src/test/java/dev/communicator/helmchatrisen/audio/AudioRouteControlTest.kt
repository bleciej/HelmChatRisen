package dev.communicator.helmchatrisen.audio

import android.media.AudioDeviceInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the rules behind the call-screen speaker/headset button: with no headset the speaker is
 * the only output and cannot be switched, while a connected headset makes the button a real toggle.
 */
class AudioRouteControlTest {

    @Test
    fun withoutHeadset_theSpeakerIsShownAndSwitchingIsDisabled() {
        val (isSpeaker, canSwitch) = resolveAudioRouteControl(
            isHeadsetConnected = false,
            routingState = AudioRoutingState.EarpieceConnected,
        )

        assertTrue(isSpeaker)
        assertFalse(canSwitch)
    }

    @Test
    fun withoutHeadset_staleBluetoothStateStillShowsSpeaker() {
        val (isSpeaker, canSwitch) = resolveAudioRouteControl(
            isHeadsetConnected = false,
            routingState = AudioRoutingState.HeadsetConnected("Helmet"),
        )

        assertTrue(isSpeaker)
        assertFalse(canSwitch)
    }

    @Test
    fun withoutHeadset_pendingScoConnectionStillShowsSpeakerImmediately() {
        val (isSpeaker, canSwitch) = resolveAudioRouteControl(
            isHeadsetConnected = false,
            routingState = AudioRoutingState.ConnectingBluetooth,
        )

        assertTrue(isSpeaker)
        assertFalse(canSwitch)
    }

    @Test
    fun withHeadsetOnBluetooth_showsHeadsetAndAllowsSwitching() {
        val (isSpeaker, canSwitch) = resolveAudioRouteControl(
            isHeadsetConnected = true,
            routingState = AudioRoutingState.HeadsetConnected("Helmet"),
        )

        assertFalse(isSpeaker)
        assertTrue(canSwitch)
    }

    @Test
    fun withHeadsetOnSpeaker_showsSpeakerAndAllowsSwitching() {
        val (isSpeaker, canSwitch) = resolveAudioRouteControl(
            isHeadsetConnected = true,
            routingState = AudioRoutingState.SpeakerConnected,
        )

        assertTrue(isSpeaker)
        assertTrue(canSwitch)
    }

    @Test
    fun withHeadsetConnecting_showsHeadsetAndAllowsSwitching() {
        val (isSpeaker, canSwitch) = resolveAudioRouteControl(
            isHeadsetConnected = true,
            routingState = AudioRoutingState.ConnectingBluetooth,
        )

        assertFalse(isSpeaker)
        assertTrue(canSwitch)
    }

    @Test
    fun headsetDeviceTypes_coverBluetoothWiredAndUsbHeadsets() {
        listOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
        ).forEach { assertTrue(isHeadsetDeviceType(it)) }
    }

    @Test
    fun builtInOutputsAndA2dp_areNotHeadsetDeviceTypes() {
        assertFalse(isHeadsetDeviceType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
        assertFalse(isHeadsetDeviceType(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE))
        assertFalse(isHeadsetDeviceType(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP))
    }
}
