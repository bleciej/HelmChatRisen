package dev.communicator.helmchatrisen.audio

import android.bluetooth.BluetoothProfile
import android.media.AudioDeviceInfo
import android.media.AudioManager

/** Family of audio output that can carry the in-call intercom audio. */
enum class HeadsetKind {
    /** No headset is connected; the phone speaker must be used. */
    NONE,

    /** Bluetooth HFP headset that can carry two-way call audio, for example a helmet intercom. */
    BLUETOOTH,

    /** Wired 3.5 mm or USB headset. */
    WIRED,
}

/**
 * Minimal, JVM-testable projection of an [AudioDeviceInfo] used for route selection.
 *
 * @property type the [AudioDeviceInfo] device type.
 * @property name the product name reported by the platform, or `null` when it is unknown.
 */
internal data class AudioDeviceCandidate(
    val type: Int,
    val name: String? = null,
)

/**
 * Translates the sticky wired-headset plug broadcast into a headset family. Only a plug event is
 * used directly: an unplug must fall back to re-detection because a Bluetooth headset may still be
 * connected.
 *
 * @param state the value of the broadcast's `state` extra.
 * @return [HeadsetKind.WIRED] when a headset was plugged in, otherwise `null`.
 */
internal fun headsetKindFromWiredPlug(state: Int): HeadsetKind? =
    if (wiredHeadsetConnectionState(state) == true) HeadsetKind.WIRED else null

/**
 * Translates a Bluetooth profile connection broadcast into a headset family. Only a connected event
 * is used directly; a disconnect must fall back to re-detection because another headset may still
 * be connected.
 *
 * @param state the value of [BluetoothProfile.EXTRA_STATE].
 * @return [HeadsetKind.BLUETOOTH] when the profile entered the connected state, otherwise `null`.
 */
internal fun headsetKindFromBluetoothProfile(state: Int): HeadsetKind? =
    if (isBluetoothHeadsetConnected(state)) HeadsetKind.BLUETOOTH else null

/**
 * Reconciles a headset family forced by a plug/profile broadcast with a fresh full detection.
 * Bluetooth always wins because helmet intercoms require HFP/SCO; the forced family is used only
 * when detection cannot see a stronger Bluetooth route yet. This lets a wired plug enable the
 * route button immediately without downgrading an active Bluetooth intercom.
 *
 * @param detectedKind the family reported by a full audio-device and Bluetooth profile query.
 * @param forcedKind the family reported by the current broadcast, or `null` to trust detection.
 * @return the headset family the router should activate.
 */
internal fun resolveBroadcastHeadsetKind(
    detectedKind: HeadsetKind,
    forcedKind: HeadsetKind?,
): HeadsetKind = when {
    detectedKind == HeadsetKind.BLUETOOTH -> HeadsetKind.BLUETOOTH
    forcedKind == HeadsetKind.BLUETOOTH -> HeadsetKind.BLUETOOTH
    forcedKind != null -> forcedKind
    else -> detectedKind
}

/**
 * Resolves the active headset family from the signals the router can read without a blocking
 * Bluetooth profile query. An open SCO channel is treated as proof of a helmet intercom, matching
 * the sticky SCO broadcast handling, and Bluetooth always outranks wired/USB routes.
 *
 * @param hasScoAudioDevice whether the platform enumerated a Bluetooth SCO output.
 * @param hasHeadsetAudioDevice whether wired, headphone or USB headset outputs were enumerated,
 *   excluding Bluetooth SCO so suppressed Bluetooth signals cannot select a wired route.
 * @param isBluetoothScoOn whether the legacy SCO switch is currently on.
 * @param hasBluetoothHeadsetProfile whether a cached HFP profile lookup found a headset.
 * @param isWiredHeadsetOn the deprecated wired-headset flag, used only on older releases.
 * @param includeBluetooth whether stale SCO/profile signals may still select a Bluetooth route.
 *   A confirmed HFP disconnect clears this so a lingering SCO channel cannot keep the intercom
 *   route alive, while wired/USB headsets are still considered normally.
 * @return the family the router must activate, or [HeadsetKind.NONE] when nothing is attached.
 */
