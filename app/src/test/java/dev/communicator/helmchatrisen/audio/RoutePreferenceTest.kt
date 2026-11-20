package dev.communicator.helmchatrisen.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the stateful rider-speaker choice used by the router: a Bluetooth route timeout latches
 * the speaker, a late SCO event cannot restart retries, and only a real disconnect or an explicit
 * rider toggle clears the latch.
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
}
