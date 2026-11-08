package com.example.helmchatreborn.ui.call

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.helmchatreborn.jitsi.JitsiCallManager
import org.jitsi.meet.sdk.JitsiMeetActivityDelegate
import org.jitsi.meet.sdk.JitsiMeetView

/**
 * Hosts the embedded [JitsiMeetView] used for the actual audio stream.
 *
 * The view is intentionally placed behind the custom Compose call UI, which is rendered as an
 * opaque overlay by [CallScreen]. Only the audio engine is taken from Jitsi; all controls,
 * status indicators and accessibility affordances come from the motorcycle-optimized UI.
 */
@Composable
fun JitsiConferenceView(
    roomName: String,
    serverUrl: String,
    displayName: String?,
    isMuted: Boolean,
    modifier: Modifier = Modifier,
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
                // The Jitsi UI is hidden behind the custom overlay; never let it receive
                // touches or accessibility focus.
                view.isFocusable = false
                view.importantForAccessibility =
                    View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                view.setOnTouchListener { _, _ -> true }
                callManager.join(
                    view = view,
                    roomName = roomName,
                    serverUrl = serverUrl,
                    displayName = displayName,
                    isMuted = isMuted,
                )
            }
        },
        onRelease = { view ->
            callManager.onViewReleased()
            view.abort()
            view.dispose()
        },
    )
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
