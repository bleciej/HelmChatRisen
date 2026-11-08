package com.example.helmchatreborn.ui.call

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import com.example.helmchatreborn.R
import com.example.helmchatreborn.ui.theme.HelmChatRebornTheme
import com.example.helmchatreborn.ui.theme.LeaveRed
import com.example.helmchatreborn.ui.theme.MicMutedRed
import com.example.helmchatreborn.ui.theme.MicUnmutedGreen
import com.example.helmchatreborn.ui.theme.MotorcycleSecondary
import com.example.helmchatreborn.ui.theme.MotorcycleTertiary
import com.example.helmchatreborn.ui.theme.ScoBlue
import com.example.helmchatreborn.ui.theme.SpeakerYellow

/**
 * UI state of the in-call screen.
 */
data class CallScreenState(
    val roomName: String,
    val serverUrl: String,
    val isConnected: Boolean = true,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val audioRouteLabel: String? = null,
    val participantCount: Int = 0,
    val errorMessage: String? = null,
)

/**
 * Full-screen, high-contrast call UI with extra-large controls sized for gloved hands.
 *
 * [conferenceContent] is rendered first, behind the opaque custom controls. It hosts the
 * embedded Jitsi audio engine and is never visible to the rider.
 */
@Composable
fun CallScreen(
    state: CallScreenState,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onLeaveCall: () -> Unit,
    modifier: Modifier = Modifier,
    conferenceContent: @Composable () -> Unit = {},
) {
    val haptics = LocalHapticFeedback.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val compactHeight = maxHeight < 560.dp
        val horizontalPadding = if (maxWidth >= 600.dp) 40.dp else 20.dp
        val micSize = if (compactHeight) 136.dp else 168.dp

        // The Jitsi view lives behind an opaque overlay so the rider only ever sees the
        // motorcycle-optimized controls.
        conferenceContent()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = horizontalPadding, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CallStatusPill(isConnected = state.isConnected)
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

            if (state.errorMessage != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.call_error, state.errorMessage),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            } else if (state.participantCount > 0) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.call_participants, state.participantCount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.weight(1f))

            MicButton(
                isMuted = state.isMuted,
                size = micSize,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleMute()
                },
            )
            Spacer(Modifier.height(if (compactHeight) 12.dp else 20.dp))
            Text(
                text = stringResource(
                    if (state.isMuted) R.string.call_unmute else R.string.call_mute
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.weight(1f))

            AudioRouteButton(
                isSpeakerOn = state.isSpeakerOn,
                audioRouteLabel = state.audioRouteLabel,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleSpeaker()
                },
            )
            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLeaveCall()
                },
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
    }
}

@Composable
private fun CallStatusPill(isConnected: Boolean) {
    val statusColor = if (isConnected) MotorcycleSecondary else MotorcycleTertiary
    val label = stringResource(
        if (isConnected) R.string.call_connected else R.string.call_connecting
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

@Composable
private fun MicButton(
    isMuted: Boolean,
    size: Dp,
    onClick: () -> Unit,
) {
    val background = if (isMuted) MicMutedRed else MicUnmutedGreen
    val contentColor = if (isMuted) Color.White else Color.Black
    val contentDescription = stringResource(
        if (isMuted) R.string.call_unmute else R.string.call_mute
    )

    Surface(
        onClick = onClick,
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

@Composable
private fun AudioRouteButton(
    isSpeakerOn: Boolean,
    audioRouteLabel: String?,
    onClick: () -> Unit,
) {
    val label = audioRouteLabel ?: stringResource(
        if (isSpeakerOn) R.string.call_speaker_on else R.string.call_speaker_off
    )
    val iconTint = if (isSpeakerOn) SpeakerYellow else ScoBlue

    OutlinedButton(
        onClick = onClick,
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
    HelmChatRebornTheme {
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
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF090B0E, heightDp = 800)
@Composable
private fun CallScreenMutedPreview() {
    HelmChatRebornTheme {
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
        )
    }
}
