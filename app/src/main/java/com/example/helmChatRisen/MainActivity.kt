package com.example.helmChatRisen

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.helmChatRisen.data.AppLanguage
import com.example.helmChatRisen.jitsi.CallState
import com.example.helmChatRisen.jitsi.JitsiCallManager
import com.example.helmChatRisen.service.AudioCallService
import com.example.helmChatRisen.ui.HelmChatApp
import com.example.helmChatRisen.ui.theme.HelmChatRisenTheme
import com.facebook.react.modules.core.PermissionListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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
 *
 * The activity also owns Picture-in-Picture. PiP is app-driven (Jitsi's own PiP module is
 * disabled) so the compact window shows the motorcycle-optimized call controls instead of the
 * hidden Jitsi UI.
 */
class MainActivity : FragmentActivity(), JitsiMeetActivityInterface {

    private val callManager by lazy { JitsiCallManager.getInstance() }

    /** Exposed to Compose so [HelmChatApp] can render the compact PiP layout. */
    private val pipState = MutableStateFlow(false)

    @Volatile
    private var isCallScreenVisible = false

    /**
     * Applies the user-selected UI language on Android 12L and lower. Android 13+ handles the
     * per-app locale through the system and leaves the base context untouched.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        callManager.configureDefaultOptions()
        enableEdgeToEdge()
        setContent {
            val isInPictureInPicture by pipState.collectAsStateWithLifecycle()
            HelmChatRisenTheme {
                HelmChatApp(
                    isInPictureInPicture = isInPictureInPicture,
                    onCallScreenVisibilityChanged = { visible ->
                        isCallScreenVisible = visible
                        updatePipParams()
                    },
                    onEnterPictureInPicture = { enterPipIfPossible() },
                )
            }
        }
        observeCallForPip()
    }

    private fun observeCallForPip() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    callManager.callState.collect { state ->
                        if (state is CallState.Idle && pipState.value) {
                            // The call ended while minimized: close the PiP window.
                            finish()
                            return@collect
                        }
                        updatePipParams()
                    }
                }
                launch {
                    callManager.isMuted.collect {
                        if (pipState.value) updatePipParams()
                    }
                }
            }
        }
    }

    private fun canEnterPip(): Boolean {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return false
        if (!isCallScreenVisible) return false
        return callManager.callState.value.let {
            it is CallState.Connected || it is CallState.Reconnecting
        }
    }

    private fun enterPipIfPossible() {
        if (!canEnterPip()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val params = buildPipParams(autoEnter = true)
                setPictureInPictureParams(params)
                enterPictureInPictureMode(params)
            } else {
                enterPictureInPictureMode()
            }
        } catch (e: IllegalStateException) {
            // The system refused PiP (e.g. another PiP window is active). The call keeps
            // running in the background through the foreground service.
        }
    }

    private fun updatePipParams() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            setPictureInPictureParams(buildPipParams(autoEnter = canEnterPip()))
        } catch (e: IllegalStateException) {
            // Params can only be set while the activity is attached.
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildPipParams(autoEnter: Boolean): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(1, 1))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Enter PiP automatically when the rider swipes home during a call.
            builder.setAutoEnterEnabled(autoEnter)
            builder.setSeamlessResizeEnabled(false)
        }

        val muted = callManager.isMuted.value
        val muteAction = RemoteAction(
            Icon.createWithResource(
                this,
                if (muted) android.R.drawable.ic_lock_silent_mode_off
                else android.R.drawable.ic_lock_silent_mode,
            ),
            getString(if (muted) R.string.call_unmute else R.string.call_mute),
            getString(if (muted) R.string.call_unmute else R.string.call_mute),
            PendingIntent.getService(
                this,
                1,
                Intent(this, AudioCallService::class.java)
                    .setAction(AudioCallService.ACTION_TOGGLE_MUTE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        val endAction = RemoteAction(
            Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
            getString(R.string.call_leave),
            getString(R.string.call_leave),
            PendingIntent.getService(
                this,
                2,
                Intent(this, AudioCallService::class.java)
                    .setAction(AudioCallService.ACTION_END_CALL),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        builder.setActions(listOf(muteAction, endAction))

        return builder.build()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Fallback for Android 7–11, where automatic PiP entry is not available.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            enterPipIfPossible()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipState.value = isInPictureInPictureMode
        updatePipParams()
    }

    @Deprecated("Deprecated in Android 8.0, kept for API 24–25")
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        pipState.value = isInPictureInPictureMode
        updatePipParams()
    }

    override fun onResume() {
        super.onResume()
        JitsiMeetActivityDelegate.onHostResume(this)
        updatePipParams()
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
