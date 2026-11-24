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
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import timber.log.Timber
import dev.communicator.helmchatrisen.R
import dev.communicator.helmchatrisen.audio.AudioRoutingState
import dev.communicator.helmchatrisen.audio.resolveAudioRouteControl
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
 * Immutable inputs for the call detail pane.
 *
 * @param screenState the state consumed by the custom call overlay.
 * @param route the Navigation 3 call destination being rendered.
 * @param routingState the current audio route reported by the router.
 * @param isHeadsetConnected whether a headset is available for call audio.
 * @param isInPictureInPicture whether the compact Picture-in-Picture layout is active.
 */
private data class CallRouteUiState(
    val screenState: CallScreenState,
    val route: CallRoute,
    val routingState: AudioRoutingState,
    val isHeadsetConnected: Boolean,
    val isInPictureInPicture: Boolean,
)

/**
 * Callbacks used by the call detail pane.
 *
 * @param onToggleMute flips the local microphone state.
 * @param onToggleSpeaker switches the audio route based on the current routing state.
 * @param onLeaveCall ends the conference through the normal hang-up flow.
 * @param onInvite shares the invitation for the supplied route.
 * @param onEnterPictureInPicture minimizes the call into Picture-in-Picture.
 */
private data class CallRouteUiActions(
    val onToggleMute: () -> Unit,
    val onToggleSpeaker: (AudioRoutingState) -> Unit,
    val onLeaveCall: () -> Unit,
    val onInvite: (CallRoute) -> Unit,
    val onEnterPictureInPicture: () -> Unit,
)

/**
 * Renders the detail pane for an active call, keeping Jitsi's native view behind the custom overlay.
 *
 * @param state the call state and route information to display.
 * @param actions callbacks for rider interactions.
 */
@Composable
private fun CallRouteEntry(
    state: CallRouteUiState,
    actions: CallRouteUiActions,
) {
    var hostSetupActive by remember(state.route) {
        mutableStateOf(state.route.hostSetup)
    }

    CallScreen(
        state = state.screenState.copy(
            audioRouteLabel = audioRouteLabel(
                isHeadsetConnected = state.isHeadsetConnected,
                routingState = state.routingState,
            ),
        ),
        onToggleMute = actions.onToggleMute,
        onToggleSpeaker = { actions.onToggleSpeaker(state.routingState) },
        onLeaveCall = actions.onLeaveCall,
        onInvite = { actions.onInvite(state.route) },
        isInPictureInPicture = state.isInPictureInPicture,
        onEnterPictureInPicture = actions.onEnterPictureInPicture,
        hostSetup = hostSetupActive,
        onHostSetupDone = { hostSetupActive = false },
        conferenceContent = {
            JitsiConferenceView(
                roomName = state.route.roomName,
                serverUrl = state.route.serverUrl,
                displayName = state.route.displayName,
                isMuted = state.screenState.isMuted,
                modifier = Modifier.fillMaxSize(),
                token = state.route.token,
                interactive = hostSetupActive,
            )
        },
    )
}

/**
 * Builds the localized label shown on the audio-route control.
 *
 * @param isHeadsetConnected whether a headset is available for call audio.
 * @param routingState the current audio route reported by the router.
 * @return the label describing the active output.
 */
@Composable
private fun audioRouteLabel(
    isHeadsetConnected: Boolean,
    routingState: AudioRoutingState,
): String {
    if (!isHeadsetConnected) return stringResource(R.string.call_speaker_on)
    return when (routingState) {
        is AudioRoutingState.SpeakerConnected -> stringResource(R.string.call_speaker_on)
        is AudioRoutingState.HeadsetConnected ->
            routingState.deviceName?.let { deviceName ->
                stringResource(R.string.call_route_bluetooth, deviceName)
            } ?: stringResource(R.string.call_speaker_off)
        is AudioRoutingState.ConnectingBluetooth -> stringResource(R.string.call_route_connecting)
        else -> stringResource(R.string.call_speaker_off)
    }
}