internal fun resolveHeadsetKind(
    hasScoAudioDevice: Boolean,
    hasHeadsetAudioDevice: Boolean,
    isBluetoothScoOn: Boolean,
    hasBluetoothHeadsetProfile: Boolean,
    isWiredHeadsetOn: Boolean,
    includeBluetooth: Boolean = true,
): HeadsetKind = when {
    includeBluetooth &&
        (hasScoAudioDevice || isBluetoothScoOn || hasBluetoothHeadsetProfile) -> HeadsetKind.BLUETOOTH
    hasHeadsetAudioDevice || isWiredHeadsetOn -> HeadsetKind.WIRED
    else -> HeadsetKind.NONE
}

/**
 * Reports whether activating a headset family must release an existing Bluetooth SCO channel.
 * Wired and USB handovers must close the old HFP session so a later return to Bluetooth starts a
 * fresh SCO connection instead of reusing a channel the platform already tore down.
 *
 * @param kind the headset family being activated.
 * @return `true` when the router must call `stopBluetoothSco()` before selecting the new route.
 */
internal fun shouldReleaseScoForHeadsetKind(kind: HeadsetKind): Boolean =
    kind != HeadsetKind.BLUETOOTH

/**
 * Reports whether a Bluetooth profile broadcast needs a fresh profile query. A connected event is
 * authoritative and updates the cache directly; a disconnect may still leave another HFP headset
 * attached, so the cache must be refreshed off the main thread before clearing the route.
 *
 * @param state the value of [BluetoothProfile.EXTRA_STATE].
 * @return `true` when a background profile refresh is required.
 */
internal fun shouldRefreshBluetoothProfileAfterEvent(state: Int): Boolean =
    !isBluetoothHeadsetConnected(state)

/**
 * Reports whether an explicit disconnect event and a negative HFP lookup together prove that the
 * Bluetooth headset is gone. A negative lookup on its own can be a transient profile/permission
 * race and must not latch suppression, otherwise a still-connected helmet cannot reconnect. Once a
 * real disconnect is confirmed, stale SCO and cached-profile signals must stop keeping the intercom
 * route alive.
 *
 * @param disconnectReported whether a plug/profile/SCO event reported a disconnect.
 * @param profileStillConnected whether the fresh HFP lookup still found a headset.
 * @return `true` when Bluetooth signals must be ignored for the next route decision.
 */
internal fun shouldIgnoreStaleBluetoothSignals(
    disconnectReported: Boolean,
    profileStillConnected: Boolean,
): Boolean = disconnectReported && !profileStillConnected

/**
 * Reports whether a newly enumerated Bluetooth SCO output proves that a helmet reconnected after a
 * confirmed HFP disconnect. The router must drop stale-signal suppression in that case, otherwise a
 * later unforced re-detection could hide the returning headset and fall back to the phone speaker.
 *
 * @param forcedKind the headset family reported by the device-added callback.
 * @param hasScoDevice whether the platform enumerated a Bluetooth SCO output.
 * @return `true` when stale Bluetooth signals must no longer be ignored.
 */
internal fun shouldClearBluetoothSuppressionOnReconnect(
    forcedKind: HeadsetKind?,
    hasScoDevice: Boolean,
): Boolean = ((forcedKind == HeadsetKind.BLUETOOTH) && hasScoDevice)

/**
 * Reports whether intercom teardown may restore the normal audio mode and clear the speaker flag.
 * An active or ringing GSM call owns those settings, so the router must leave them untouched.
 *
 * @param currentMode the mode reported by [AudioManager.getMode].
 * @return `true` when no telephony call owns the audio mode.
 */
internal fun shouldRestoreNormalAudioMode(currentMode: Int): Boolean =
    when (currentMode) {
        AudioManager.MODE_IN_CALL, AudioManager.MODE_RINGTONE -> false
        else -> true
    }

/**
 * Advances the latched stale-Bluetooth suppression after a completed HFP lookup. The decision must
 * survive later broadcasts and retries: a confirmed disconnect also makes the router stop SCO, and
 * the resulting DISCONNECTED event still reports the stale device and profile signals. A fresh
 * positive lookup clears the latch immediately so a newly attached helmet is routed normally.
 *
 * @param profileStillConnected whether the fresh HFP lookup still found a headset.
 * @param disconnectConfirmedByLookup whether this lookup proved that the headset is gone.
 * @param previouslyIgnoring whether stale Bluetooth signals were already being ignored.
 * @return `true` while stale SCO and cached-profile signals must continue to be ignored.
 */
