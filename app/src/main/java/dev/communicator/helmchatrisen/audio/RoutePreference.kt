package dev.communicator.helmchatrisen.audio

/**
 * Remembers the rider's speaker choice across routing callbacks.
 *
 * A Bluetooth route timeout latches the speaker so stale SCO broadcasts cannot restart the retry
 * loop forever. That latch is not a rider decision: it is released as soon as a headset connection
 * event proves that the intercom is available again. An explicit rider choice survives such events
 * and changes only on another toggle or a confirmed full disconnect. Keeping the transitions in
 * one object makes the timeout and disconnect rules directly testable.
 */
internal class RoutePreference {
    /** Whether the built-in speaker is currently forced. */
    var isSpeakerForced: Boolean = false
        private set

    /** Whether the current speaker choice came from a route timeout rather than a rider toggle. */
    private var isTimeoutLatch = false

    /** Latches the speaker route after a Bluetooth SCO timeout. */
    fun latchSpeakerAfterTimeout() {
        isSpeakerForced = true
        isTimeoutLatch = true
    }

    /**
     * Applies a rider toggle or resets the choice at call boundaries.
     *
     * @param enable `true` to force the speaker, `false` to restore automatic headset routing.
     */
    fun setForced(enable: Boolean) {
        isSpeakerForced = enable
        isTimeoutLatch = false
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
        if (cleared) {
            isSpeakerForced = false
            isTimeoutLatch = false
        }
        return cleared
    }

    /**
     * Releases a timeout-created speaker latch when a headset connection event arrives. A plain
     * re-detection (`null`) is ignored so stale SCO chatter cannot bounce audio away from the
     * speaker, while an explicit rider choice is never changed by this method.
     *
     * @param connectedKind the headset family reported by a plug, profile, SCO or device-added
     *   event, or `null` when no new connection was announced.
     * @return `true` when the latch was cleared and automatic headset routing must resume.
     */
    fun clearTimeoutLatchOnHeadsetConnected(connectedKind: HeadsetKind?): Boolean {
        if (connectedKind == null || !isTimeoutLatch) return false
        isSpeakerForced = false
        isTimeoutLatch = false
        return true
    }
}
