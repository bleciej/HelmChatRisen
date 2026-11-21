package dev.communicator.helmchatrisen.ui.call

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.communicator.helmchatrisen.R
import dev.communicator.helmchatrisen.ui.theme.HelmChatRisenTheme
import dev.communicator.helmchatrisen.ui.theme.LeaveRed
import dev.communicator.helmchatrisen.ui.theme.MicMutedRed
import dev.communicator.helmchatrisen.ui.theme.MicUnmutedGreen
import dev.communicator.helmchatrisen.ui.theme.MotorcycleSecondary
import dev.communicator.helmchatrisen.ui.theme.MotorcycleTertiary
import dev.communicator.helmchatrisen.ui.theme.ScoBlue
import dev.communicator.helmchatrisen.ui.theme.SpeakerYellow

/**
 * UI state of the in-call screen.
 *
 * @param roomName the Jitsi room shown in the header.
 * @param serverUrl the Jitsi server shown in the header.
 * @param isConnected whether the intercom is live.
 * @param isReconnecting whether Jitsi is restoring a dropped conference.
 * @param isOffline whether the device currently has no default network.
 * @param isMuted whether the local microphone is muted.
 * @param isSpeakerOn whether the built-in speaker is the active audio route.
 * @param canSwitchAudioRoute whether a headset is connected so the route can be switched.
 * @param audioRouteLabel the human-readable audio route shown on the route button.
 * @param participantCount the number of riders in the room, including the local rider.
 * @param errorMessage an error to show below the header, or `null` when there is none.
 * @param isEnding whether the app is waiting for Jitsi to finish a requested hang-up.
 */
data class CallScreenState(
    val roomName: String,
    val serverUrl: String,
    val isConnected: Boolean = true,
    val isReconnecting: Boolean = false,
    val isOffline: Boolean = false,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val canSwitchAudioRoute: Boolean = true,
    val audioRouteLabel: String? = null,
    val participantCount: Int = 0,
    val errorMessage: String? = null,
    val isEnding: Boolean = false,
)

/**
 * Full-screen, high-contrast call UI with extra-large controls sized for gloved hands.
 *
 * [conferenceContent] is rendered first, behind the opaque custom controls. It hosts the
 * embedded Jitsi audio engine and is never visible to the rider, except in host-setup mode
 * where Jitsi's own sign-in UI must stay interactive.
 *
 * @param state the call status, mute state and audio route displayed to the rider.
 * @param onToggleMute invoked when the rider taps the microphone control.
 * @param onToggleSpeaker invoked when the rider switches between speaker and helmet audio.
 * @param onLeaveCall invoked when the rider ends the call.
 * @param onInvite invoked when the rider shares the room invitation.
 * @param modifier the modifier applied to the call root.
 * @param isInPictureInPicture whether the compact Picture-in-Picture layout is active.
 * @param onEnterPictureInPicture invoked when the rider minimizes the call.
 * @param hostSetup whether Jitsi's own sign-in UI must stay interactive.
 * @param onHostSetupDone invoked when the rider finishes the host sign-in step.
 * @param conferenceContent the embedded Jitsi view rendered behind the custom overlay.
 */
@Composable
fun CallScreen(
    state: CallScreenState,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onLeaveCall: () -> Unit,
    onInvite: () -> Unit,
    modifier: Modifier = Modifier,
    isInPictureInPicture: Boolean = false,
    onEnterPictureInPicture: () -> Unit = {},
    hostSetup: Boolean = false,
    onHostSetupDone: () -> Unit = {},
    conferenceContent: @Composable () -> Unit = {},
) {
    val haptics = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        conferenceContent()

        when {
            isInPictureInPicture -> PipCallContent(
                state = state,
                onToggleMute = onToggleMute,
                onLeaveCall = onLeaveCall,
            )

            hostSetup -> HostSetupContent(
                state = state,
                onHostSetupDone = onHostSetupDone,
                onLeaveCall = onLeaveCall,
            )

            else -> CallContent(
                state = state,
                onToggleMute = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleMute()
                },
                onToggleSpeaker = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleSpeaker()
                },
                onLeaveCall = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLeaveCall()
                },
                onInvite = onInvite,
                onEnterPictureInPicture = onEnterPictureInPicture,
            )
        }
    }
}

/**
 * Portrait call layout: status header, Picture-in-Picture and invite actions in opposite top
 * corners, large microphone control, audio-route selector and the prominent end-call button. Short
 * windows use reduced control sizes while keeping every target large enough for gloved operation.
 *
 * @param state the call status, mute state and audio route displayed to the rider.
 * @param onToggleMute invoked when the rider taps the microphone control.
 * @param onToggleSpeaker invoked when the rider switches between speaker and helmet audio.
 * @param onLeaveCall invoked when the rider ends the call.
 * @param onInvite invoked when the rider shares the room invitation.
 * @param onEnterPictureInPicture invoked when the rider minimizes the call.
 */
