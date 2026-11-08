package com.example.helmchatreborn.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SportsMotorsports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.helmchatreborn.R
import com.example.helmchatreborn.ui.theme.HelmChatRebornTheme
import com.example.helmchatreborn.ui.theme.MotorcyclePrimary
import com.example.helmchatreborn.ui.theme.MotorcycleSecondary

/**
 * Motorcycle-optimized join screen.
 *
 * All controls are intentionally large and high-contrast so they can be operated
 * with riding gloves. The form is stateless; state is provided by [HomeViewModel].
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onRoomNameChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onUseDefaultServerChange: (Boolean) -> Unit,
    onCustomServerUrlChange: (String) -> Unit,
    onJoinCall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val horizontalPadding = if (maxWidth >= 600.dp) 48.dp else 20.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = horizontalPadding, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 620.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HomeHeader()
                Spacer(Modifier.height(28.dp))
                JoinCard(
                    state = state,
                    onRoomNameChange = onRoomNameChange,
                    onDisplayNameChange = onDisplayNameChange,
                    onUseDefaultServerChange = onUseDefaultServerChange,
                    onCustomServerUrlChange = onCustomServerUrlChange,
                    onJoinCall = onJoinCall,
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.home_join_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun HomeHeader() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(88.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.SportsMotorsports,
                contentDescription = null,
                tint = MotorcyclePrimary,
                modifier = Modifier.size(72.dp),
            )
        }
        Text(
            text = "HelmChat",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.home_tagline),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        AudioOnlyBadge()
    }
}

@Composable
private fun AudioOnlyBadge() {
    Surface(
        shape = CircleShape,
        color = MotorcycleSecondary.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, MotorcycleSecondary.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 40.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = null,
                tint = MotorcycleSecondary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.home_mode_badge),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MotorcycleSecondary,
            )
        }
    }
}

@Composable
private fun JoinCard(
    state: HomeUiState,
    onRoomNameChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onUseDefaultServerChange: (Boolean) -> Unit,
    onCustomServerUrlChange: (String) -> Unit,
    onJoinCall: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            SectionLabel(
                icon = Icons.Filled.Groups,
                text = stringResource(R.string.home_room_section),
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.roomName,
                onValueChange = onRoomNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.home_room_label)) },
                placeholder = { Text(stringResource(R.string.home_room_placeholder)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                textStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.displayName,
                onValueChange = onDisplayNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.home_display_name_label)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                textStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            Spacer(Modifier.height(20.dp))

            SectionLabel(
                icon = Icons.Filled.Dns,
                text = stringResource(R.string.home_server_section),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_server_default),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = state.effectiveServerUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Switch(
                    checked = state.useDefaultServer,
                    onCheckedChange = onUseDefaultServerChange,
                )
            }

            AnimatedVisibility(visible = !state.useDefaultServer) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.customServerUrl,
                        onValueChange = onCustomServerUrlChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.home_server_custom_label)) },
                        placeholder = { Text(stringResource(R.string.home_server_custom_support)) },
                        singleLine = true,
                        isError = !state.isServerUrlValid,
                        supportingText = {
                            if (!state.isServerUrlValid) {
                                Text(stringResource(R.string.home_server_invalid))
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        textStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = onJoinCall,
                enabled = state.canJoin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MotorcyclePrimary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Call,
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.home_join),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MotorcyclePrimary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF090B0E)
@Composable
private fun HomeScreenPreview() {
    HelmChatRebornTheme {
        HomeScreen(
            state = HomeUiState(
                roomName = "niedzielna-przejezdzajka",
                displayName = "Burt",
            ),
            onRoomNameChange = {},
            onDisplayNameChange = {},
            onUseDefaultServerChange = {},
            onCustomServerUrlChange = {},
            onJoinCall = {},
        )
    }
}