/**
 * Resolves a Jitsi error into a localized message.
 *
 * @param callState the current call state.
 * @return the error message, or `null` when the state is not an error.
 */
@Composable
private fun callErrorMessage(callState: CallState): String? =
    (callState as? CallState.Error)?.let { (message, messageResId) ->
        message ?: messageResId?.let { id -> stringResource(id) }
    }

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
    val callStartedAtMillis by callManager.callStartedAtMillis.collectAsStateWithLifecycle()
    val isMuted by callManager.isMuted.collectAsStateWithLifecycle()
    val participantCount by callManager.participantCount.collectAsStateWithLifecycle()
    val isNetworkAvailable by callManager.isNetworkAvailable.collectAsStateWithLifecycle()
    val routingState by audioRouter.routingState.collectAsStateWithLifecycle()
    val isHeadsetConnected by audioRouter.isHeadsetConnected.collectAsStateWithLifecycle()

    var pendingCall by remember { mutableStateOf<CallRequest?>(null) }
    var showPermissionDialog by remember { mutableStateOf(value = false) }
    var languageTag by remember { mutableStateOf(AppLanguage.currentTag(context)) }

    /**
     * Starts the foreground service and pushes the call destination for [request]. Requests made
     * while a previous conference is ending are ignored so stale teardown events cannot affect a
     * new call.
     *
     * @param request the room, server and host-setup options supplied by the home screen.
     */
    fun startCall(request: CallRequest) {
        if (!callManager.canStartNewCall) {
            Timber.w("Ignoring join request for '${request.roomName}' during $callState")
            return
        }
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
            ),
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
    var joinedRouteKey by remember { mutableStateOf<CallRoute?>(null) }
    val hasJoinedCall = ((currentRoute is CallRoute) && (joinedRouteKey == currentRoute))

    /**
     * Removes the call destination after a terminal state, keeping at least one back-stack entry.
     */
    fun dismissCallRoute() {
        if (backStack.isEmpty()) return
        if (backStack.size > 1) {
            backStack.removeLastOrNull()
        } else {
            // A restored call destination can be the only entry; replace it instead of emptying
            // the Navigation 3 back stack.
            backStack[backStack.lastIndex] = HomeRoute
        }
    }

    LaunchedEffect(callState, currentRoute) {
        syncCallRouteState(
            callState = callState,
            currentRoute = currentRoute,
            hasJoinedCall = hasJoinedCall,
            onJoined = { route -> joinedRouteKey = route },
            onIdle = {
                // The service observes the same Idle state and stops its own foreground
                // notification and audio route, so no extra service command is needed here.
                joinedRouteKey = null
                dismissCallRoute()
            },
        ) {
            joinedRouteKey = null
        }
    }

    LaunchedEffect(currentRoute) {
        onCallScreenVisibilityChanged(currentRoute is CallRoute)
    }

    /**
     * Asks Jitsi to leave while keeping the call destination mounted until the SDK confirms the
     * hang-up. Removing the destination earlier would dispose the embedded view with HANG_UP still
     * in flight and leave the microphone active.
     *
     * @return `true` when the call screen will be removed by the [CallState.Idle] observer.
     */
    fun leaveCall(): Boolean {
        (currentRoute as? CallRoute)?.let { route -> joinedRouteKey = route }
        val teardownPending = callManager.hangUp()
        if (!teardownPending) dismissCallRoute()
        return teardownPending
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
            onBack = { handleBackNavigation(backStack, context) { leaveCall() } },
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
                                startCallWithPermissionCheck(
                                    request = request,
                                    context = context,
                                    onGranted = ::startCall,
                                    onPermissionNeeded = { request ->
                                        pendingCall = request
                                        permissionLauncher.launch(requestedPermissions())
                                    },
                                )
                            }
                        },
                        onJoinCall = {
                            homeViewModel.createCallRequest()?.let { request ->
                                startCallWithPermissionCheck(
                                    request = request,
                                    context = context,
                                    onGranted = ::startCall,
                                    onPermissionNeeded = { request ->
                                        pendingCall = request
                                        permissionLauncher.launch(requestedPermissions())
                                    },
                                )
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
                    val audioRouteControl = resolveAudioRouteControl(
                        isHeadsetConnected = isHeadsetConnected,
                        routingState = routingState,
                    )
                    CallRouteEntry(
                        state = CallRouteUiState(
                            screenState = CallScreenState(
                                roomName = route.roomName,
                                serverUrl = route.serverUrl,
                                isConnected = callState is CallState.Connected,
                                isReconnecting = callState is CallState.Reconnecting,
                                isOffline = !isNetworkAvailable,
                                isMuted = isMuted,
                                isSpeakerOn = audioRouteControl.isSpeaker,
                                canSwitchAudioRoute = audioRouteControl.canSwitch,
                                participantCount = participantCount,
                                errorMessage = callErrorMessage(callState),
                                isEnding = callState is CallState.Ending,
                                callStartedAtMillis = callStartedAtMillis,
                            ),
                            route = route,
                            routingState = routingState,
                            isHeadsetConnected = isHeadsetConnected,
                            isInPictureInPicture = isInPictureInPicture,
                        ),
                        actions = CallRouteUiActions(
                            onToggleMute = { callManager.toggleMute() },
                            onToggleSpeaker = { currentRouting ->
                                audioRouter.setSpeakerphone(
                                    currentRouting !is AudioRoutingState.SpeakerConnected,
                                )
                            },
                            onLeaveCall = { leaveCall() },
                            onInvite = { invitedRoute ->
                                shareRoomInvite(
                                    context = context,
                                    roomName = invitedRoute.roomName,
                                    serverUrl = invitedRoute.serverUrl,
                                )
                            },
                            onEnterPictureInPicture = onEnterPictureInPicture,
                        ),
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

/**
 * Handles back navigation without disposing an active conference before Jitsi confirms hang-up.
 *
 * @param backStack the current Navigation 3 back stack.
 * @param context the activity context used to finish the app when the stack has one entry.
 * @param onLeaveCall asks Jitsi to tear down the active call.
 */
private fun handleBackNavigation(
    backStack: MutableList<NavKey>,
    context: Context,
    onLeaveCall: () -> Unit,
) {
    if (backStack.lastOrNull() is CallRoute) {
        onLeaveCall()
    } else if (backStack.size > 1) {
        backStack.removeLastOrNull()
    } else {
        (context as? Activity)?.finish()
    }
}

/**
 * Starts a call immediately when all permissions are granted, otherwise defers the request.
 *
 * @param request the call request produced by the home screen.
 * @param context the context used to check runtime permissions.
 * @param onGranted starts the call with the granted request.
 * @param onPermissionNeeded stores the request so the permission launcher can resume it.
 */
private fun startCallWithPermissionCheck(
    request: CallRequest,
    context: Context,
    onGranted: (CallRequest) -> Unit,
    onPermissionNeeded: (CallRequest) -> Unit,
) {
    if (areRequiredPermissionsGranted(context)) {
        onGranted(request)
    } else {
        onPermissionNeeded(request)
    }
}

/**
 * Keeps the joined destination mounted until Jitsi reaches a terminal call state.
 *
 * @param callState the current call state.
 * @param currentRoute the top of the Navigation 3 back stack.
 * @param hasJoinedCall whether the current route was previously marked as joined.
 * @param onJoined records the route as the active call destination.
 * @param onIdle clears the route after Jitsi confirms the conference ended.
 * @param onReset clears join tracking when no call destination is present.
 */
private fun syncCallRouteState(
    callState: CallState,
    currentRoute: NavKey?,
    hasJoinedCall: Boolean,
    onJoined: (CallRoute) -> Unit,
    onIdle: () -> Unit,
    onReset: () -> Unit,
) {
    if (currentRoute !is CallRoute) {
        onReset()
        return
    }
    when (callState) {
        is CallState.Connecting,
        is CallState.Connected,
        is CallState.Ending,
        is CallState.Reconnecting,
        -> onJoined(currentRoute)
        is CallState.Idle -> if (hasJoinedCall) onIdle()
        is CallState.Error -> Unit
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
    val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
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
