package dev.communicator.helmchatrisen.audio

import android.bluetooth.BluetoothProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the best-effort Bluetooth headset lookup: an unsupported profile or a permission race is
 * reported as unavailable instead of "no headset", so a still-connected helmet is not hidden from
 * the router. Successful lookups still distinguish a connected HFP device from an empty result.
 */
class BluetoothHeadsetDetectionTest {

    @Test
    fun unsupportedHeadsetProfile_isReportedAsUnavailable() {
        val isConnected = queryConnectedBluetoothHeadset { profile ->
            when (profile) {
                BluetoothProfile.HEADSET -> throw IllegalArgumentException("Profile not supported: $profile")
                BluetoothProfile.A2DP -> listOf<String>("music headset")
                else -> emptyList<String>()
            }
        }

        assertNull(isConnected)
    }

    @Test
    fun a2dpOnlyConnection_isNotTreatedAsIntercomHeadset() {
        val isConnected = queryConnectedBluetoothHeadset { profile ->
            if (profile == BluetoothProfile.A2DP) {
                listOf<String>("music headset")
            } else {
                emptyList<String>()
            }
        }

        assertEquals(false, isConnected)
    }

    @Test
    fun connectedHeadsetProfile_isDetected() {
        val isConnected = queryConnectedBluetoothHeadset { profile ->
            if (profile == BluetoothProfile.HEADSET) listOf<String>("helmet intercom") else emptyList<String>()
        }

        assertEquals(true, isConnected)
    }

    @Test
    fun unsupportedProfiles_doNotCrashWhenNoHeadsetIsConnected() {
        val isConnected = queryConnectedBluetoothHeadset { profile ->
            throw IllegalArgumentException("Profile not supported: $profile")
        }

        assertNull(isConnected)
    }

    @Test
    fun revokedPermission_isReportedAsUnavailable() {
        val isConnected = queryConnectedBluetoothHeadset { profile ->
            throw SecurityException("BLUETOOTH_CONNECT denied for profile $profile")
        }

        assertNull(isConnected)
    }

    @Test
    fun emptyLookupResult_isTreatedAsNoHeadset() {
        val isConnected = queryConnectedBluetoothHeadset { null }

        assertEquals(false, isConnected)
    }

    @Test
    fun wiredHeadsetPlugState_isTranslatedImmediately() {
        assertTrue(wiredHeadsetConnectionState(state = 1) == true)
        assertTrue(wiredHeadsetConnectionState(state = 0) == false)
        assertTrue(wiredHeadsetConnectionState(state = -1) == null)
    }

    @Test
    fun bluetoothProfileState_onlyConnectedEnablesTheButton() {
        assertTrue(isBluetoothHeadsetConnected(BluetoothProfile.STATE_CONNECTED))
        assertFalse(isBluetoothHeadsetConnected(BluetoothProfile.STATE_DISCONNECTED))
        assertFalse(isBluetoothHeadsetConnected(BluetoothProfile.STATE_CONNECTING))
    }
}