internal fun advanceStaleBluetoothSignalSuppression(
    profileStillConnected: Boolean,
    disconnectConfirmedByLookup: Boolean,
    previouslyIgnoring: Boolean,
): Boolean = !profileStillConnected && (disconnectConfirmedByLookup || previouslyIgnoring)

/** Reports whether an SCO broadcast represents a fully closed channel. */
@Suppress("DEPRECATION")
internal fun isScoDisconnectState(state: Int): Boolean =
    state == AudioManager.SCO_AUDIO_STATE_DISCONNECTED

/**
 * Reports whether the local SCO-stop token has been resolved by this broadcast. A DISCONNECTED
 * event completes the stop; a later CONNECTED event proves that a new channel superseded it, so a
 * stale token must not suppress the next genuine disconnect. Intermediate states leave the token
 * intact because the platform can emit CONNECTING before DISCONNECTED.
 *
 * @param state the SCO state reported by the broadcast.
 * @return `true` when the token must be cleared after this event.
 */
@Suppress("DEPRECATION")
internal fun shouldRetireScoStopToken(state: Int): Boolean =
    isScoDisconnectState(state) || (state == AudioManager.SCO_AUDIO_STATE_CONNECTED)

/**
 * Reports whether an SCO disconnect is the expected side effect of this router closing the channel
 * (speaker toggle or wired handover) rather than the headset actually going away. Such broadcasts
 * must not refresh the HFP profile with a disconnect flag or clear the rider's speaker choice: the
 * resulting empty profile lookup would mark a still-connected headset as absent until Bluetooth is
 * restarted. A rider's forced-speaker preference is deliberately not evidence: a physical
 * disconnect during speaker playback must still be detected.
 *
 * @param state the SCO state reported by the broadcast.
 * @param scoStopRequestedLocally whether the router itself called `stopBluetoothSco()`.
 * @return `true` when the disconnect must be treated as app-initiated.
 */
internal fun isExpectedScoDisconnect(
    state: Int,
    scoStopRequestedLocally: Boolean,
): Boolean = isScoDisconnectState(state) && scoStopRequestedLocally

/**
 * Reports whether an audio-device removal is only the SCO output disappearing because this router
 * stopped it for a speaker toggle or wired handover. Treating that removal as a physical helmet
 * disconnect would latch stale-signal suppression and clear the rider's speaker choice, after which
 * a re-enumerated SCO device would send intercom audio back to the headset.
 *
 * @param removedDevices devices reported as removed by the platform callback.
 * @param remainingDevices devices still enumerated after the removal.
 * @param scoStopRequestedLocally whether the router itself called `stopBluetoothSco()`.
 * @return `true` when the removal must be ignored as a local side effect.
 */
internal fun isExpectedLocalScoStopRemoval(
    removedDevices: Iterable<AudioDeviceCandidate>,
    remainingDevices: Iterable<AudioDeviceCandidate>,
    scoStopRequestedLocally: Boolean,
): Boolean = scoStopRequestedLocally &&
    hasBluetoothScoDevice(removedDevices) &&
    !hasBluetoothScoDevice(remainingDevices) &&
    !hasNonBluetoothHeadsetDevice(removedDevices)

/**
 * Reports whether a noisy-audio broadcast is the platform reaction to a locally stopped SCO channel.
 * The Bluetooth HFP profile still reports the helmet in that case, so only a separate profile or
 * plug disconnect may clear the rider's route state.
 *
 * @param scoStopRequestedLocally whether the router itself called `stopBluetoothSco()`.
 * @param hasScoOutput whether the platform still enumerates a Bluetooth SCO output.
 * @return `true` when the noisy event must be ignored as a local side effect.
 */
internal fun shouldIgnoreNoisyAudioAfterLocalScoStop(
    scoStopRequestedLocally: Boolean,
    hasScoOutput: Boolean,
): Boolean = scoStopRequestedLocally && !hasScoOutput

/**
 * Chooses the route state published after a transient SCO error. While a headset is still attached
 * the last named route is kept, so the call control does not fall back to the generic intercom label
 * during the bounded retry that reconnects the channel. Only a failure with no headset at all is
 * reported as an error.
 *
 * @param kind the headset family still detected after the error.
 * @param lastConnectedName the device name from the last successful headset route, or `null`.
 * @return the routing state to publish.
 */
