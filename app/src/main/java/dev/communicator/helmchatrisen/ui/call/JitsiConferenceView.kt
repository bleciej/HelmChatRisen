package dev.communicator.helmchatrisen.ui.call

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.communicator.helmchatrisen.jitsi.JitsiCallManager
import org.jitsi.meet.sdk.JitsiMeetActivityDelegate
import org.jitsi.meet.sdk.JitsiMeetView
import timber.log.Timber

/**
 * Hosts the embedded [JitsiMeetView] used for the actual audio stream.
 *
 * The view is normally placed behind the custom Compose call UI, which is rendered as an
 * opaque overlay by [CallScreen]. Only the audio engine is taken from Jitsi; all controls,
 * status indicators and accessibility affordances come from the motorcycle-optimized UI.
 *
 * When [interactive] is true (meet.jit.si host setup) the Jitsi UI stays visible and receives
 * touches so the rider can complete Jitsi's own moderator sign-in flow. The manager can request a
 * replacement view after a stalled reconnect; the key forces Compose to build a fresh native view
 * instead of reloading the same conference URL inside the existing one.
 */
@Composable
fun JitsiConferenceView(
    roomName: String,
    serverUrl: String,
    displayName: String?,
    isMuted: Boolean,
    modifier: Modifier = Modifier,
    token: String? = null,
    interactive: Boolean = false,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val callManager = remember { JitsiCallManager.getInstance() }
    val viewRecreationToken by callManager.viewRecreationToken.collectAsStateWithLifecycle()

    if (activity == null) {
        return
    }

    key(viewRecreationToken) {
        AndroidView(
            modifier = modifier,
            factory = { viewContext ->
                val hostActivity = viewContext.findActivity() ?: activity
                JitsiMeetView(hostActivity).also { view ->
                    // SDK 13.1.1 starts ReactHost at app startup, but the delegate still needs the
                    // resume event so a view created later by Compose attaches to the Activity.
                    JitsiMeetActivityDelegate.onHostResume(hostActivity)
                    applyInteractivity(view, interactive)
                    if (callManager.canJoinConference()) {
                        callManager.join(
                            view = view,
                            roomName = roomName,
                            serverUrl = serverUrl,
                            displayName = displayName,
                            isMuted = isMuted,
                            token = token,
                        )
                    } else {
                        Timber.w("Skipping Jitsi join while the previous call is still ending")
                    }
                }
            },
            update = { view -> applyInteractivity(view, interactive) },
            onRelease = { view ->
                callManager.onViewReleased(view)
            },
        )
    }
}

@SuppressLint("ClickableViewAccessibility")
private fun applyInteractivity(view: JitsiMeetView, interactive: Boolean) {
    if (interactive) {
        view.isFocusable = true
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        view.setOnTouchListener(null)
    } else {
        // Hidden behind the custom overlay; never let it receive touches or accessibility focus.
        // The listener intentionally swallows every gesture: the view is invisible, so there is
        // nothing to click and no accessibility action to forward.
        view.isFocusable = false
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        view.setOnTouchListener { _, _ -> true }
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
