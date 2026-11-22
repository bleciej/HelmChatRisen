package dev.communicator.helmchatrisen.audio

import android.bluetooth.BluetoothProfile
import android.media.AudioDeviceInfo
import android.media.AudioManager
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
    fun lastScoDeviceRemoval_confirmsBluetoothDisconnect() {
        val removed = listOf(
            AudioDeviceCandidate(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, "Helmet"),
            AudioDeviceCandidate(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, "Helmet"),
        )
        val remaining = listOf(
            AudioDeviceCandidate(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "Phone"),
        )

        assertTrue(hasBluetoothScoDevice(removed))
        assertFalse(hasBluetoothScoDevice(remaining))
        assertTrue(
            shouldConfirmBluetoothDisconnectFromDevices(
                removedDevices = removed,
                remainingDevices = remaining,
            ),
        )
    }

    @Test
    fun removalWithAnotherScoDevice_keepsTheBluetoothRoute() {
        val removed = listOf(
            AudioDeviceCandidate(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, "Helmet A"),
        )
        val remaining = listOf(
            AudioDeviceCandidate(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, "Helmet B"),
        )

        assertFalse(
            shouldConfirmBluetoothDisconnectFromDevices(
                removedDevices = removed,
                remainingDevices = remaining,
            ),
        )
    }

    @Test
    fun wiredOnlyRemoval_doesNotConfirmBluetoothDisconnect() {
        val removed = listOf(
            AudioDeviceCandidate(AudioDeviceInfo.TYPE_WIRED_HEADSET, "Wired headset"),
        )
        val remaining = listOf(
            AudioDeviceCandidate(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "Phone"),
        )

        assertFalse(
            shouldConfirmBluetoothDisconnectFromDevices(
                removedDevices = removed,
                remainingDevices = remaining,
            ),
        )
    }

    /**
     * While stale Bluetooth signals are suppressed, an SCO device must not be counted as a wired
     * headset. Otherwise the router would stop SCO and publish a nameless intercom route instead of
     * routing to the phone speaker.
     */
    @Test
    fun suppressedBluetooth_scoDeviceIsNotAWiredHeadset() {
        val scoOnly = listOf(
            AudioDeviceCandidate(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, "Helmet intercom"),
            AudioDeviceCandidate(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "Phone"),
        )

        assertFalse(hasNonBluetoothHeadsetDevice(scoOnly))
        assertTrue(
            hasNonBluetoothHeadsetDevice(
                scoOnly + AudioDeviceCandidate(AudioDeviceInfo.TYPE_WIRED_HEADSET, "Wired headset"),
            ),
        )
    }

    @Test
    fun forcedSpeaker_outRanksEveryConnectedHeadset() {
        assertEquals(
            RouteTarget.SPEAKER,
            resolveRouteTarget(HeadsetKind.BLUETOOTH, isSpeakerphoneForced = true),
        )
        assertEquals(
            RouteTarget.SPEAKER,
            resolveRouteTarget(HeadsetKind.WIRED, isSpeakerphoneForced = true),
        )
    }

    @Test
    fun headsetIsUsedWhenTheRiderDidNotForceTheSpeaker() {
        assertEquals(
            RouteTarget.HEADSET,
            resolveRouteTarget(HeadsetKind.BLUETOOTH, isSpeakerphoneForced = false),
        )
        assertEquals(
            RouteTarget.HEADSET,
            resolveRouteTarget(HeadsetKind.WIRED, isSpeakerphoneForced = false),
        )
    }

    @Test
    fun noHeadset_alwaysUsesTheSpeaker() {
        assertEquals(
            RouteTarget.SPEAKER,
            resolveRouteTarget(HeadsetKind.NONE, isSpeakerphoneForced = false),
        )
        assertEquals(
            RouteTarget.SPEAKER,
            resolveRouteTarget(HeadsetKind.NONE, isSpeakerphoneForced = true),
        )
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

    /**
     * Communication mode is entered once when a call starts from normal mode. Telephony-owned modes
     * and an already-correct mode are not written again, so OEM audio policies are not repeatedly
     * told to duck or pause background media.
     */
    @Test
    fun communicationMode_isEnteredOnlyOnceFromNormalMode() {
        assertTrue(
            shouldEnterCommunicationMode(
                isRoutingActive = true,
                currentMode = AudioManager.MODE_NORMAL,
            ),
        )
        // MODE_RINGTONE is telephony-owned: an incoming GSM call keeps its own audio path.
        assertFalse(
            shouldEnterCommunicationMode(
                isRoutingActive = true,
                currentMode = AudioManager.MODE_RINGTONE,
            ),
        )
        assertFalse(
            shouldEnterCommunicationMode(
                isRoutingActive = true,
                currentMode = AudioManager.MODE_IN_COMMUNICATION,
            ),
        )
        assertFalse(
            shouldEnterCommunicationMode(
                isRoutingActive = true,
                currentMode = AudioManager.MODE_IN_CALL,
            ),
        )
        assertFalse(
            shouldEnterCommunicationMode(
                isRoutingActive = false,
                currentMode = AudioManager.MODE_NORMAL,
            ),
        )
        assertFalse(
            shouldEnterCommunicationMode(
                isRoutingActive = false,
                currentMode = AudioManager.MODE_IN_COMMUNICATION,
            ),
        )
    }

    /**
     * Only an explicit disconnect event confirmed by a negative lookup may latch suppression. A
     * negative lookup on its own can be a transient profile/permission race and must not block a
     * still-connected helmet from reconnecting.
     */
    @Test
    fun staleBluetoothSignals_requireAnExplicitDisconnectAndANegativeLookup() {
        assertTrue(
            shouldIgnoreStaleBluetoothSignals(
                disconnectReported = true,
                profileStillConnected = false,
            ),
        )
        assertFalse(
            shouldIgnoreStaleBluetoothSignals(
                disconnectReported = true,
                profileStillConnected = true,
            ),
        )
        assertFalse(
            shouldIgnoreStaleBluetoothSignals(
                disconnectReported = false,
                profileStillConnected = false,
            ),
        )
        assertFalse(
            shouldIgnoreStaleBluetoothSignals(
                disconnectReported = false,
                profileStillConnected = true,
            ),
        )
    }

    /**
     * Reproduces the disconnect report: SCO and cached profile still claim a headset, but once the
     * lookup disproves it the route must fall back to the phone speaker.
     */
    @Test
    fun confirmedBluetoothDisconnect_ignoresStaleScoSignalsAndUsesSpeaker() {
        val kind = resolveHeadsetKind(
            hasScoAudioDevice = true,
            hasHeadsetAudioDevice = false,
            isBluetoothScoOn = true,
            hasBluetoothHeadsetProfile = true,
            isWiredHeadsetOn = false,
            includeBluetooth = false,
        )

        assertEquals(HeadsetKind.NONE, kind)
        assertEquals(RouteTarget.SPEAKER, resolveRouteTarget(kind, isSpeakerphoneForced = false))
    }

    /**
     * The speaker toggle and wired handover close SCO themselves. The resulting disconnect
     * broadcast must not be read as the headset going away, otherwise an empty HFP lookup detaches
     * the still-paired headset until Bluetooth is restarted. The local stop token is the only proof
     * of an app-initiated teardown: a rider's forced-speaker choice is not passed to this rule.
     */
    @Test
    @Suppress("DEPRECATION")
    fun expectedScoDisconnect_isDetectedOnlyForAppInitiatedTeardown() {
        assertTrue(
            isExpectedScoDisconnect(
                state = AudioManager.SCO_AUDIO_STATE_DISCONNECTED,
                scoStopRequestedLocally = true,
            ),
        )
        // A helmet that physically disconnects during speaker playback is a genuine disconnect,
        // even though the rider has chosen the built-in speaker.
        assertFalse(
            isExpectedScoDisconnect(
                state = AudioManager.SCO_AUDIO_STATE_DISCONNECTED,
                scoStopRequestedLocally = false,
            ),
        )
        assertFalse(
            isExpectedScoDisconnect(
                state = AudioManager.SCO_AUDIO_STATE_CONNECTED,
                scoStopRequestedLocally = true,
            ),
        )
    }

    /**
     * The local-stop token belongs to the DISCONNECTED event caused by stopBluetoothSco(). An
     * intermediate CONNECTING broadcast must not consume it, or the real disconnect that follows
     * would be treated as a physical helmet removal and clear the rider's speaker choice.
     */
    @Test
    @Suppress("DEPRECATION")
    fun scoStopToken_survivesIntermediateStatesUntilDisconnect() {
        assertFalse(isScoDisconnectState(AudioManager.SCO_AUDIO_STATE_CONNECTING))
        assertFalse(shouldRetireScoStopToken(AudioManager.SCO_AUDIO_STATE_CONNECTING))
        assertFalse(
            isExpectedScoDisconnect(
                state = AudioManager.SCO_AUDIO_STATE_CONNECTING,
                scoStopRequestedLocally = true,
            ),
        )
        assertTrue(
            isExpectedScoDisconnect(
                state = AudioManager.SCO_AUDIO_STATE_DISCONNECTED,
                scoStopRequestedLocally = true,
            ),
        )
        assertTrue(shouldRetireScoStopToken(AudioManager.SCO_AUDIO_STATE_DISCONNECTED))
        // A confirmed new channel supersedes an older stop request.
        assertTrue(shouldRetireScoStopToken(AudioManager.SCO_AUDIO_STATE_CONNECTED))
    }

    /**
     * Removing the SCO output while this router stops it for the speaker must not latch a false
     * helmet disconnect. A simultaneous wired removal or a still-present SCO output is real state
     * change and must still be processed.
     */
    @Test
    fun localScoStopRemoval_isIgnoredOnlyForTheScoOutput() {
        val removedSco = listOf(
            AudioDeviceCandidate(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, "Helmet"),
        )
        val remainingSpeaker = listOf(
            AudioDeviceCandidate(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "Phone"),
        )

        assertTrue(
            isExpectedLocalScoStopRemoval(
                removedDevices = removedSco,
                remainingDevices = remainingSpeaker,
                scoStopRequestedLocally = true,
            ),
        )
        assertFalse(
            isExpectedLocalScoStopRemoval(
                removedDevices = removedSco,
                remainingDevices = remainingSpeaker,
                scoStopRequestedLocally = false,
            ),
        )
        assertFalse(
            isExpectedLocalScoStopRemoval(
                removedDevices = removedSco,
                remainingDevices = removedSco,
                scoStopRequestedLocally = true,
            ),
        )
        assertFalse(
            isExpectedLocalScoStopRemoval(
                removedDevices = removedSco + AudioDeviceCandidate(
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    "Wired headset",
                ),
                remainingDevices = remainingSpeaker,
                scoStopRequestedLocally = true,
            ),
        )
    }

    /** A noisy event from a locally stopped SCO channel must not clear a real HFP connection. */
    @Test
    fun noisyAudioFromLocalScoStop_isIgnoredOnlyWithoutScoOutput() {
        assertTrue(
            shouldIgnoreNoisyAudioAfterLocalScoStop(
                scoStopRequestedLocally = true,
                hasScoOutput = false,
            ),
        )
        assertFalse(
            shouldIgnoreNoisyAudioAfterLocalScoStop(
                scoStopRequestedLocally = true,
                hasScoOutput = true,
            ),
        )
        assertFalse(
            shouldIgnoreNoisyAudioAfterLocalScoStop(
                scoStopRequestedLocally = false,
                hasScoOutput = false,
            ),
        )
    }

    /**
     * A wired or USB headset remains a valid route after the Bluetooth helmet disconnects, so the
     * stale SCO signals must not force the phone speaker while another intercom is still attached.
     */
    @Test
    fun confirmedBluetoothDisconnect_keepsAWiredHeadsetAsTheActiveFamily() {
        assertEquals(
            HeadsetKind.WIRED,
            resolveHeadsetKind(
                hasScoAudioDevice = true,
                hasHeadsetAudioDevice = true,
                isBluetoothScoOn = true,
                hasBluetoothHeadsetProfile = true,
                isWiredHeadsetOn = false,
                includeBluetooth = false,
            ),
        )
    }

    @Test
    fun staleBluetoothSuppression_latchesUntilAFreshPositiveLookup() {
        assertFalse(
            advanceStaleBluetoothSignalSuppression(
                profileStillConnected = false,
                disconnectConfirmedByLookup = false,
                previouslyIgnoring = false,
            ),
        )
        assertTrue(
            advanceStaleBluetoothSignalSuppression(
                profileStillConnected = false,
                disconnectConfirmedByLookup = true,
                previouslyIgnoring = false,
            ),
        )
        // The latch survives later lookups that cannot prove a new connection.
        assertTrue(
            advanceStaleBluetoothSignalSuppression(
                profileStillConnected = false,
                disconnectConfirmedByLookup = false,
                previouslyIgnoring = true,
            ),
        )
        // A fresh positive lookup releases it immediately, even if a disconnect is still pending.
        assertFalse(
            advanceStaleBluetoothSignalSuppression(
                profileStillConnected = true,
                disconnectConfirmedByLookup = true,
                previouslyIgnoring = true,
            ),
        )
    }

    /**
     * Drives the full teardown sequence with pure decisions: a confirmed HFP disconnect latches the
     * suppression, stopping SCO emits a stale DISCONNECTED broadcast, and the route must stay on the
     * speaker until a fresh positive connection releases the latch.
     */
    @Test
    @Suppress("DEPRECATION")
    fun confirmedDisconnect_staleScoBroadcastKeepsTheSpeakerRoute() {
        var ignoringStaleBluetoothSignals = advanceStaleBluetoothSignalSuppression(
            profileStillConnected = false,
            disconnectConfirmedByLookup = shouldIgnoreStaleBluetoothSignals(
                disconnectReported = true,
                profileStillConnected = false,
            ),
            previouslyIgnoring = false,
        )
        assertTrue(ignoringStaleBluetoothSignals)

        // The stale SCO shutdown is not app-initiated once the local stop token is consumed, but the
        // latched decision must still hide the lingering device and SCO flag.
        assertFalse(
            isExpectedScoDisconnect(
                state = AudioManager.SCO_AUDIO_STATE_DISCONNECTED,
                scoStopRequestedLocally = false,
            ),
        )
        val staleKind = resolveHeadsetKind(
            hasScoAudioDevice = true,
            hasHeadsetAudioDevice = false,
            isBluetoothScoOn = true,
            hasBluetoothHeadsetProfile = true,
            isWiredHeadsetOn = false,
            includeBluetooth = !ignoringStaleBluetoothSignals,
        )
        assertEquals(HeadsetKind.NONE, staleKind)
        assertEquals(RouteTarget.SPEAKER, resolveRouteTarget(staleKind, isSpeakerphoneForced = false))

        ignoringStaleBluetoothSignals = advanceStaleBluetoothSignalSuppression(
            profileStillConnected = true,
            disconnectConfirmedByLookup = false,
            previouslyIgnoring = ignoringStaleBluetoothSignals,
        )
        assertFalse(ignoringStaleBluetoothSignals)
        assertEquals(
            HeadsetKind.BLUETOOTH,
            resolveHeadsetKind(
                hasScoAudioDevice = true,
                hasHeadsetAudioDevice = false,
                isBluetoothScoOn = true,
                hasBluetoothHeadsetProfile = true,
                isWiredHeadsetOn = false,
                includeBluetooth = !ignoringStaleBluetoothSignals,
            ),
        )
    }

    /**
     * Background A2DP music can suspend and resume the SCO channel. While another headset family is
     * still attached, the named intercom route must survive a transient error so the control keeps
     * showing the helmet instead of the generic intercom label.
     */
    @Test
    fun scoError_keepsTheLastNamedHeadsetRoute() {
        assertEquals(
            AudioRoutingState.HeadsetConnected("Helmet intercom"),
            resolveScoErrorRoutingState(
                kind = HeadsetKind.BLUETOOTH,
                lastConnectedName = "Helmet intercom",
            ),
        )
        assertEquals(
            AudioRoutingState.HeadsetConnected("USB headset"),
            resolveScoErrorRoutingState(
                kind = HeadsetKind.WIRED,
                lastConnectedName = "USB headset",
            ),
        )
    }

    @Test
    fun scoError_withoutANameWaitsForTheHeadset() {
        assertEquals(
            AudioRoutingState.ConnectingBluetooth,
            resolveScoErrorRoutingState(
                kind = HeadsetKind.BLUETOOTH,
                lastConnectedName = null,
            ),
        )
        assertEquals(
            AudioRoutingState.ConnectingBluetooth,
            resolveScoErrorRoutingState(
                kind = HeadsetKind.WIRED,
                lastConnectedName = null,
            ),
        )
    }

    @Test
    fun scoError_withoutAnyHeadsetIsReportedAsAnError() {
        assertEquals(
            AudioRoutingState.Error("Bluetooth SCO connection failed"),
            resolveScoErrorRoutingState(
                kind = HeadsetKind.NONE,
                lastConnectedName = null,
            ),
        )
    }

    /**
     * A helmet reconnect observed only as a newly enumerated SCO output must clear the confirmed
     * disconnect latch. Otherwise a later unforced re-detection would hide the returning intercom.
     */
    @Test
    fun deviceCallbackReconnect_clearsStaleBluetoothSuppression() {
        assertTrue(
            shouldClearBluetoothSuppressionOnReconnect(
                forcedKind = HeadsetKind.BLUETOOTH,
                hasScoDevice = true,
            ),
        )
        assertFalse(
            shouldClearBluetoothSuppressionOnReconnect(
                forcedKind = HeadsetKind.BLUETOOTH,
                hasScoDevice = false,
            ),
        )
        assertFalse(
            shouldClearBluetoothSuppressionOnReconnect(
                forcedKind = HeadsetKind.WIRED,
                hasScoDevice = true,
            ),
        )
        assertFalse(
            shouldClearBluetoothSuppressionOnReconnect(
                forcedKind = null,
                hasScoDevice = true,
            ),
        )
    }

    /** Teardown must not overwrite an audio mode owned by an incoming or active GSM call. */
    @Test
    fun teardown_keepsTelephonyOwnedAudioMode() {
        assertTrue(shouldRestoreNormalAudioMode(AudioManager.MODE_NORMAL))
        assertTrue(shouldRestoreNormalAudioMode(AudioManager.MODE_IN_COMMUNICATION))
        assertFalse(shouldRestoreNormalAudioMode(AudioManager.MODE_IN_CALL))
        assertFalse(shouldRestoreNormalAudioMode(AudioManager.MODE_RINGTONE))
    }
}
