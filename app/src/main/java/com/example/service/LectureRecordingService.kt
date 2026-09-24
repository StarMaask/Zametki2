package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.preferences.UserPreferencesManager

/**
 * Foreground service to guarantee uninterruptible lecture recording and transcription
 * even when the screen is locked, phone is in pocket, or user switched apps.
 */
class LectureRecordingService : Service() {

    companion object {
        const val CHANNEL_ID = "lecture_recording_channel"
        const val NOTIFICATION_ID = 4001

        const val ACTION_START = "com.example.service.ACTION_START"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"
        const val ACTION_TOGGLE_PAUSE = "com.example.service.ACTION_TOGGLE_PAUSE"

        const val EXTRA_NOTE_TITLE = "extra_note_title"
        const val EXTRA_IS_PAUSED = "extra_is_paused"
        const val EXTRA_DURATION_TEXT = "extra_duration_text"

        // Callback listener so LectureTranscriptionManager can react to notification actions
        var onServiceActionReceived: ((String) -> Unit)? = null
        var isServiceRunning = false
            private set

        fun start(context: Context, noteTitle: String = "Новая заметка") {
            val intent = Intent(context, LectureRecordingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_NOTE_TITLE, noteTitle)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LectureRecordingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun updateStatus(context: Context, isPaused: Boolean, durationText: String, noteTitle: String) {
            if (!isServiceRunning) return
            val intent = Intent(context, LectureRecordingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_NOTE_TITLE, noteTitle)
                putExtra(EXTRA_IS_PAUSED, isPaused)
                putExtra(EXTRA_DURATION_TEXT, durationText)
            }
            context.startService(intent)
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var isBluetoothScoActive = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        createNotificationChannel()
        acquireWakeLock()
        configureBluetoothAudio(enable = true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {
            ACTION_STOP -> {
                onServiceActionReceived?.invoke(ACTION_STOP)
                stopForegroundService()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_PAUSE -> {
                onServiceActionReceived?.invoke(ACTION_TOGGLE_PAUSE)
            }
        }

        val noteTitle = intent?.getStringExtra(EXTRA_NOTE_TITLE) ?: "Запись лекции"
        val isPaused = intent?.getBooleanExtra(EXTRA_IS_PAUSED, false) ?: false
        val durationText = intent?.getStringExtra(EXTRA_DURATION_TEXT) ?: "00:00"

        val notification = buildNotification(noteTitle, isPaused, durationText)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "NotesApp:LectureRecordingWakeLock"
            )?.apply {
                setReferenceCounted(false)
                acquire(120 * 60 * 1000L) // 2 hours safety timeout
            }
        } catch (_: Exception) {}
    }

    private fun configureBluetoothAudio(enable: Boolean) {
        try {
            val prefs = UserPreferencesManager(applicationContext)
            val allowBluetooth = prefs.isBluetoothScoEnabledSync()
            if (!allowBluetooth) return

            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (enable) {
                if (audioManager.isBluetoothScoAvailableOffCall) {
                    audioManager.startBluetoothSco()
                    audioManager.isBluetoothScoOn = true
                    isBluetoothScoActive = true
                }
            } else if (isBluetoothScoActive) {
                audioManager.isBluetoothScoOn = false
                audioManager.stopBluetoothSco()
                isBluetoothScoActive = false
            }
        } catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Запись лекций (Фоновый режим)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Непрерывная запись звука и распознавание речи при выключенном экране"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(noteTitle: String, isPaused: Boolean, durationText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = Intent(this, LectureRecordingService::class.java).apply {
            action = ACTION_TOGGLE_PAUSE
        }
        val pausePendingIntent = PendingIntent.getService(
            this, 1, pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LectureRecordingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = if (isPaused) "Пауза • $durationText" else "Идет запись • $durationText"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🎙️ $noteTitle")
            .setContentText(statusText)
            .setSubText("Звук в текст (Лекция)")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .addAction(
                if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (isPaused) "Продолжить" else "Пауза",
                pausePendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Завершить",
                stopPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun stopForegroundService() {
        isServiceRunning = false
        configureBluetoothAudio(enable = false)
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        wakeLock = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopForegroundService()
        super.onDestroy()
    }
}
