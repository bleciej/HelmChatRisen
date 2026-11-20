package dev.communicator.helmchatrisen.audio

import android.bluetooth.BluetoothProfile
import android.media.AudioDeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the pure rules used to translate headset broadcasts into an audio route. Broadcasting can
 * arrive before Android enumerates the device, so these helpers decide both the headset family and
 * the preferred communication device without depending on framework state.
 */
class AudioDeviceSelectionTest {

    @Test
    fun wiredPlugEvent_selectsWiredFamily() {
        assertEquals(HeadsetKind.WIRED, headsetKindFromWiredPlug(state = 1))
        assertNull(headsetKindFromWiredPlug(state = 0))
        assertNull(headsetKindFromWiredPlug(state = -1))
    }

    @Test
    fun bluetoothProfileEvent_onlyConnectedSelectsBluetoothFamily() {
        assertEquals(
            HeadsetKind.BLUETOOTH,
            headsetKindFromBluetoothProfile(BluetoothProfile.STATE_CONNECTED),
        )
        assertNull(headsetKindFromBluetoothProfile(BluetoothProfile.STATE_DISCONNECTED))
        assertNull(headsetKindFromBluetoothProfile(BluetoothProfile.STATE_CONNECTING))
    }

    @Test
    fun bluetoothRoute_prefersScoOverA2dp() {
        val selected = selectHeadsetDevice(
            devices = listOf(
                AudioDeviceCandidate(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "Phone"),
                AudioDeviceCandidate(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "Music headset"),
                AudioDeviceCandidate(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, "Helmet intercom"),
            ),
            kind = HeadsetKind.BLUETOOTH,
        )

        assertEquals("Helmet intercom", selected?.name)
        assertEquals(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, selected?.type)
    }

    @Test
    fun bluetoothRoute_ignoresA2dpOnlyOutput() {
        val selected = selectHeadsetDevice(
            devices = listOf(
                AudioDeviceCandidate(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "Phone"),
                AudioDeviceCandidate(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "Music headset"),
            ),
            kind = HeadsetKind.BLUETOOTH,
        )

        assertNull(selected)
    }

    @Test
    fun wiredRoute_prefersUsbOverAnalogueHeadsets() {
        val selected = selectHeadsetDevice(
            devices = listOf(
                AudioDeviceCandidate(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, "Analogue"),
                AudioDeviceCandidate(AudioDeviceInfo.TYPE_USB_HEADSET, "USB headset"),
                AudioDeviceCandidate(AudioDeviceInfo.TYPE_WIRED_HEADSET, "Wired headset"),
            ),
            kind = HeadsetKind.WIRED,
        )

        assertEquals("USB headset", selected?.name)
        assertEquals(AudioDeviceInfo.TYPE_USB_HEADSET, selected?.type)
    }

    @Test
    fun wiredRoute_ignoresBluetoothDevices() {
        val selected = selectHeadsetDevice(
            devices = listOf(
                AudioDeviceCandidate(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, "Helmet"),
                AudioDeviceCandidate(AudioDeviceInfo.TYPE_WIRED_HEADSET, "Wired headset"),
            ),
            kind = HeadsetKind.WIRED,
        )

        assertEquals(AudioDeviceInfo.TYPE_WIRED_HEADSET, selected?.type)
    }

    @Test
    fun noHeadset_neverSelectsACommunicationDevice() {
        val selected = selectHeadsetDevice(
            devices = listOf(AudioDeviceCandidate(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, "Helmet")),
            kind = HeadsetKind.NONE,
        )

        assertNull(selected)
    }

    @Test
    fun audioDeviceCallback_derivesHeadsetFamilyFromPublishedDevices() {
        assertEquals(
            HeadsetKind.BLUETOOTH,
            headsetKindFromAudioDevices(
                listOf(AudioDeviceCandidate(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, "Helmet")),
            ),
        )
        assertEquals(
            HeadsetKind.WIRED,
            headsetKindFromAudioDevices(
                listOf(AudioDeviceCandidate(AudioDeviceInfo.TYPE_USB_HEADSET, "USB headset")),
            ),
        )
        assertNull(
            headsetKindFromAudioDevices(
                listOf(AudioDeviceCandidate(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "Phone")),
            ),
        )
    }

