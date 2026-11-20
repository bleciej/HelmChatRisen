package dev.communicator.helmchatrisen.audio

import android.bluetooth.BluetoothProfile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the best-effort Bluetooth headset lookup: unsupported profiles and permission races must
 * degrade to "no headset" instead of crashing the call, while a real HFP/A2DP link is still found.
 */
class BluetoothHeadsetDetectionTest {

    @Test
    fun unsupportedHeadsetProfile_isTreatedAsNoHeadset() {
        val isConnected = hasConnectedBluetoothHeadset { profile ->
            when (profile) {
                BluetoothProfile.HEADSET -> throw IllegalArgumentException("Profile not supported: $profile")
                BluetoothProfile.A2DP -> listOf<String>("music headset")
                else -> emptyList<String>()
            }
        }

        assertFalse(isConnected)
    }

    @Test
    fun a2dpOnlyConnection_isNotTreatedAsIntercomHeadset() {
        val isConnected = hasConnectedBluetoothHeadset { profile ->
            if (profile == BluetoothProfile.A2DP) {
                listOf<String>("music headset")
            } else {
                emptyList<String>()
            }
        }

        assertFalse(isConnected)
    }

    @Test
    fun connectedHeadsetProfile_isDetected() {
        val isConnected = hasConnectedBluetoothHeadset { profile ->
            if (profile == BluetoothProfile.HEADSET) listOf<String>("helmet intercom") else emptyList<String>()
        }

        assertTrue(isConnected)
    }

    @Test
    fun unsupportedProfiles_doNotCrashWhenNoHeadsetIsConnected() {
        val isConnected = hasConnectedBluetoothHeadset { profile ->
            throw IllegalArgumentException("Profile not supported: $profile")
        }

        assertFalse(isConnected)
    }

    @Test
    fun revokedPermission_isTreatedAsNoHeadset() {
        val isConnected = hasConnectedBluetoothHeadset { profile ->
            throw SecurityException("BLUETOOTH_CONNECT denied for profile $profile")
        }

        assertFalse(isConnected)
    }

    @Test
    fun emptyAndNullLookupResults_areTreatedAsNoHeadset() {
        val isConnected = hasConnectedBluetoothHeadset { null }

        assertFalse(isConnected)
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
