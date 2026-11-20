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
 * @param hasHeadsetAudioDevice whether wired, headphone or USB headset outputs were enumerated.
 * @param isBluetoothScoOn whether the legacy SCO switch is currently on.
 * @param hasBluetoothHeadsetProfile whether a cached HFP profile lookup found a headset.
 * @param isWiredHeadsetOn the deprecated wired-headset flag, used only on older releases.
 * @return the family the router must activate, or [HeadsetKind.NONE] when nothing is attached.
 */
internal fun resolveHeadsetKind(
    hasScoAudioDevice: Boolean,
    hasHeadsetAudioDevice: Boolean,
    isBluetoothScoOn: Boolean,
    hasBluetoothHeadsetProfile: Boolean,
    isWiredHeadsetOn: Boolean,
): HeadsetKind = when {
    hasScoAudioDevice || isBluetoothScoOn || hasBluetoothHeadsetProfile -> HeadsetKind.BLUETOOTH
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
): Boolean = (headsetDisconnected && kind == HeadsetKind.NONE)

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
): Boolean = (isRoutingActive && kind == HeadsetKind.BLUETOOTH && !isSpeakerphoneForced)

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
    addedKind != null &&
        (currentKind == HeadsetKind.NONE || addedKind == HeadsetKind.BLUETOOTH)
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
        devices.any { (deviceType, _) -> deviceType == AudioDeviceInfo.TYPE_BLUETOOTH_SCO } ->
            HeadsetKind.BLUETOOTH
        devices.any { (deviceType, _) -> isHeadsetDeviceType(deviceType) } -> HeadsetKind.WIRED
        else -> null
    }

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
    if ((isSpeakerphoneForced || kind == HeadsetKind.NONE)) {
        RouteTarget.SPEAKER
    } else {
        RouteTarget.HEADSET
    }
