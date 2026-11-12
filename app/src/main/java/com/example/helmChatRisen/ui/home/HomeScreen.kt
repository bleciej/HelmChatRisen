package com.example.helmChatRisen.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.helmChatRisen.R
import com.example.helmChatRisen.data.AppLanguage
import com.example.helmChatRisen.data.RecentRoom
import com.example.helmChatRisen.jitsi.JitsiServer
import com.example.helmChatRisen.jitsi.JitsiServers
import com.example.helmChatRisen.ui.theme.HelmChatRisenTheme
import com.example.helmChatRisen.ui.theme.MotorcyclePrimary
import com.example.helmChatRisen.ui.theme.MotorcycleSecondary

/**
 * Motorcycle-optimized join screen.
 *
 * All controls are intentionally large and high-contrast so they can be operated
 * with riding gloves. The form is stateless; state is provided by [HomeViewModel].
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    recentRooms: List<RecentRoom>,
    onRoomNameChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onUseCustomServerChange: (Boolean) -> Unit,
    onPresetServerSelected: (JitsiServer) -> Unit = {},
    onCustomServerUrlChange: (String) -> Unit,
    onJwtTokenChange: (String) -> Unit,
    onRecentRoomSelected: (RecentRoom) -> Unit,
    onRecentRoomDelete: (RecentRoom) -> Unit,
    onClearRecentRooms: () -> Unit,
    onOpenMeetJitSiInBrowser: () -> Unit,
    onHostSetup: () -> Unit,
    onJoinCall: () -> Unit,
    onInvite: () -> Unit,
    currentLanguageTag: String? = null,
    onLanguageSelected: (String?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showLanguageDialog by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val horizontalPadding = if (maxWidth >= 600.dp) 48.dp else 20.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // Pinned top bar: the language action sits at the top of the app instead of
            // floating above the scrolling form.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { showLanguageDialog = true },
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Translate,
                        contentDescription = stringResource(R.string.language_button),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = horizontalPadding, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 620.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    HomeHeader()
                    if (recentRooms.isNotEmpty()) {
                        Spacer(Modifier.height(20.dp))
                        RecentRoomsSection(
                            rooms = recentRooms,
                            onSelect = onRecentRoomSelected,
                            onDelete = onRecentRoomDelete,
                            onClear = onClearRecentRooms,
                        )
                    }
                    Spacer(Modifier.height(28.dp))
                    JoinCard(
                        state = state,
                        onRoomNameChange = onRoomNameChange,
                        onDisplayNameChange = onDisplayNameChange,
                        onUseCustomServerChange = onUseCustomServerChange,
                        onPresetServerSelected = onPresetServerSelected,
                        onCustomServerUrlChange = onCustomServerUrlChange,
                        onJwtTokenChange = onJwtTokenChange,
                        onOpenMeetJitSiInBrowser = onOpenMeetJitSiInBrowser,
                        onHostSetup = onHostSetup,
                        onJoinCall = onJoinCall,
                        onInvite = onInvite,
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

    if (showLanguageDialog) {
        LanguageDialog(
            currentTag = currentLanguageTag,
            onSelect = {
                showLanguageDialog = false
                onLanguageSelected(it)
            },
            onDismiss = { showLanguageDialog = false },
        )
    }
}

/**
 * Language picker. Options show each language in its own name so a rider can find their
 * language even when the app is currently displayed in a different one.
 */
@Composable
private fun LanguageDialog(
    currentTag: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language_dialog_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                LanguageOption(
                    flag = AppLanguage.SYSTEM_FLAG,
                    label = stringResource(R.string.language_system),
                    selected = currentTag == null,
                    onClick = { onSelect(null) },
                )
                AppLanguage.supportedTags.forEach { tag ->
                    LanguageOption(
                        flag = AppLanguage.nativeFlags[tag].orEmpty(),
                        label = AppLanguage.nativeNames[tag] ?: tag,
                        selected = currentTag?.startsWith(tag) == true,
                        onClick = { onSelect(tag) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.language_close))
            }
        },
    )
}

@Composable
private fun LanguageOption(
    flag: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MotorcyclePrimary.copy(alpha = 0.14f) else Color.Transparent,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Spacer(Modifier.width(8.dp))
            Text(
                text = flag,
                fontSize = 22.sp,
                // Flags are decorative; TalkBack should read the language name only.
                modifier = Modifier.clearAndSetSemantics {},
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun HomeHeader() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(96.dp),
            shape = CircleShape,
            color = Color.White,
            border = BorderStroke(2.dp, MotorcyclePrimary.copy(alpha = 0.5f)),
        ) {
            Image(
                painter = painterResource(R.drawable.helmchat_logo),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
                contentScale = ContentScale.Fit,
            )
        }
        Text(
            text = stringResource(R.string.home_title),
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
private fun RecentRoomsSection(
    rooms: List<RecentRoom>,
    onSelect: (RecentRoom) -> Unit,
    onDelete: (RecentRoom) -> Unit,
    onClear: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel(
                    icon = Icons.Filled.History,
                    text = stringResource(R.string.home_recent_section),
                )
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = onClear,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(
                        text = stringResource(R.string.home_recent_clear),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            rooms.forEachIndexed { index, room ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    )
                }
                RecentRoomRow(
                    room = room,
                    onSelect = { onSelect(room) },
                    onDelete = { onDelete(room) },
                )
            }
        }
    }
}

