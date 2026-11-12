package com.example.helmChatRisen.ui.call

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.helmChatRisen.jitsi.JitsiCallManager
import org.jitsi.meet.sdk.JitsiMeetActivityDelegate
import org.jitsi.meet.sdk.JitsiMeetView

/**
 * Hosts the embedded [JitsiMeetView] used for the actual audio stream.
 *
 * The view is normally placed behind the custom Compose call UI, which is rendered as an
 * opaque overlay by [CallScreen]. Only the audio engine is taken from Jitsi; all controls,
 * status indicators and accessibility affordances come from the motorcycle-optimized UI.
 *
 * When [interactive] is true (meet.jit.si host setup) the Jitsi UI stays visible and receives
 * touches so the rider can complete Jitsi's own moderator sign-in flow.
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

    if (activity == null) {
        return
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            val hostActivity = viewContext.findActivity() ?: activity
            JitsiMeetView(hostActivity).also { view ->
                // The React instance is created lazily with the first JitsiMeetView. Tell the
                // delegate the host is resumed so React attaches to the Activity even though
                // the view was not present during Activity.onResume().
                JitsiMeetActivityDelegate.onHostResume(hostActivity)
                applyInteractivity(view, interactive)
                callManager.join(
                    view = view,
                    roomName = roomName,
                    serverUrl = serverUrl,
                    displayName = displayName,
                    isMuted = isMuted,
                    token = token,
                )
            }
        },
        update = { view -> applyInteractivity(view, interactive) },
        onRelease = { view ->
            callManager.onViewReleased()
            view.abort()
            view.dispose()
        },
    )
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