    @Test
    fun forcedSpeaker_outRanksEveryConnectedHeadset() {
        assertEquals(RouteTarget.SPEAKER, resolveRouteTarget(HeadsetKind.BLUETOOTH, true))
        assertEquals(RouteTarget.SPEAKER, resolveRouteTarget(HeadsetKind.WIRED, true))
    }

    @Test
    fun headsetIsUsedWhenTheRiderDidNotForceTheSpeaker() {
        assertEquals(RouteTarget.HEADSET, resolveRouteTarget(HeadsetKind.BLUETOOTH, false))
        assertEquals(RouteTarget.HEADSET, resolveRouteTarget(HeadsetKind.WIRED, false))
    }

    @Test
    fun noHeadset_alwaysUsesTheSpeaker() {
        assertEquals(RouteTarget.SPEAKER, resolveRouteTarget(HeadsetKind.NONE, false))
        assertEquals(RouteTarget.SPEAKER, resolveRouteTarget(HeadsetKind.NONE, true))
    }

    /**
     * The Bluetooth helmet intercom wins even when a USB or wired device is also present, so a
     * partial device event cannot downgrade the active route or make the router think no headset
     * remains.
     */
    @Test
    fun headsetFamilyPriority_bluetoothOutranksWiredRegardlessOfOrder() {
        val devices = listOf(
            AudioDeviceCandidate(AudioDeviceInfo.TYPE_USB_HEADSET, "USB headset"),
            AudioDeviceCandidate(AudioDeviceInfo.TYPE_WIRED_HEADSET, "Wired headset"),
            AudioDeviceCandidate(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, "Helmet intercom"),
        )

        assertEquals(HeadsetKind.BLUETOOTH, headsetKindFromAudioDevices(devices))
        assertEquals(HeadsetKind.BLUETOOTH, headsetKindFromAudioDevices(devices.reversed()))
    }

    @Test
    fun wiredPlugHint_cannotDowngradeADetectedBluetoothIntercom() {
        assertEquals(
            HeadsetKind.BLUETOOTH,
            resolveBroadcastHeadsetKind(
                detectedKind = HeadsetKind.BLUETOOTH,
                forcedKind = HeadsetKind.WIRED,
            ),
        )
        assertEquals(
            HeadsetKind.BLUETOOTH,
            resolveBroadcastHeadsetKind(
                detectedKind = HeadsetKind.WIRED,
                forcedKind = HeadsetKind.BLUETOOTH,
            ),
        )
    }

    @Test
    fun wiredPlugHint_stillEnablesTheRouteBeforeEnumerationCatchesUp() {
        assertEquals(
            HeadsetKind.WIRED,
            resolveBroadcastHeadsetKind(
                detectedKind = HeadsetKind.NONE,
                forcedKind = HeadsetKind.WIRED,
            ),
        )
        assertEquals(
            HeadsetKind.WIRED,
            resolveBroadcastHeadsetKind(
                detectedKind = HeadsetKind.WIRED,
                forcedKind = null,
            ),
        )
        assertEquals(
            HeadsetKind.NONE,
            resolveBroadcastHeadsetKind(
                detectedKind = HeadsetKind.NONE,
                forcedKind = null,
            ),
        )
    }

    @Test
    fun confirmedEmptyDisconnect_clearsTheSpeakerOverride() {
        assertTrue(
            shouldClearSpeakerOverrideOnDisconnect(
                headsetDisconnected = true,
                kind = HeadsetKind.NONE,
            ),
        )
        assertFalse(
            shouldClearSpeakerOverrideOnDisconnect(
                headsetDisconnected = true,
                kind = HeadsetKind.BLUETOOTH,
            ),
        )
        assertFalse(
            shouldClearSpeakerOverrideOnDisconnect(
                headsetDisconnected = false,
                kind = HeadsetKind.NONE,
            ),
        )
    }

