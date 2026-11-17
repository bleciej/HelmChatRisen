package dev.communicator.helmchatrisen.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import dev.communicator.helmchatrisen.R
import dev.communicator.helmchatrisen.audio.AudioRoutingState
import dev.communicator.helmchatrisen.data.AppLanguage
import dev.communicator.helmchatrisen.data.RecentRoom
import dev.communicator.helmchatrisen.data.RecentRoomsRepository
import dev.communicator.helmchatrisen.audio.BluetoothAudioRouter
import dev.communicator.helmchatrisen.jitsi.CallState
import dev.communicator.helmchatrisen.jitsi.JitsiCallManager
import dev.communicator.helmchatrisen.jitsi.RoomLinks
import dev.communicator.helmchatrisen.service.AudioCallService
import dev.communicator.helmchatrisen.ui.call.CallScreen
import dev.communicator.helmchatrisen.ui.call.CallScreenState
import dev.communicator.helmchatrisen.ui.call.JitsiConferenceView
import dev.communicator.helmchatrisen.ui.home.CallRequest
import dev.communicator.helmchatrisen.ui.home.HomeScreen
import dev.communicator.helmchatrisen.ui.home.HomeViewModel
import dev.communicator.helmchatrisen.ui.navigation.CallRoute
import dev.communicator.helmchatrisen.ui.navigation.HomeRoute
import kotlinx.coroutines.launch