internal fun resolveScoErrorRoutingState(
    kind: HeadsetKind,
    lastConnectedName: String?,
): AudioRoutingState = when {
    kind == HeadsetKind.NONE -> AudioRoutingState.Error("Bluetooth SCO connection failed")
    lastConnectedName != null -> AudioRoutingState.HeadsetConnected(lastConnectedName)
    else -> AudioRoutingState.ConnectingBluetooth
}

/**
 * Reports whether a confirmed disconnect leaves no headset and must reset the rider's speaker
 * choice so the next headset becomes active automatically.
 *
 * @param headsetDisconnected whether a plug/profile/device/SCO event confirmed a disconnect.
 * @param kind the headset family still detected after the event.
 * @return `true` when the forced-speaker flag and SCO state must be cleared.
 */
internal fun shouldClearSpeakerOverrideOnDisconnect(
    headsetDisconnected: Boolean,
    kind: HeadsetKind,
): Boolean = (headsetDisconnected && (kind == HeadsetKind.NONE))

/**
 * Reports whether communication-device selection still needs bounded Bluetooth retries. Wired and
 * USB headsets are published through the platform device callback and must never fall back to the
 * speaker after a Bluetooth SCO timeout while the headset remains connected.
 *
 * @param kind the connected headset family.
 * @param isRoutingActive whether audio routing is currently active.
 * @param isSpeakerphoneForced whether the rider explicitly chose the speaker.
 * @return `true` only for an active, non-speaker Bluetooth route.
 */
internal fun shouldScheduleBluetoothRouteRetry(
    kind: HeadsetKind,
    isRoutingActive: Boolean,
    isSpeakerphoneForced: Boolean,
): Boolean = (isRoutingActive && (kind == HeadsetKind.BLUETOOTH) && !isSpeakerphoneForced)

/**
 * Reports whether a new intercom call may switch the platform to
 * [AudioManager.MODE_IN_COMMUNICATION]. The mode is entered exactly once when routing starts:
 * repeatedly re-entering it during a call makes OEM audio policies duck or pause background
 * radio/music. Telephony-owned modes and an already-correct mode are left untouched.
 *
 * @param isRoutingActive whether the router is starting to own call audio.
 * @param currentMode the audio mode reported by the platform.
 * @return `true` only for an active call starting from [AudioManager.MODE_NORMAL].
 */
internal fun shouldEnterCommunicationMode(isRoutingActive: Boolean, currentMode: Int): Boolean =
    isRoutingActive && (currentMode == AudioManager.MODE_NORMAL)

/**
 * Reports whether newly published audio devices can change the active headset route. Non-headset
 * additions and a second wired device while Bluetooth is active are ignored so repeated callbacks
 * cannot restart the Bluetooth retry sequence.
 *
 * @param currentKind the headset family currently driving the route.
 * @param addedKind the strongest headset family present in the added-device batch, or `null`.
 * @return `true` when the router must re-detect and reapply the route.
 */
internal fun shouldReevaluateAfterDevicesAdded(
    currentKind: HeadsetKind,
    addedKind: HeadsetKind?,
): Boolean = (
    (addedKind != null) &&
        ((currentKind == HeadsetKind.NONE) || (addedKind == HeadsetKind.BLUETOOTH))
    )

/**
 * Picks the best communication device for the connected headset family. Bluetooth voice calls
 * prefer the SCO device, while wired connections prefer USB over analogue headsets.
 *
 * @param devices the devices currently offered by [AudioManager.getAvailableCommunicationDevices].
 * @param kind the connected headset family.
 * @return the preferred matching device, or `null` when the platform has not published it yet.
 */
