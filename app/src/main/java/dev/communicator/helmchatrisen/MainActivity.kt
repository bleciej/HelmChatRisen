package dev.communicator.helmchatrisen

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.communicator.helmchatrisen.data.AppLanguage
import dev.communicator.helmchatrisen.jitsi.CallState
import dev.communicator.helmchatrisen.jitsi.JitsiCallManager
import dev.communicator.helmchatrisen.service.AudioCallService
import dev.communicator.helmchatrisen.ui.HelmChatApp
import dev.communicator.helmchatrisen.ui.theme.HelmChatRisenTheme
import com.facebook.react.modules.core.PermissionListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.jitsi.meet.sdk.JitsiMeetActivityDelegate
import org.jitsi.meet.sdk.JitsiMeetActivityInterface
import timber.log.Timber

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
     *
     * @param newBase the base context to wrap with the selected locale.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrapContext(newBase))
    }

    /**
     * Initializes logging, the default Jitsi options and the Compose content, then starts
     * observing the call for Picture-in-Picture updates.
     *
     * @param savedInstanceState the saved instance state, or `null` on a fresh launch.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        callManager.configureDefaultOptions()
        // Always-dark UI: keep light system bar icons even when the system is in light mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
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

    /** Closes the PiP window when the call ends and refreshes the PiP params while it is open. */
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

    /**
     * Reports whether the activity may currently enter Picture-in-Picture.
     *
     * @return `true` when the device supports PiP, the call screen is visible and a call is
     *   connected or reconnecting.
     */
    private fun canEnterPip(): Boolean {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return false
        if (!isCallScreenVisible) return false
        return callManager.callState.value.let {
            it is CallState.Connected || it is CallState.Reconnecting
        }
    }

    /** Enters Picture-in-Picture when [canEnterPip] allows it, ignoring a refusal by the system. */
    private fun enterPipIfPossible() {
        if (!canEnterPip()) return
        try {
            val params = buildPipParams(autoEnter = true)
            setPictureInPictureParams(params)
            enterPictureInPictureMode(params)
        } catch (_: IllegalStateException) {
            // The system refused PiP (e.g. another PiP window is active). The call keeps
            // running in the background through the foreground service.
        }
    }

    /** Refreshes the PiP params so the window actions reflect the current mute and call state. */
    private fun updatePipParams() {
        try {
            setPictureInPictureParams(buildPipParams(autoEnter = canEnterPip()))
        } catch (_: IllegalStateException) {
            // Params can only be set while the activity is attached.
        }
    }

    /**
     * Builds the PiP params with the compact call actions.
     *
     * @param autoEnter whether the system may enter PiP automatically on swipe-home.
     * @return the params applied through `setPictureInPictureParams`.
     */
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

    /** Falls back to manual PiP entry on Android versions without automatic entry. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Fallback for Android 8–11 (API 26–30), where automatic PiP entry is not available.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            enterPipIfPossible()
        }
    }

    /**
     * Tracks PiP mode changes so Compose can render the compact layout.
     *
     * @param isInPictureInPictureMode whether the activity is now in PiP.
     * @param newConfig the configuration of the PiP window.
     */
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipState.value = isInPictureInPictureMode
        updatePipParams()
    }

    /** Forwards the resume to the Jitsi delegate and refreshes the PiP params. */
    override fun onResume() {
        super.onResume()
        JitsiMeetActivityDelegate.onHostResume(this)
        updatePipParams()
    }

    /** Forwards the pause to the Jitsi delegate. */
    override fun onStop() {
        JitsiMeetActivityDelegate.onHostPause(this)
        super.onStop()
    }

    /** Hangs up and stops the call service on a real finish, then destroys the Jitsi host. */
    override fun onDestroy() {
        if (isFinishing) {
            JitsiCallManager.getInstance().hangUp()
            stopService(Intent(this, AudioCallService::class.java))
        }
        JitsiMeetActivityDelegate.onHostDestroy(this)
        super.onDestroy()
    }

    /**
     * Forwards a new intent to the Jitsi delegate.
     *
     * @param intent the new intent delivered to the activity.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        JitsiMeetActivityDelegate.onNewIntent(intent)
    }

    /**
     * Forwards an activity result to the Jitsi delegate.
     *
     * @param requestCode the request code the result belongs to.
     * @param resultCode the result code returned by the child activity.
     * @param data the result data, or `null` when the child returned none.
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        JitsiMeetActivityDelegate.onActivityResult(this, requestCode, resultCode, data)
        super.onActivityResult(requestCode, resultCode, data)
    }

    /**
     * Forwards a permission result to the Jitsi delegate.
     *
     * @param requestCode the request code the result belongs to.
     * @param permissions the requested permissions.
     * @param grantResults the grant result for each requested permission.
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        JitsiMeetActivityDelegate.onRequestPermissionsResult(requestCode, permissions, grantResults)
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    /**
     * Forwards a permission request from the Jitsi UI bundle to the delegate.
     *
     * @param permissions the permissions to request.
     * @param requestCode the request code to report the result with.
     * @param listener the React Native listener notified of the result, or `null` when none is used.
     */
    override fun requestPermissions(
        permissions: Array<String>,
        requestCode: Int,
        listener: PermissionListener?,
    ) {
        JitsiMeetActivityDelegate.requestPermissions(this, permissions, requestCode, listener)
    }
}