@Composable
private fun CallContent(
    state: CallScreenState,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onLeaveCall: () -> Unit,
    onInvite: () -> Unit,
    onEnterPictureInPicture: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val compactHeight = maxHeight < 560.dp
        val horizontalPadding = if (maxWidth >= 600.dp) 40.dp else 20.dp
        val micSize = if (compactHeight) 136.dp else 168.dp
        val scrollState = rememberScrollState()

        /**
         * Keeps the controls centered on tall windows and prevents unbounded-height weights when
         * a short landscape window has to scroll.
         */
        @Composable
        fun ColumnScope.CallSpacer() {
            if (compactHeight) {
                Spacer(Modifier.height(16.dp))
            } else {
                Spacer(Modifier.weight(1f))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (compactHeight) Modifier.verticalScroll(scrollState) else Modifier)
                .padding(horizontal = horizontalPadding, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CallStatusPill(
                isConnected = state.isConnected,
                isReconnecting = state.isReconnecting,
                isOffline = state.isOffline,
                isEnding = state.isEnding,
            )
            Spacer(Modifier.height(if (compactHeight) 12.dp else 24.dp))

            Text(
                text = state.roomName,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = state.serverUrl,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            when {
                state.errorMessage != null -> {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.call_error, state.errorMessage),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
                state.isReconnecting || state.isOffline -> {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.call_reconnecting_hint),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MotorcycleTertiary,
                        textAlign = TextAlign.Center,
                    )
                }
                state.participantCount > 0 -> {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.call_participants, state.participantCount),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            CallSpacer()

            MicButton(
                isMuted = state.isMuted,
                size = micSize,
                enabled = !state.isEnding,
                onClick = onToggleMute,
            )
            Spacer(Modifier.height(if (compactHeight) 12.dp else 20.dp))
            Text(
                text = stringResource(
                    if (state.isMuted) R.string.call_unmute else R.string.call_mute,
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            CallSpacer()

            AudioRouteButton(
                isSpeakerOn = state.isSpeakerOn,
                audioRouteLabel = state.audioRouteLabel,
                enabled = !state.isEnding && state.canSwitchAudioRoute,
                onClick = onToggleSpeaker,
            )
            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onLeaveCall,
                enabled = !state.isEnding,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LeaveRed,
                    contentColor = Color.White,
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.CallEnd,
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.call_leave),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        IconButton(
            onClick = onEnterPictureInPicture,
            enabled = !state.isEnding,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 8.dp, start = 8.dp)
                .size(64.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.PictureInPictureAlt,
                contentDescription = stringResource(R.string.call_pip_enter),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(30.dp),
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 8.dp),
        ) {
            IconButton(
                onClick = onInvite,
                enabled = !state.isEnding,
                modifier = Modifier.size(64.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = stringResource(R.string.home_invite),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
    }
}

/**
 * Compact layout used inside the Picture-in-Picture window. The system may shrink the window
 * to a thumbnail, so only the room name, status and the two essential actions are shown. Controls
 * are disabled while the call is ending to prevent re-entrant teardown from the compact surface.
 *
 * @param state the call status, mute state and audio route displayed to the rider.
 * @param onToggleMute invoked when the rider taps the microphone control.
 * @param onLeaveCall invoked when the rider ends the call.
 */
@Composable
private fun PipCallContent(
    state: CallScreenState,
    onToggleMute: () -> Unit,
    onLeaveCall: () -> Unit,
) {
    val statusColor = when {
        state.isEnding -> LeaveRed
        state.isReconnecting || state.isOffline -> MotorcycleTertiary
        state.isConnected -> MotorcycleSecondary
        else -> MotorcycleTertiary
    }
    val statusLabelRes = when {
        state.isEnding -> R.string.call_ending
        state.isReconnecting || state.isOffline -> R.string.call_reconnecting
        state.isConnected -> R.string.call_connected
        else -> R.string.call_connecting
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(statusColor, CircleShape),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = state.roomName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(statusLabelRes),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = statusColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(
                onClick = onToggleMute,
                enabled = !state.isEnding,
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = if (state.isMuted) MicMutedRed else MicUnmutedGreen,
                contentColor = if (state.isMuted) Color.White else Color.Black,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (state.isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                        contentDescription = stringResource(
                            if (state.isEnding) {
                                R.string.call_ending
                            } else if (state.isMuted) {
                                R.string.call_unmute
                            } else {
                                R.string.call_mute
                            },
                        ),
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            Surface(
                onClick = onLeaveCall,
                enabled = !state.isEnding,
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = LeaveRed,
                contentColor = Color.White,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.CallEnd,
                        contentDescription = stringResource(
                            if (state.isEnding) R.string.call_ending else R.string.call_leave,
                        ),
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        }
    }
}

/**
 * Host-setup overlay for meet.jit.si. The Jitsi UI stays visible in the middle so the rider can
 * complete Jitsi's own moderator sign-in. The header and footer are the only opaque parts, and all
 * controls are disabled while the call is ending.
 *
 * @param state the call status and leave state displayed to the rider.
 * @param onHostSetupDone invoked when the rider finishes the host sign-in step.
 * @param onLeaveCall invoked when the rider ends the call.
 */
@Composable
private fun HostSetupContent(
    state: CallScreenState,
    onHostSetupDone: () -> Unit,
    onLeaveCall: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            if (state.isEnding) R.string.call_ending else R.string.call_host_setup_title,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.call_host_setup_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = onHostSetupDone,
                    enabled = !state.isEnding,
                    modifier = Modifier.heightIn(min = 56.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.call_host_setup_done),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // Transparent middle: the embedded Jitsi sign-in UI stays touchable.
        Spacer(Modifier.weight(1f))

        Surface(
            color = MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
        ) {
            OutlinedButton(
                onClick = onLeaveCall,
                enabled = !state.isEnding,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .heightIn(min = 56.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, LeaveRed),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = LeaveRed),
            ) {
                Icon(
                    imageVector = Icons.Filled.CallEnd,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.call_leave),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * Compact status badge shown above the room name.
 *
 * @param isConnected whether the intercom is live.
 * @param isReconnecting whether Jitsi is restoring a dropped conference.
 * @param isOffline whether the device currently has no default network.
 * @param isEnding whether the app is waiting for Jitsi to finish a requested hang-up.
 */
@Composable
private fun CallStatusPill(
    isConnected: Boolean,
    isReconnecting: Boolean,
    isOffline: Boolean = false,
    isEnding: Boolean = false,
) {
    val statusColor = when {
        isEnding -> LeaveRed
        isReconnecting || isOffline -> MotorcycleTertiary
        isConnected -> MotorcycleSecondary
        else -> MotorcycleTertiary
    }
    val label = stringResource(
        when {
            isEnding -> R.string.call_ending
            isReconnecting || isOffline -> R.string.call_reconnecting
            isConnected -> R.string.call_connected
            else -> R.string.call_connecting
        }
    )

    Surface(
        shape = CircleShape,
        color = statusColor.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 40.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(statusColor, CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = statusColor,
            )
        }
    }
}

/**
 * Large circular microphone toggle.
 *
 * @param isMuted whether the microphone is currently muted.
 * @param size the diameter of the touch target.
 * @param enabled whether the control accepts taps while the call is being torn down.
 * @param onClick invoked when the rider taps the control.
 */
@Composable
private fun MicButton(
    isMuted: Boolean,
    size: Dp,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val background = if (isMuted) MicMutedRed else MicUnmutedGreen
    val contentColor = if (isMuted) Color.White else Color.Black
    val contentDescription = stringResource(
        if (isMuted) R.string.call_unmute else R.string.call_mute
    )

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = background,
        contentColor = contentColor,
        shadowElevation = 12.dp,
        border = BorderStroke(6.dp, background.copy(alpha = 0.28f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                contentDescription = contentDescription,
                modifier = Modifier.size(size * 0.42f),
            )
        }
    }
}

/**
 * Full-width selector showing the current helmet or speaker route.
 *
 * @param isSpeakerOn whether the built-in speaker is the active audio route.
 * @param audioRouteLabel the device-specific route name, or `null` to use the generic label.
 * @param enabled whether the control accepts taps; disabled without a headset or during teardown.
 * @param onClick invoked when the rider switches the audio route.
 */
@Composable
private fun AudioRouteButton(
    isSpeakerOn: Boolean,
    audioRouteLabel: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val label = audioRouteLabel ?: stringResource(
        if (isSpeakerOn) R.string.call_speaker_on else R.string.call_speaker_off
    )
    val iconTint = if (isSpeakerOn) SpeakerYellow else ScoBlue

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Icon(
            imageVector = if (isSpeakerOn) Icons.Filled.Speaker else Icons.Filled.Headset,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF090B0E, heightDp = 800)
@Composable
private fun CallScreenPreview() {
    HelmChatRisenTheme {
        CallScreen(
            state = CallScreenState(
                roomName = "niedzielna-przejezdzajka",
                serverUrl = "https://meet.ffmuc.net",
                isConnected = true,
                isMuted = false,
            ),
            onToggleMute = {},
            onToggleSpeaker = {},
            onLeaveCall = {},
            onInvite = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF090B0E, heightDp = 800)
@Composable
private fun CallScreenMutedPreview() {
    HelmChatRisenTheme {
        CallScreen(
            state = CallScreenState(
                roomName = "grupa-sena",
                serverUrl = "https://meet.ffmuc.net",
                isConnected = false,
                isMuted = true,
                isSpeakerOn = true,
            ),
            onToggleMute = {},
            onToggleSpeaker = {},
            onLeaveCall = {},
            onInvite = {},
        )
    }
}

/** Preview of the call layout when no headset is connected and the route control is locked to the speaker. */
@Preview(showBackground = true, backgroundColor = 0xFF090B0E, heightDp = 800)
@Composable
private fun CallScreenNoHeadsetPreview() {
    HelmChatRisenTheme {
        CallScreen(
            state = CallScreenState(
                roomName = "poranny-dojazd",
                serverUrl = "https://meet.ffmuc.net",
                isConnected = true,
                isSpeakerOn = true,
                canSwitchAudioRoute = false,
                audioRouteLabel = stringResource(R.string.call_speaker_on),
                participantCount = 2,
            ),
            onToggleMute = {},
            onToggleSpeaker = {},
            onLeaveCall = {},
            onInvite = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF090B0E, heightDp = 800)
@Composable
private fun CallScreenReconnectingPreview() {
    HelmChatRisenTheme {
        CallScreen(
            state = CallScreenState(
                roomName = "niedzielna-przejezdzajka",
                serverUrl = "https://meet.ffmuc.net",
                isConnected = false,
                isReconnecting = true,
            ),
            onToggleMute = {},
            onToggleSpeaker = {},
            onLeaveCall = {},
            onInvite = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF090B0E, heightDp = 800)
@Composable
private fun CallScreenOfflinePreview() {
    HelmChatRisenTheme {
        CallScreen(
            state = CallScreenState(
                roomName = "niedzielna-przejezdzajka",
                serverUrl = "https://meet.ffmuc.net",
                isConnected = true,
                isOffline = true,
                participantCount = 3,
            ),
            onToggleMute = {},
            onToggleSpeaker = {},
            onLeaveCall = {},
            onInvite = {},
        )
    }
}

/** Preview of the full-screen call layout while the conference is ending. */
@Preview(showBackground = true, backgroundColor = 0xFF090B0E, heightDp = 800)
@Composable
private fun CallScreenEndingPreview() {
    HelmChatRisenTheme {
        CallScreen(
            state = CallScreenState(
                roomName = "niedzielna-przejezdzajka",
                serverUrl = "https://meet.ffmuc.net",
                isConnected = false,
                isEnding = true,
            ),
            onToggleMute = {},
            onToggleSpeaker = {},
            onLeaveCall = {},
            onInvite = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF090B0E, widthDp = 200, heightDp = 200)
@Composable
private fun CallScreenPipPreview() {
    HelmChatRisenTheme {
        CallScreen(
            state = CallScreenState(
                roomName = "grupa-sena",
                serverUrl = "https://meet.ffmuc.net",
                isConnected = true,
            ),
            onToggleMute = {},
            onToggleSpeaker = {},
            onLeaveCall = {},
            onInvite = {},
            isInPictureInPicture = true,
        )
    }
}

/** Preview of the compact PiP layout while the conference is ending. */
@Preview(showBackground = true, backgroundColor = 0xFF090B0E, widthDp = 200, heightDp = 200)
@Composable
private fun CallScreenPipEndingPreview() {
    HelmChatRisenTheme {
        CallScreen(
            state = CallScreenState(
                roomName = "grupa-sena",
                serverUrl = "https://meet.ffmuc.net",
                isConnected = false,
                isEnding = true,
            ),
            onToggleMute = {},
            onToggleSpeaker = {},
            onLeaveCall = {},
            onInvite = {},
            isInPictureInPicture = true,
        )
    }
}

/** Preview of the host-setup overlay while the conference is ending. */
@Preview(showBackground = true, backgroundColor = 0xFF090B0E, heightDp = 800)
@Composable
private fun CallScreenHostSetupEndingPreview() {
    HelmChatRisenTheme {
        CallScreen(
            state = CallScreenState(
                roomName = "host-setup-room",
                serverUrl = "https://meet.jit.si",
                isConnected = false,
                isEnding = true,
            ),
            onToggleMute = {},
            onToggleSpeaker = {},
            onLeaveCall = {},
            onInvite = {},
            hostSetup = true,
        )
    }
}