    @Test
    fun routeRetry_isScheduledOnlyForActiveBluetoothRoutes() {
        assertTrue(
            shouldScheduleBluetoothRouteRetry(
                kind = HeadsetKind.BLUETOOTH,
                isRoutingActive = true,
                isSpeakerphoneForced = false,
            ),
        )
        assertFalse(
            shouldScheduleBluetoothRouteRetry(
                kind = HeadsetKind.WIRED,
                isRoutingActive = true,
                isSpeakerphoneForced = false,
            ),
        )
        assertFalse(
            shouldScheduleBluetoothRouteRetry(
                kind = HeadsetKind.NONE,
                isRoutingActive = true,
                isSpeakerphoneForced = false,
            ),
        )
        assertFalse(
            shouldScheduleBluetoothRouteRetry(
                kind = HeadsetKind.BLUETOOTH,
                isRoutingActive = false,
                isSpeakerphoneForced = false,
            ),
        )
        assertFalse(
            shouldScheduleBluetoothRouteRetry(
                kind = HeadsetKind.BLUETOOTH,
                isRoutingActive = true,
                isSpeakerphoneForced = true,
            ),
        )
    }

    /**
     * An open SCO channel is itself proof of a helmet intercom, even when the cached profile lookup
     * and device enumeration have not caught up with the connection yet.
     */
    @Test
    fun openScoChannel_isTreatedAsBluetoothHeadset() {
        assertEquals(
            HeadsetKind.BLUETOOTH,
            resolveHeadsetKind(
                hasScoAudioDevice = false,
                hasHeadsetAudioDevice = false,
                isBluetoothScoOn = true,
                hasBluetoothHeadsetProfile = false,
                isWiredHeadsetOn = false,
            ),
        )
    }

    @Test
    fun headsetResolution_bluetoothOutranksWiredAndFallsBackToNone() {
        assertEquals(
            HeadsetKind.BLUETOOTH,
            resolveHeadsetKind(
                hasScoAudioDevice = false,
                hasHeadsetAudioDevice = true,
                isBluetoothScoOn = false,
                hasBluetoothHeadsetProfile = true,
                isWiredHeadsetOn = true,
            ),
        )
        assertEquals(
            HeadsetKind.WIRED,
            resolveHeadsetKind(
                hasScoAudioDevice = false,
                hasHeadsetAudioDevice = true,
                isBluetoothScoOn = false,
                hasBluetoothHeadsetProfile = false,
                isWiredHeadsetOn = false,
            ),
        )
        assertEquals(
            HeadsetKind.NONE,
            resolveHeadsetKind(
                hasScoAudioDevice = false,
                hasHeadsetAudioDevice = false,
                isBluetoothScoOn = false,
                hasBluetoothHeadsetProfile = false,
                isWiredHeadsetOn = false,
            ),
        )
    }

    @Test
    fun wiredHandover_releasesTheBluetoothScoChannel() {
        assertTrue(shouldReleaseScoForHeadsetKind(HeadsetKind.WIRED))
        assertFalse(shouldReleaseScoForHeadsetKind(HeadsetKind.BLUETOOTH))
    }

    @Test
    fun bluetoothProfileEvents_onlyDisconnectRequestsAFreshLookup() {
        assertTrue(shouldRefreshBluetoothProfileAfterEvent(BluetoothProfile.STATE_DISCONNECTED))
        assertTrue(shouldRefreshBluetoothProfileAfterEvent(BluetoothProfile.STATE_CONNECTING))
        assertFalse(shouldRefreshBluetoothProfileAfterEvent(BluetoothProfile.STATE_CONNECTED))
    }

    @Test
    fun deviceCallback_ignoresNonHeadsetsAndWiredAdditionsDuringBluetoothCalls() {
        assertFalse(
            shouldReevaluateAfterDevicesAdded(
                currentKind = HeadsetKind.NONE,
                addedKind = null,
            ),
        )
        assertTrue(
            shouldReevaluateAfterDevicesAdded(
                currentKind = HeadsetKind.NONE,
                addedKind = HeadsetKind.WIRED,
            ),
        )
        assertTrue(
            shouldReevaluateAfterDevicesAdded(
                currentKind = HeadsetKind.WIRED,
                addedKind = HeadsetKind.BLUETOOTH,
            ),
        )
        assertFalse(
            shouldReevaluateAfterDevicesAdded(
                currentKind = HeadsetKind.BLUETOOTH,
                addedKind = HeadsetKind.WIRED,
            ),
        )
        assertFalse(
            shouldReevaluateAfterDevicesAdded(
                currentKind = HeadsetKind.WIRED,
                addedKind = HeadsetKind.WIRED,
            ),
        )
    }
}