internal fun selectHeadsetDevice(
    devices: Iterable<AudioDeviceCandidate>,
    kind: HeadsetKind,
): AudioDeviceCandidate? {
    val preferredTypes = when (kind) {
        // A2DP is a music output and is not accepted by setCommunicationDevice(), so HFP/SCO is
        // the only Bluetooth device that can carry this app's two-way intercom audio.
        HeadsetKind.BLUETOOTH -> intArrayOf(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
        HeadsetKind.WIRED -> intArrayOf(
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        )
        HeadsetKind.NONE -> return null
    }
    return preferredTypes.asSequence().firstNotNullOfOrNull { type ->
        devices.firstOrNull { (candidateType, _) -> candidateType == type }
    }
}

/**
 * Reports whether the supplied devices contain a wired or USB headset, explicitly excluding
 * Bluetooth SCO. SCO devices are part of [isHeadsetDeviceType], but while stale Bluetooth signals
 * are suppressed they must not be misclassified as a wired route: that would stop SCO and publish a
 * nameless headset route instead of falling back to the phone speaker after a real disconnect.
 *
 * @param devices the audio devices to inspect.
 * @return `true` when at least one wired or USB headset is present.
 */
internal fun hasNonBluetoothHeadsetDevice(devices: Iterable<AudioDeviceCandidate>): Boolean =
    devices.any { (deviceType, _) ->
        (deviceType != AudioDeviceInfo.TYPE_BLUETOOTH_SCO) && isHeadsetDeviceType(deviceType)
    }

/**
 * Derives a headset family directly from devices reported by an audio-device callback. Platform
 * callbacks can arrive before a corresponding plug/profile broadcast, so this avoids waiting for a
 * separate query that may still return a stale device list.
 *
 * @param devices the devices supplied by the platform callback.
 * @return [HeadsetKind.BLUETOOTH] for SCO devices, [HeadsetKind.WIRED] for wired or USB devices,
 *   or `null` when none of the devices can carry intercom audio.
 */
internal fun headsetKindFromAudioDevices(devices: Iterable<AudioDeviceCandidate>): HeadsetKind? =
    when {
        hasBluetoothScoDevice(devices) -> HeadsetKind.BLUETOOTH
        devices.any { (deviceType, _) -> isHeadsetDeviceType(deviceType) } -> HeadsetKind.WIRED
        else -> null
    }

/**
 * Reports whether the platform still enumerates a Bluetooth SCO output. This is the most reliable
 * disconnect signal on devices whose HFP profile query is unsupported by the OEM Bluetooth stack.
 *
 * @param devices the devices currently enumerated by [AudioManager.getDevices].
 * @return `true` when at least one Bluetooth SCO output is available.
 */
internal fun hasBluetoothScoDevice(devices: Iterable<AudioDeviceCandidate>): Boolean =
    devices.any { (deviceType, _) -> deviceType == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }

/**
 * Reports whether a device-removal callback proves that the last Bluetooth HFP route is gone. A
 * single SCO removal must not clear the cached HFP state while another SCO output remains.
 *
 * @param removedDevices devices reported as removed by the platform callback.
 * @param remainingDevices devices still enumerated after the removal.
 * @return `true` when the router must drop its cached HFP state and suppress stale Bluetooth signals.
 */
internal fun shouldConfirmBluetoothDisconnectFromDevices(
    removedDevices: Iterable<AudioDeviceCandidate>,
    remainingDevices: Iterable<AudioDeviceCandidate>,
): Boolean = hasBluetoothScoDevice(removedDevices) && !hasBluetoothScoDevice(remainingDevices)

/**
 * Maps a platform audio device to the testable projection used by [selectHeadsetDevice].
 *
 * @receiver the platform device.
 * @return the device type and product name.
 */
internal fun AudioDeviceInfo.toCandidate(): AudioDeviceCandidate = AudioDeviceCandidate(
    type = type,
    name = productName?.toString(),
)

/** Output the router should drive for the current headset and the rider's preference. */
internal enum class RouteTarget {
    /** Built-in speaker: either no headset is connected or the rider explicitly chose it. */
    SPEAKER,

    /** The connected headset or helmet intercom. */
    HEADSET,
}

/**
 * Decides which output wins. An explicit rider choice for the speaker outranks a connected
 * headset; otherwise the headset is used whenever one is available.
 *
 * @param kind the detected headset family.
 * @param isSpeakerphoneForced whether the rider tapped the speaker route.
 * @return the output the router should activate.
 */
internal fun resolveRouteTarget(kind: HeadsetKind, isSpeakerphoneForced: Boolean): RouteTarget =
    if (isSpeakerphoneForced || (kind == HeadsetKind.NONE)) {
        RouteTarget.SPEAKER
    } else {
        RouteTarget.HEADSET
    }