@Composable
private fun RecentRoomRow(
    room: RecentRoom,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        onClick = onSelect,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Groups,
                contentDescription = null,
                tint = MotorcyclePrimary,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = room.roomName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = serverHost(room.serverUrl),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(
                        R.string.home_recent_delete,
                        room.roomName,
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun serverHost(serverUrl: String): String {
    return serverUrl
        .removePrefix("https://")
        .removePrefix("http://")
        .trimEnd('/')
}

/**
 * Drop-down with the curated public Jitsi servers. Rows are intentionally tall (76 dp) and
 * show a short description so the choice can be made with gloves on.
 */
@Composable
private fun ServerPicker(
    selected: JitsiServer,
    onSelect: (JitsiServer) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Surface(
            onClick = { expanded = true },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Dns,
                    contentDescription = null,
                    tint = MotorcyclePrimary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selected.host,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(selected.descriptionRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.92f),
        ) {
            JitsiServers.presets.forEach { preset ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = preset.host,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (preset == selected) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Normal
                                },
                            )
                            Text(
                                text = stringResource(preset.descriptionRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(preset)
                    },
                    leadingIcon = if (preset == selected) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MotorcyclePrimary,
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun JoinCard(
    state: HomeUiState,
    onRoomNameChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onUseCustomServerChange: (Boolean) -> Unit,
    onPresetServerSelected: (JitsiServer) -> Unit,
    onCustomServerUrlChange: (String) -> Unit,
    onJwtTokenChange: (String) -> Unit,
    onOpenMeetJitSiInBrowser: () -> Unit,
    onHostSetup: () -> Unit,
    onJoinCall: () -> Unit,
    onInvite: () -> Unit,
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
            Spacer(Modifier.height(12.dp))

            ServerPicker(
                selected = state.selectedServer,
                onSelect = onPresetServerSelected,
            )

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.home_server_custom_switch),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(16.dp))
                Switch(
                    checked = state.useCustomServer,
                    onCheckedChange = onUseCustomServerChange,
                )
            }

            AnimatedVisibility(visible = state.useCustomServer) {
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
                            imeAction = ImeAction.Next,
                        ),
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = state.jwtToken,
                        onValueChange = onJwtTokenChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.home_jwt_label)) },
                        placeholder = { Text(stringResource(R.string.home_jwt_placeholder)) },
                        supportingText = {
                            Text(stringResource(R.string.home_jwt_support))
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        textStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Go,
                        ),
                    )
                }
            }

            AnimatedVisibility(visible = state.isMeetJitSiServer) {
                Column {
                    Spacer(Modifier.height(20.dp))
                    MeetJitSiWarning(
                        onHostSetup = onHostSetup,
                        onOpenInBrowser = onOpenMeetJitSiInBrowser,
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

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = onInvite,
                enabled = state.canInvite,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(2.dp, MotorcyclePrimary),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MotorcyclePrimary,
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.home_invite),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun MeetJitSiWarning(
    onHostSetup: () -> Unit,
    onOpenInBrowser: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.home_meet_jitsi_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.home_meet_jitsi_warning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.home_meet_jitsi_password_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onHostSetup,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MotorcyclePrimary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.VpnKey,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.home_meet_jitsi_host),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onOpenInBrowser,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.OpenInBrowser,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.home_meet_jitsi_open),
                    fontSize = 18.sp,
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
    HelmChatRisenTheme {
        HomeScreen(
            state = HomeUiState(
                roomName = "niedzielna-przejezdzajka",
            ),
            recentRooms = listOf(
                RecentRoom(
                    roomName = "niedzielna-przejezdzajka",
                    serverUrl = "https://meet.ffmuc.net",
                    lastUsedAt = 0L,
                ),
            ),
            onRoomNameChange = {},
            onDisplayNameChange = {},
            onUseCustomServerChange = {},
            onPresetServerSelected = {},
            onCustomServerUrlChange = {},
            onJwtTokenChange = {},
            onRecentRoomSelected = {},
            onRecentRoomDelete = {},
            onClearRecentRooms = {},
            onOpenMeetJitSiInBrowser = {},
            onHostSetup = {},
            onJoinCall = {},
            onInvite = {},
        )
    }
}
