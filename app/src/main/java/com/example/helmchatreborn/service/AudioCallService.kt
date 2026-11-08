package com.example.helmchatreborn.service

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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.helmchatreborn.MainActivity
import com.example.helmchatreborn.R
import com.example.helmchatreborn.audio.BluetoothAudioRouter
import com.example.helmchatreborn.jitsi.CallState
import com.example.helmchatreborn.jitsi.JitsiCallManager
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

    companion object {
        private const val TAG = "AudioCallService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "helmchat_audio_call_channel"

        const val ACTION_START_CALL = "com.example.helmchatreborn.action.START_CALL"
        const val ACTION_END_CALL = "com.example.helmchatreborn.action.END_CALL"
        const val ACTION_TOGGLE_MUTE = "com.example.helmchatreborn.action.TOGGLE_MUTE"

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
    private var isConnected = false
    private var activeRoomName: String = ""
    private var activeServerUrl: String = JitsiCallManager.DEFAULT_SERVER_URL

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AudioCallService created")
        bluetoothAudioRouter = BluetoothAudioRouter.getInstance(applicationContext)
        jitsiCallManager = JitsiCallManager.getInstance()
        createNotificationChannel()

        jitsiCallManager.callState.onEach { state ->
            when (state) {
                is CallState.Connecting -> {
                    isConnected = false
                    updateNotification()
                }
                is CallState.Connected -> {
                    isConnected = true
                    if (activeRoomName.isBlank()) {
                        activeRoomName = state.roomName
                    }
                    activeServerUrl = state.serverUrl
                    updateNotification()
                }
                is CallState.Error -> {
                    Log.e(TAG, "Call error: ${state.message}")
                    isConnected = false
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
        Log.d(TAG, "AudioCallService destroyed")
        bluetoothAudioRouter.resetForNewCall()
        serviceScope.cancel()
    }

    private fun startCallInternal(roomName: String, serverUrl: String) {
        if (roomName.isBlank()) {
            Log.e(TAG, "Cannot start call with empty room name")
            stopSelf()
            return
        }

        activeRoomName = roomName
        activeServerUrl = serverUrl
        isConnected = false
        callActive = true
        bluetoothAudioRouter.resetForNewCall()

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
        Log.i(TAG, "Ending call")
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
        val status = if (isConnected) {
            getString(R.string.notification_connected)
        } else {
            getString(R.string.notification_connecting)
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
