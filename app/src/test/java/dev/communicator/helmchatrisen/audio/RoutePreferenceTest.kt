package dev.communicator.helmchatrisen.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the stateful rider-speaker choice used by the router: a Bluetooth route timeout latches
 * the speaker, a late SCO event cannot restart retries, a real headset connection releases the
 * timeout latch, and only a confirmed disconnect or an explicit rider toggle clears a rider choice.
 */
class RoutePreferenceTest {

    @Test
    fun timeoutLatch_survivesLateScoDisconnectWithHeadsetStillDetected() {
        val preference = RoutePreference()

        preference.latchSpeakerAfterTimeout()
        val cleared = preference.clearOnDisconnect(
            headsetDisconnected = true,
            kind = HeadsetKind.BLUETOOTH,
        )

        assertFalse(cleared)
        assertTrue(preference.isSpeakerForced)
        assertEquals(
            RouteTarget.SPEAKER,
            resolveRouteTarget(HeadsetKind.BLUETOOTH, preference.isSpeakerForced),
        )
    }

    @Test
    fun confirmedFullDisconnect_clearsTheLatchAndAllowsHeadsetRoutingAgain() {
        val preference = RoutePreference()

        preference.latchSpeakerAfterTimeout()
        val cleared = preference.clearOnDisconnect(
            headsetDisconnected = true,
            kind = HeadsetKind.NONE,
        )

        assertTrue(cleared)
        assertFalse(preference.isSpeakerForced)
        assertEquals(
            RouteTarget.HEADSET,
            resolveRouteTarget(HeadsetKind.BLUETOOTH, preference.isSpeakerForced),
        )
    }

    @Test
    fun riderToggle_canReleaseATimeoutLatchImmediately() {
        val preference = RoutePreference()

        preference.latchSpeakerAfterTimeout()
        preference.setForced(enable = false)

        assertFalse(preference.isSpeakerForced)
    }

    @Test
    fun transientEventWithoutDisconnect_doesNotClearTheChoice() {
        val preference = RoutePreference()

        preference.setForced(enable = true)
        val cleared = preference.clearOnDisconnect(
            headsetDisconnected = false,
            kind = HeadsetKind.NONE,
        )

        assertFalse(cleared)
        assertTrue(preference.isSpeakerForced)
    }

    @Test
    fun headsetConnectionAfterTimeout_resumesAutomaticHeadsetRouting() {
        val preference = RoutePreference()

        preference.latchSpeakerAfterTimeout()
        val cleared = preference.clearTimeoutLatchOnHeadsetConnected(HeadsetKind.BLUETOOTH)

        assertTrue(cleared)
        assertFalse(preference.isSpeakerForced)
        assertEquals(
            RouteTarget.HEADSET,
            resolveRouteTarget(HeadsetKind.BLUETOOTH, preference.isSpeakerForced),
        )
    }

    @Test
    fun plainRedetectionAfterTimeout_keepsTheSpeakerFallback() {
        val preference = RoutePreference()

        preference.latchSpeakerAfterTimeout()
        val cleared = preference.clearTimeoutLatchOnHeadsetConnected(null)

        assertFalse(cleared)
        assertTrue(preference.isSpeakerForced)
    }

    @Test
    fun explicitSpeakerChoice_survivesAHeadsetConnectionEvent() {
        val preference = RoutePreference()

        preference.setForced(enable = true)
        val cleared = preference.clearTimeoutLatchOnHeadsetConnected(HeadsetKind.BLUETOOTH)

        assertFalse(cleared)
        assertTrue(preference.isSpeakerForced)
    }
}
