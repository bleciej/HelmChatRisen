package com.example.helmchatreborn

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.example.helmchatreborn.jitsi.JitsiCallManager
import com.example.helmchatreborn.service.AudioCallService
import com.example.helmchatreborn.ui.HelmChatApp
import com.example.helmchatreborn.ui.theme.HelmChatRebornTheme
import com.facebook.react.modules.core.PermissionListener
import org.jitsi.meet.sdk.JitsiMeetActivityDelegate
import org.jitsi.meet.sdk.JitsiMeetActivityInterface

/**
 * Single-activity host for the Compose UI and the embedded Jitsi view.
 *
 * The Jitsi SDK requires the enclosing Activity to implement [JitsiMeetActivityInterface] and
 * forwards React Native / WebRTC lifecycle callbacks through [JitsiMeetActivityDelegate].
 *
 * It must also be a `FragmentActivity`: react-native-screens (used by the Jitsi UI bundle)
 * resolves its `FragmentManager` from the host activity and throws
 * "In order to use RNScreens components your app's activity need to extend ReactActivity"
 * otherwise.
 */
class MainActivity : FragmentActivity(), JitsiMeetActivityInterface {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        JitsiCallManager.getInstance().configureDefaultOptions()
        enableEdgeToEdge()
        setContent {
            HelmChatRebornTheme {
                HelmChatApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        JitsiMeetActivityDelegate.onHostResume(this)
    }

    override fun onStop() {
        JitsiMeetActivityDelegate.onHostPause(this)
        super.onStop()
    }

    override fun onDestroy() {
        if (isFinishing) {
            JitsiCallManager.getInstance().hangUp()
            stopService(Intent(this, AudioCallService::class.java))
        }
        JitsiMeetActivityDelegate.onHostDestroy(this)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        JitsiMeetActivityDelegate.onNewIntent(intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        JitsiMeetActivityDelegate.onActivityResult(this, requestCode, resultCode, data)
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        JitsiMeetActivityDelegate.onRequestPermissionsResult(requestCode, permissions, grantResults)
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun requestPermissions(
        permissions: Array<String>,
        requestCode: Int,
        listener: PermissionListener?,
    ) {
        JitsiMeetActivityDelegate.requestPermissions(this, permissions, requestCode, listener)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        sendBroadcast(
            Intent(ACTION_CONFIGURATION_CHANGED).putExtra(EXTRA_NEW_CONFIG, newConfig)
        )
    }

    private companion object {
        /** Mirrors the broadcast sent by [org.jitsi.meet.sdk.JitsiMeetActivity]. */
        const val ACTION_CONFIGURATION_CHANGED = "onConfigurationChanged"
        const val EXTRA_NEW_CONFIG = "newConfig"
    }
}
