package dev.communicator.helmchatrisen.audio

/**
 * Remembers the rider's explicit speaker choice across routing callbacks.
 *
 * The choice is latched when a Bluetooth route times out, cleared only by a confirmed disconnect
 * that leaves no headset, and otherwise mirrors the rider's speaker/headset toggle. Keeping the
 * transitions in one object makes the timeout latch and disconnect rules directly testable.
 */
internal class RoutePreference {
    /** Whether the built-in speaker is currently forced. */
    var isSpeakerForced: Boolean = false
        private set

    /** Latches the speaker route after a Bluetooth SCO timeout. */
    fun latchSpeakerAfterTimeout() {
        isSpeakerForced = true
    }

    /**
     * Applies a rider toggle or resets the choice at call boundaries.
     *
     * @param enable `true` to force the speaker, `false` to restore automatic headset routing.
     */
    fun setForced(enable: Boolean) {
        isSpeakerForced = enable
    }

    /**
     * Clears the choice only when a confirmed disconnect leaves no headset at all. A late SCO event
     * while another headset remains connected must not undo a rider's speaker choice.
     *
     * @param headsetDisconnected whether a plug/profile/device/SCO event reported a disconnect.
     * @param kind the headset family still detected after the event.
     * @return `true` when the choice was cleared and the caller must release the SCO channel.
     */
    fun clearOnDisconnect(headsetDisconnected: Boolean, kind: HeadsetKind): Boolean {
        val cleared = shouldClearSpeakerOverrideOnDisconnect(headsetDisconnected, kind)
        if (cleared) isSpeakerForced = false
        return cleared
    }
}
