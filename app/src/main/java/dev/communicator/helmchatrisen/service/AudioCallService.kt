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

    override fun onCreate() {
        super.onCreate()
        Timber.d("AudioCallService created")
        bluetoothAudioRouter = BluetoothAudioRouter.getInstance(applicationContext)
        jitsiCallManager = JitsiCallManager.getInstance()
        createNotificationChannel()

        jitsiCallManager.callState.onEach { state ->
            when (state) {
                is CallState.Connecting -> {
                    updateNotification()
                }
                is CallState.Connected -> {
                    if (activeRoomName.isBlank()) {
                        activeRoomName = state.roomName
                    }
                    activeServerUrl = state.serverUrl
                    updateNotification()
                }
                is CallState.Reconnecting -> {
                    // Jitsi is reloading the conference; keep the service, mic and audio route
                    // alive so the call resumes without the rider doing anything.
                    if (activeRoomName.isBlank()) {
                        activeRoomName = state.roomName
                    }
                    activeServerUrl = state.serverUrl
                    updateNotification()
                }
                is CallState.Error -> {
                    Timber.e("Call error: ${state.message ?: state.messageResId}")
                    updateNotification()
                }
                is CallState.Idle -> {
                    if (callActive) {
                        callActive = false
                        stopForegroundAndSelf()
                    }
                }
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("AudioCallService destroyed")
        bluetoothAudioRouter.stopAudioRouting()
        bluetoothAudioRouter.resetForNewCall()
        serviceScope.cancel()
    }

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun endCallInternal() {
        Timber.i("Ending call")
        if (callActive) {
            callActive = false
            jitsiCallManager.hangUp()
        }
        stopForegroundAndSelf()
    }

    private fun stopForegroundAndSelf() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

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
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

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

        val muted = jitsiCallManager.isMuted.value
        val status = when {
            !jitsiCallManager.isNetworkAvailable.value ->
                getString(R.string.notification_reconnecting)
            jitsiCallManager.callState.value is CallState.Connected ->
                getString(R.string.notification_connected)
            jitsiCallManager.callState.value is CallState.Reconnecting ->
                getString(R.string.notification_reconnecting)
            else -> getString(R.string.notification_connecting)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title, activeRoomName))
            .setContentText(status)
            .setSubText(activeServerUrl)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingOpenIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                if (muted) android.R.drawable.ic_lock_silent_mode_off else android.R.drawable.ic_lock_silent_mode,
                getString(if (muted) R.string.notification_unmute else R.string.notification_mute),
                pendingToggleMuteIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.notification_end),
                pendingEndCallIntent
            )
            .build()
    }

    private fun updateNotification() {
        if (!callActive) return
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }
}