/**
 * Root of the HelmChatRisen Compose UI.
 *
 * Navigation is state-driven with Navigation 3. A Material 3 Adaptive list-detail
 * scene strategy is used so that on wide screens the join form (list pane) and the
 * active call (detail pane) are shown side by side, while phones show one pane at a time.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun HelmChatApp(
    isInPictureInPicture: Boolean = false,
    onCallScreenVisibilityChanged: (Boolean) -> Unit = {},
    onEnterPictureInPicture: () -> Unit = {},
) {
    val context = LocalContext.current
    val backStack = rememberNavBackStack(HomeRoute)
    val homeViewModel: HomeViewModel = viewModel()
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val recentRoomsRepository = remember(context) { RecentRoomsRepository(context) }
    val recentRooms by recentRoomsRepository.recentRooms
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val coroutineScope = rememberCoroutineScope()

    val callManager = remember { JitsiCallManager.getInstance() }
    val audioRouter = remember(context) { BluetoothAudioRouter.getInstance(context) }

    val callState by callManager.callState.collectAsStateWithLifecycle()
    val isMuted by callManager.isMuted.collectAsStateWithLifecycle()
    val participantCount by callManager.participantCount.collectAsStateWithLifecycle()
    val isNetworkAvailable by callManager.isNetworkAvailable.collectAsStateWithLifecycle()
    val routingState by audioRouter.routingState.collectAsStateWithLifecycle()

    var pendingCall by remember { mutableStateOf<CallRequest?>(null) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(false) }
    var languageTag by remember { mutableStateOf(AppLanguage.currentTag(context)) }

    fun startCall(request: CallRequest) {
        isSpeakerOn = false
        audioRouter.resetForNewCall()
        AudioCallService.startCall(context, request.roomName, request.serverUrl)
        coroutineScope.launch {
            recentRoomsRepository.record(
                RecentRoom(
                    roomName = request.roomName,
                    serverUrl = request.serverUrl,
                    displayName = request.displayName,
                    lastUsedAt = System.currentTimeMillis(),
                ),
            )
        }
        backStack.add(
            CallRoute(
                roomName = request.roomName,
                serverUrl = request.serverUrl,
                displayName = request.displayName,
                token = request.token,
                hostSetup = request.hostSetup,
            )
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val request = pendingCall
        pendingCall = null
        if (request != null) {
            if (areRequiredPermissionsGranted(result)) {
                startCall(request)
            } else {
                showPermissionDialog = true
            }
        }
    }

    val currentRoute = backStack.lastOrNull()
    var hasJoinedCall by remember(currentRoute) { mutableStateOf(false) }

    LaunchedEffect(callState, currentRoute) {
        if (currentRoute is CallRoute) {
            when (callState) {
                is CallState.Connecting,
                is CallState.Connected,
                is CallState.Reconnecting -> hasJoinedCall = true
                is CallState.Idle -> if (hasJoinedCall && backStack.size > 1) {
                    // The service stops itself as soon as the call state reaches Idle, and
                    // leaveCall() stops it explicitly, so no extra stopCall() is needed here.
                    backStack.removeLastOrNull()
                }
                is CallState.Error -> Unit
            }
        } else {
            hasJoinedCall = false
        }
    }

    LaunchedEffect(currentRoute) {
        onCallScreenVisibilityChanged(currentRoute is CallRoute)
    }

    fun leaveCall() {
        callManager.hangUp()
        AudioCallService.stopCall(context)
        if (backStack.size > 1) {
            backStack.removeLastOrNull()
        }
    }

    val windowAdaptiveInfo = currentWindowAdaptiveInfoV2()
    val directive = remember(windowAdaptiveInfo) {
        calculatePaneScaffoldDirective(windowAdaptiveInfo)
            .copy(horizontalPartitionSpacerSize = 0.dp)
    }
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>(directive = directive)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.fillMaxSize(),
            onBack = {
                if (backStack.lastOrNull() is CallRoute) {
                    callManager.hangUp()
                    AudioCallService.stopCall(context)
                    if (backStack.size > 1) {
                        backStack.removeLastOrNull()
                    } else {
                        (context as? Activity)?.finish()
                    }
                } else if (backStack.size > 1) {
                    backStack.removeLastOrNull()
                } else {
                    (context as? Activity)?.finish()
                }
            },
            sceneStrategies = listOf(listDetailStrategy),
            entryProvider = entryProvider {
                entry<HomeRoute>(
                    metadata = ListDetailSceneStrategy.listPane(
                        detailPlaceholder = { CallPlaceholder() },
                    ),
                ) {
                    HomeScreen(
                        state = homeState,
                        recentRooms = recentRooms,
                        onRoomNameChange = homeViewModel::onRoomNameChange,
                        onDisplayNameChange = homeViewModel::onDisplayNameChange,
                        onUseCustomServerChange = homeViewModel::onUseCustomServerChange,
                        onPresetServerSelected = homeViewModel::onPresetServerSelected,
                        onCustomServerUrlChange = homeViewModel::onCustomServerUrlChange,
                        onJwtTokenChange = homeViewModel::onJwtTokenChange,
                        onRecentRoomSelected = homeViewModel::applyRecentRoom,
                        onRecentRoomDelete = { room ->
                            coroutineScope.launch { recentRoomsRepository.remove(room) }
                        },
                        onClearRecentRooms = {
                            coroutineScope.launch { recentRoomsRepository.clear() }
                        },
                        onOpenMeetJitSiInBrowser = {
                            openMeetJitSiInBrowser(context, homeState.roomName)
                        },
                        onHostSetup = {
                            homeViewModel.createCallRequest(hostSetup = true)?.let { request ->
                                if (areRequiredPermissionsGranted(context)) {
                                    startCall(request)
                                } else {
                                    pendingCall = request
                                    permissionLauncher.launch(requestedPermissions())
                                }
                            }
                        },
                        onJoinCall = {
                            homeViewModel.createCallRequest()?.let { request ->
                                if (areRequiredPermissionsGranted(context)) {
                                    startCall(request)
                                } else {
                                    pendingCall = request
                                    permissionLauncher.launch(requestedPermissions())
                                }
                            }
                        },
                        onInvite = {
                            shareRoomInvite(
                                context = context,
                                roomName = homeState.roomName,
                                serverUrl = homeState.effectiveServerUrl,
                            )
                        },
                        currentLanguageTag = languageTag,
                        onLanguageSelected = { tag ->
                            AppLanguage.setTag(context, tag)
                            languageTag = AppLanguage.currentTag(context)
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                (context as? Activity)?.recreate()
                            }
                        },
                    )
                }

                entry<CallRoute>(
                    metadata = ListDetailSceneStrategy.detailPane(),
                ) { route ->
                    val isConnected = callState is CallState.Connected
                    val isReconnecting = callState is CallState.Reconnecting
                    val errorMessage = (callState as? CallState.Error)?.let { it.message ?: it.messageResId?.let { id -> stringResource(id) } }
                    var hostSetupActive by remember(route) {
                        mutableStateOf(route.hostSetup)
                    }
                    val audioRouteLabel = when (val routing = routingState) {
                        is AudioRoutingState.SpeakerConnected ->
                            stringResource(R.string.call_speaker_on)
                        is AudioRoutingState.BluetoothConnected ->
                            routing.deviceName?.let {
                                stringResource(R.string.call_route_bluetooth, it)
                            } ?: stringResource(R.string.call_speaker_off)
                        is AudioRoutingState.ConnectingBluetooth ->
                            stringResource(R.string.call_route_connecting)
                        else -> stringResource(R.string.call_speaker_off)
                    }

                    CallScreen(
                        state = CallScreenState(
                            roomName = route.roomName,
                            serverUrl = route.serverUrl,
                            isConnected = isConnected,
                            isReconnecting = isReconnecting,
                            isOffline = !isNetworkAvailable,
                            isMuted = isMuted,
                            isSpeakerOn = isSpeakerOn,
                            audioRouteLabel = audioRouteLabel,
                            participantCount = participantCount,
                            errorMessage = errorMessage,
                        ),
                        onToggleMute = { callManager.toggleMute() },
                        onToggleSpeaker = {
                            val newSpeaker = !isSpeakerOn
                            isSpeakerOn = newSpeaker
                            audioRouter.setSpeakerphone(newSpeaker)
                        },
                        onLeaveCall = { leaveCall() },
                        onInvite = {
                            shareRoomInvite(
                                context = context,
                                roomName = route.roomName,
                                serverUrl = route.serverUrl,
                            )
                        },
                        isInPictureInPicture = isInPictureInPicture,
                        onEnterPictureInPicture = onEnterPictureInPicture,
                        hostSetup = hostSetupActive,
                        onHostSetupDone = { hostSetupActive = false },
                        conferenceContent = {
                            JitsiConferenceView(
                                roomName = route.roomName,
                                serverUrl = route.serverUrl,
                                displayName = route.displayName,
                                isMuted = isMuted,
                                modifier = Modifier.fillMaxSize(),
                                token = route.token,
                                interactive = hostSetupActive,
                            )
                        },
                    )
                }
            },
        )
    }

    if (showPermissionDialog) {
        PermissionDeniedDialog(
            onOpenSettings = {
                showPermissionDialog = false
                openAppSettings(context)
            },
            onDismiss = {
                showPermissionDialog = false
                pendingCall = null
            },
        )
    }
}

@Composable
private fun PermissionDeniedDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.permission_title)) },
        text = { Text(stringResource(R.string.permission_body)) },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.permission_open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.permission_cancel))
            }
        },
    )
}

private fun requestedPermissions(): Array<String> {
    return buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()
}

private fun areRequiredPermissionsGranted(context: Context): Boolean {
    val recordAudio = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED
    val bluetooth = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT,
        ) == PackageManager.PERMISSION_GRANTED
    return recordAudio && bluetooth
}

private fun areRequiredPermissionsGranted(result: Map<String, Boolean>): Boolean {
    val recordAudio = result[Manifest.permission.RECORD_AUDIO] == true
    val bluetooth = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        result[Manifest.permission.BLUETOOTH_CONNECT] == true
    return recordAudio && bluetooth
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

/**
 * meet.jit.si requires a moderator to sign in with Google/GitHub/Facebook before a room can
 * be started. The embedded Jitsi UI is hidden behind our own overlay, so the rider can
 * authenticate in the system browser and then join the already-started room from the app.
 */
private fun openMeetJitSiInBrowser(context: Context, roomName: String) {
    val room = roomName.trim()
    val url = if (room.isBlank()) {
        "https://meet.jit.si"
    } else {
        "https://meet.jit.si/${Uri.encode(room)}"
    }
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

/**
 * Opens the system share sheet with a ready-to-send invitation. The message contains the
 * room link, so a new rider can join from a browser even without the app installed.
 */
private fun shareRoomInvite(context: Context, roomName: String, serverUrl: String) {
    val room = roomName.trim()
    if (room.isEmpty()) return
    val link = RoomLinks.roomLink(serverUrl, room)
    val message = context.getString(R.string.home_invite_message, room, link)
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, room)
        putExtra(Intent.EXTRA_TEXT, message)
    }
    val chooser = Intent.createChooser(sendIntent, context.getString(R.string.home_invite))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}

/**
 * Detail-pane placeholder shown on wide screens when no call is active.
 */
@Composable
private fun CallPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(104.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Headset,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(52.dp),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.call_placeholder_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.call_placeholder_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
