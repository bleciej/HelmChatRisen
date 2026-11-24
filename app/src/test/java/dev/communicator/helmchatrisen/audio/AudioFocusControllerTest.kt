package dev.communicator.helmchatrisen.audio

import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the one-call lifecycle of the intercom's ducking audio focus without an Android device.
 */
class AudioFocusControllerTest {

    /** Captures the focus listener and counts platform calls for the tests. */
    private class FakeOperations(
        private val result: Int = AudioManager.AUDIOFOCUS_REQUEST_GRANTED,
    ) : AudioFocusOperations {

        /** Number of focus requests made by the controller. */
        var requestCount = 0
            private set

        /** Number of abandon calls made by the controller. */
        var abandonCount = 0
            private set

        /** Listener supplied to the latest request, or `null` before a request. */
        var listener: ((Int) -> Unit)? = null
            private set

        /**
         * Records a request and stores its focus-change listener.
         *
         * @param listener called with `AudioManager.AUDIOFOCUS_*` change constants.
         * @return the preconfigured result code.
         */
        override fun request(listener: (Int) -> Unit): Int {
            requestCount += 1
            this.listener = listener
            return result
        }

        /** Records an abandon call. */
        override fun abandon() {
            abandonCount += 1
        }
    }

    /**
     * A granted request is held once and released once, even when start and teardown repeat.
     */
    @Test
    fun grantedFocus_isRequestedAndAbandonedOnce() {
        val operations = FakeOperations()
        val controller = AudioFocusController(operations)

        controller.request()
        controller.request()
        assertTrue(controller.isHeld)
        assertEquals(1, operations.requestCount)

        controller.abandon()
        controller.abandon()
        assertFalse(controller.isHeld)
        assertEquals(1, operations.abandonCount)
    }

    /**
     * A denied request must not block the call or be abandoned as if focus had been granted.
     */
    @Test
    fun deniedFocus_isNotHeldOrAbandoned() {
        val operations = FakeOperations(result = AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        val controller = AudioFocusController(operations)

        controller.request()
        controller.abandon()

        assertFalse(controller.isHeld)
        assertEquals(1, operations.requestCount)
        assertEquals(0, operations.abandonCount)
    }

    /**
     * A delayed grant is still owned by this call and must be released at teardown.
     */
    @Test
    fun delayedFocus_isHeldUntilAbandoned() {
        val operations = FakeOperations(result = AudioManager.AUDIOFOCUS_REQUEST_DELAYED)
        val controller = AudioFocusController(operations)

        controller.request()

        assertTrue(controller.isHeld)
        controller.abandon()
        assertEquals(1, operations.abandonCount)
    }

    /**
     * A permanent loss means another app owns focus, so teardown must not abandon it again.
     */
    @Test
    fun permanentFocusLoss_releasesTheHold() {
        val operations = FakeOperations()
        val controller = AudioFocusController(operations)
        controller.request()

        operations.listener?.invoke(AudioManager.AUDIOFOCUS_LOSS)

        assertFalse(controller.isHeld)
        controller.abandon()
        assertEquals(0, operations.abandonCount)
    }

    /**
     * Transient losses and a later gain keep the call's focus ownership intact.
     */
    @Test
    fun transientLossAndGain_keepTheHold() {
        val operations = FakeOperations()
        val controller = AudioFocusController(operations)
        controller.request()
        val listener = requireNotNull(operations.listener)

        listener(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        listener(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
        listener(AudioManager.AUDIOFOCUS_GAIN)

        assertTrue(controller.isHeld)
        controller.abandon()
        assertEquals(1, operations.abandonCount)
    }
}
