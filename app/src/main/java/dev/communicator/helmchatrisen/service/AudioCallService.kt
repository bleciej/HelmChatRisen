package dev.communicator.helmchatrisen.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import timber.log.Timber
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.communicator.helmchatrisen.MainActivity
import dev.communicator.helmchatrisen.R
import dev.communicator.helmchatrisen.audio.BluetoothAudioRouter
import dev.communicator.helmchatrisen.data.AppLanguage
import dev.communicator.helmchatrisen.jitsi.CallState
import dev.communicator.helmchatrisen.jitsi.JitsiCallManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Foreground service that keeps the microphone alive while an embedded Jitsi call is running.
 *
 * Jitsi's own ongoing-conference service is only started by [org.jitsi.meet.sdk.JitsiMeetActivity],
 * which is not used here. This service therefore owns the foreground notification and exposes
 * mute / hang-up actions. It does not join the conference itself; the embedded `JitsiMeetView`
 * does that from the Compose UI.
 */
class AudioCallService : Service() {

    /**
     * Keeps the foreground notification (channel name, description, actions) in the language
     * chosen by the rider on Android 12L and lower.
     *
     * @param newBase the base context provided by the framework.
     * @return the base context wrapped with the rider's language choice.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrapContext(newBase))
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "helmchat_audio_call_channel"

        const val ACTION_START_CALL = "dev.communicator.helmchatrisen.action.START_CALL"
        const val ACTION_END_CALL = "dev.communicator.helmchatrisen.action.END_CALL"
        const val ACTION_TOGGLE_MUTE = "dev.communicator.helmchatrisen.action.TOGGLE_MUTE"

        const val EXTRA_ROOM_NAME = "extra_room_name"
        const val EXTRA_SERVER_URL = "extra_server_url"

        /**
         * Starts (or restarts) the foreground service that owns the microphone for [roomName]
         * on [serverUrl]. Call this while the app is in the foreground so the Android 12+
         * background-start restrictions do not apply.
         *
         * @param context the context used to start the foreground service.
         * @param roomName the Jitsi room to keep the microphone alive for.
         * @param serverUrl the Jitsi server hosting the room; defaults to
         *   [JitsiCallManager.DEFAULT_SERVER_URL].
         */
        fun startCall(
            context: Context,
            roomName: String,
            serverUrl: String = JitsiCallManager.DEFAULT_SERVER_URL,
        ) {
            val intent = Intent(context, AudioCallService::class.java).apply {
                action = ACTION_START_CALL
                putExtra(EXTRA_ROOM_NAME, roomName)
                putExtra(EXTRA_SERVER_URL, serverUrl)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /**
         * Asks the running service to begin hanging up. The service keeps its foreground
         * notification and audio route until Jitsi reaches a terminal state, so the ending status
         * remains visible while conference teardown is in flight. Safe when no call is active.
         *
         * @param context the context used to deliver the end-call intent.
         */
        fun stopCall(context: Context) {
            val intent = Intent(context, AudioCallService::class.java).apply {
                action = ACTION_END_CALL
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var bluetoothAudioRouter: BluetoothAudioRouter
    private lateinit var jitsiCallManager: JitsiCallManager

    private var callActive = false
    private var activeRoomName: String = ""
    private var activeServerUrl: String = JitsiCallManager.DEFAULT_SERVER_URL

    /**
     * Resolves the audio router and Jitsi manager, creates the notification channel, and
     * subscribes to call, mute and connectivity changes so the foreground notification stays
     * current.
     */
    override fun onCreate() {
        super.onCreate()
        Timber.d("AudioCallService created")
        bluetoothAudioRouter = BluetoothAudioRouter.getInstance(applicationContext)
        jitsiCallManager = JitsiCallManager.getInstance()
        createNotificationChannel()

        jitsiCallManager.callState.onEach { state ->
            when (state) {
                is CallState.Connecting -> updateNotification()
                // Jitsi may reload the conference while reconnecting; keep the service, mic
                // and audio route alive and refresh the notification for both states.
                is CallState.Connected -> applySessionState(state.roomName, state.serverUrl)
                is CallState.Reconnecting -> applySessionState(state.roomName, state.serverUrl)
                is CallState.Error -> {
                    Timber.e("Call error: ${state.message ?: state.messageResId}")
                    updateNotification()
                }
                is CallState.Ending -> updateNotification()
                is CallState.Idle -> finishCall()
            }
        }.launchIn(serviceScope)

        jitsiCallManager.isMuted.onEach {
            if (callActive) {
                updateNotification()
            }
        }.launchIn(serviceScope)

        jitsiCallManager.isNetworkAvailable.onEach {
            if (callActive) {
                updateNotification()
            }
        }.launchIn(serviceScope)
    }

    /**
     * Handles the start/end/mute actions delivered through the service intents and returns
     * START_NOT_STICKY so Android does not recreate a finished call.
     *
     * @param intent the intent carrying one of the service actions, or `null` when the system
     *   restarts the service without one.
     * @param flags additional flags supplied with this start request.
     * @param startId the unique id of this start request.
     * @return START_NOT_STICKY so a killed service is not recreated automatically.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_CALL -> {
                val roomName = intent.getStringExtra(EXTRA_ROOM_NAME) ?: ""
                val serverUrl = intent.getStringExtra(EXTRA_SERVER_URL)
                    ?: JitsiCallManager.DEFAULT_SERVER_URL
                startCallInternal(roomName, serverUrl)
            }
            ACTION_END_CALL -> {
                endCallInternal()
            }
            ACTION_TOGGLE_MUTE -> {
                jitsiCallManager.toggleMute()
            }
        }
        return START_NOT_STICKY
    }

    /**
     * The service is not bound; it is driven entirely through onStartCommand intents.
     *
     * @param intent the bind intent, which is ignored.
     * @return `null`, because the service exposes no binder.
     */
    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Releases the helmet audio route and stops the service coroutine scope.
     */
    override fun onDestroy() {
        super.onDestroy()
        Timber.d("AudioCallService destroyed")
        bluetoothAudioRouter.stopAudioRouting()
        bluetoothAudioRouter.resetForNewCall()
        serviceScope.cancel()
    }

    /**
     * Marks the call active, routes audio to the helmet headset, and promotes the service to
     * the microphone foreground type. Stops itself when [roomName] is blank.
     *
     * @param roomName the Jitsi room to keep the microphone alive for.
     * @param serverUrl the Jitsi server hosting the room.
     */
    private fun startCallInternal(roomName: String, serverUrl: String) {
        if (roomName.isBlank()) {
            Timber.e("Cannot start call with empty room name")
            stopSelf()
            return
        }

        activeRoomName = roomName
        activeServerUrl = serverUrl
        callActive = true
        bluetoothAudioRouter.resetForNewCall()
        // Own the communication audio mode and helmet route for the whole call. Jitsi's own
        // audio focus handling is disabled so music keeps playing (ducked).
        bluetoothAudioRouter.startAudioRouting()

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /**
     * Begins conference teardown without dropping the foreground service or audio route yet.
     *
     * The [CallState.Idle] observer calls [finishCall] once Jitsi confirms the hang-up or the
     * manager's fallback timeout expires. Repeated end actions are ignored.
     */
    private fun endCallInternal() {
        if (!callActive) return
        if (jitsiCallManager.callState.value is CallState.Ending) {
            Timber.d("Call teardown already in progress, ignoring repeated end action")
            updateNotification()
            return
        }
        Timber.i("Ending call")
        val teardownPending = jitsiCallManager.hangUp()
        if (teardownPending) {
            updateNotification()
        } else {
            Timber.i("Jitsi had no active conference to leave")
            finishCall()
        }
    }

    /**
     * Stops the foreground service only after the call reached a terminal state.
     */
    private fun finishCall() {
        if (!callActive) return
        callActive = false
        stopForegroundAndSelf()
    }

    /**
     * Removes the ongoing notification and stops the service.
     */
    private fun stopForegroundAndSelf() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Registers the low-importance call channel on Android 8.0+; a no-op on older releases,
     * which have no notification channels.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }
            val notificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Builds the ongoing call notification with its status text, "open app" tap target and the
     * mute/unmute and end-call actions.
     *
     * @return the notification posted while the call is active.
     */
    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpenIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleMuteIntent = Intent(this, AudioCallService::class.java).apply {
            action = ACTION_TOGGLE_MUTE
        }
        val pendingToggleMuteIntent = PendingIntent.getService(
            this,
            1,
            toggleMuteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val endCallIntent = Intent(this, AudioCallService::class.java).apply {
            action = ACTION_END_CALL
        }
        val pendingEndCallIntent = PendingIntent.getService(
            this,
            2,
            endCallIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isEnding = jitsiCallManager.callState.value is CallState.Ending
        val status = when {
            isEnding -> getString(R.string.notification_ending)
            !jitsiCallManager.isNetworkAvailable.value ->
                getString(R.string.notification_reconnecting)
            jitsiCallManager.callState.value is CallState.Connected ->
                getString(R.string.notification_connected)
            jitsiCallManager.callState.value is CallState.Reconnecting ->
                getString(R.string.notification_reconnecting)
            else -> getString(R.string.notification_connecting)
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title, activeRoomName))
            .setContentText(status)
            .setSubText(activeServerUrl)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingOpenIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        // While ending, the actions would re-enter a teardown that is already in progress.
        if (!isEnding) {
            val muted = jitsiCallManager.isMuted.value
            builder.addAction(
                if (muted) android.R.drawable.ic_lock_silent_mode_off else android.R.drawable.ic_lock_silent_mode,
                getString(if (muted) R.string.notification_unmute else R.string.notification_mute),
                pendingToggleMuteIntent
            )
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.notification_end),
                pendingEndCallIntent
            )
        }

        return builder.build()
    }

    /**
     * Records the room and server reported by Jitsi while the call is connected or reconnecting
     * and refreshes the notification. The room name is captured only once so a reconnect cannot
     * overwrite it.
     *
     * @param roomName the room reported by Jitsi for the current session.
     * @param serverUrl the server reported by Jitsi for the current session.
     */
    private fun applySessionState(roomName: String, serverUrl: String) {
        if (activeRoomName.isBlank()) {
            activeRoomName = roomName
        }
        activeServerUrl = serverUrl
        updateNotification()
    }

    /**
     * Re-posts the ongoing notification, or does nothing when no call is active.
     */
    private fun updateNotification() {
        if (!callActive) return
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }
}
