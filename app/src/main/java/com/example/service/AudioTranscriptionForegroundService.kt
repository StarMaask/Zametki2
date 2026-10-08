package com.example.service

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
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.local.NoteDatabase
import com.example.data.preferences.UserPreferencesManager
import com.example.util.AudioChunkerUtil
import com.example.util.GeminiOcrService
import com.example.util.SpeechPostProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/**
 * Foreground service that runs audio-to-text speech transcription in the background.
 *
 * Guarantees that:
 * 1. Transcription continues uninterrupted even if the user minimizes the app,
 *    locks the screen, or switches to other apps.
 * 2. Displays live ongoing progress in the notification tray.
 * 3. Shows a high-priority heads-up completion notification with sound/vibration
 *    when transcription finishes, allowing the user to tap and jump straight into the note.
 * 4. Automatically saves the transcribed text directly to Room DB so data is never lost.
 */
class AudioTranscriptionForegroundService : Service() {

    sealed class TranscriptionEvent {
        data class Progress(val noteId: Long, val statusText: String, val percent: Int = -1) : TranscriptionEvent()
        data class Completed(val noteId: Long, val text: String, val audioPath: String) : TranscriptionEvent()
        data class Error(val noteId: Long, val errorMessage: String) : TranscriptionEvent()
    }

    companion object {
        private const val TAG = "AudioTranscriptionSvc"

        const val CHANNEL_PROGRESS_ID = "channel_audio_transcription_progress"
        const val CHANNEL_COMPLETED_ID = "channel_audio_transcription_complete"

        const val NOTIFICATION_PROGRESS_ID = 5001
        const val NOTIFICATION_COMPLETED_BASE_ID = 6000

        const val ACTION_START = "com.example.service.ACTION_START_TRANSCRIPTION"
        const val ACTION_CANCEL = "com.example.service.ACTION_CANCEL_TRANSCRIPTION"

        const val EXTRA_AUDIO_PATH = "extra_audio_path"
        const val EXTRA_NOTE_ID = "extra_note_id"
        const val EXTRA_NOTE_TITLE = "extra_note_title"
        const val EXTRA_IS_LECTURE_MODE = "extra_is_lecture_mode"

        private val _events = MutableSharedFlow<TranscriptionEvent>(extraBufferCapacity = 64)
        val events = _events.asSharedFlow()

        @Volatile
        var isTranscribing = false
            private set

        @Volatile
        var currentStatus = ""
            private set

        @Volatile
        var currentNoteId: Long = 0L
            private set

        /**
         * Starts background audio-to-text transcription.
         */
        fun start(
            context: Context,
            audioFilePath: String,
            noteId: Long = 0L,
            noteTitle: String = "Заметка",
            isLectureMode: Boolean = false
        ) {
            val intent = Intent(context, AudioTranscriptionForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_AUDIO_PATH, audioFilePath)
                putExtra(EXTRA_NOTE_ID, noteId)
                putExtra(EXTRA_NOTE_TITLE, noteTitle)
                putExtra(EXTRA_IS_LECTURE_MODE, isLectureMode)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Cancels ongoing background transcription.
         */
        fun cancel(context: Context) {
            val intent = Intent(context, AudioTranscriptionForegroundService::class.java).apply {
                action = ACTION_CANCEL
            }
            context.startService(intent)
        }
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)
    private var transcriptionJob: Job? = null
    private var currentProcessingAudioPath: String? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == ACTION_CANCEL) {
            transcriptionJob?.cancel()
            stopForegroundService()
            return START_NOT_STICKY
        }

        val audioPath = intent?.getStringExtra(EXTRA_AUDIO_PATH).orEmpty()
        val noteId = intent?.getLongExtra(EXTRA_NOTE_ID, 0L) ?: 0L
        val noteTitle = intent?.getStringExtra(EXTRA_NOTE_TITLE) ?: "Аудиозапись"
        val isLectureMode = intent?.getBooleanExtra(EXTRA_IS_LECTURE_MODE, false) ?: false

        if (audioPath.isBlank()) {
            stopForegroundService()
            return START_NOT_STICKY
        }

        val audioFile = File(audioPath)
        if (!audioFile.exists() || audioFile.length() == 0L) {
            postErrorNotification(noteId, noteTitle, "Аудиофайл не найден или пуст.")
            stopForegroundService()
            return START_NOT_STICKY
        }

        // If the same audio file is already actively being processed by a running job, avoid restarting it
        if (isTranscribing && currentProcessingAudioPath == audioFile.absolutePath && transcriptionJob?.isActive == true) {
            Log.d(TAG, "Audio transcription already active for: ${audioFile.name}, ignoring duplicate start request")
            return START_NOT_STICKY
        }

        isTranscribing = true
        currentNoteId = noteId
        currentProcessingAudioPath = audioFile.absolutePath
        currentStatus = "Подготовка аудио к обработке ИИ..."

        // Start as foreground service immediately
        val initialNotification = buildProgressNotification(
            noteTitle = noteTitle,
            status = currentStatus,
            percent = -1
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_PROGRESS_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_PROGRESS_ID, initialNotification)
        }

        transcriptionJob?.cancel()
        transcriptionJob = serviceScope.launch {
            executeTranscription(
                audioFile = audioFile,
                noteId = noteId,
                noteTitle = noteTitle,
                isLectureMode = isLectureMode
            )
        }

        return START_NOT_STICKY
    }

    private suspend fun executeTranscription(
        audioFile: File,
        noteId: Long,
        noteTitle: String,
        isLectureMode: Boolean
    ) {
        val prefs = UserPreferencesManager(applicationContext)
        val durationMs = AudioChunkerUtil.getAudioDurationMs(audioFile)
        val durationMinutes = durationMs / (60 * 1000L)

        try {
            val result = GeminiOcrService.transcribeAudioWithGemini(
                context = applicationContext,
                audioFile = audioFile,
                onProgress = { status ->
                    currentStatus = status
                    updateProgressNotification(noteTitle, status)
                    _events.tryEmit(TranscriptionEvent.Progress(noteId, status))
                }
            )

            if (result.isSuccess) {
                val rawText = result.getOrNull().orEmpty().trim()
                if (rawText.isBlank()) {
                    handleTranscriptionFailure(noteId, noteTitle, "ИИ не смог различить речь в этой записи.")
                    return
                }

                // Post-process transcribed text (smart punctuation, Russian dictionary glossary, and anti-loop filter)
                val cleanedText = SpeechPostProcessor.process(
                    text = rawText,
                    enableSmartPunctuation = prefs.isSmartPunctuationSync(),
                    replacements = prefs.getWordReplacementsSync()
                )

                val formattedText = if (isLectureMode && durationMinutes >= 3) {
                    val includeTimestamps = prefs.isTimestampsInLectureSync()
                    if (includeTimestamps) {
                        "[$durationMinutes мин]\n$cleanedText"
                    } else {
                        cleanedText
                    }
                } else {
                    cleanedText
                }

                // 1. Persist directly to Room DB so the note is updated even if user never returns to the app
                saveTranscribedTextToNote(noteId, formattedText, audioFile.absolutePath)

                // 2. Broadcast success event to active UI (e.g. NoteEditorScreen)
                _events.emit(TranscriptionEvent.Completed(noteId, formattedText, audioFile.absolutePath))

                // 3. Post high-priority heads-up completion notification
                postCompletionNotification(noteId, noteTitle, formattedText)
            } else {
                val errorMsg = result.exceptionOrNull()?.localizedMessage ?: "Сбой обработки аудио"
                handleTranscriptionFailure(noteId, noteTitle, errorMsg)
            }
        } catch (c: CancellationException) {
            Log.d(TAG, "Transcription coroutine was cancelled normally")
            // Re-throw so coroutine cancellation machinery functions correctly without surfacing errors
            throw c
        } catch (t: Throwable) {
            Log.e(TAG, "Transcription failed with exception", t)
            val errorMsg = if (t is OutOfMemoryError) {
                "Недостаточно памяти для обработки записи"
            } else {
                t.localizedMessage ?: "Внутренняя ошибка сервиса"
            }
            handleTranscriptionFailure(noteId, noteTitle, errorMsg)
        } finally {
            withContext(Dispatchers.Main) {
                stopForegroundService()
            }
        }
    }

    private suspend fun saveTranscribedTextToNote(noteId: Long, transcribedText: String, audioPath: String) {
        if (noteId <= 0L) return
        try {
            val db = NoteDatabase.getInstance(applicationContext)
            val dao = db.noteDao()
            val existingEntity = dao.getNoteById(noteId)
            if (existingEntity != null) {
                val existingContent = existingEntity.content
                val separator = if (existingContent.isNotBlank() && !existingContent.endsWith("\n")) {
                    "\n\n"
                } else if (existingContent.isNotBlank() && !existingContent.endsWith("\n\n")) {
                    "\n"
                } else {
                    ""
                }
                val newContent = existingContent + separator + transcribedText
                val updatedEntity = existingEntity.copy(
                    content = newContent,
                    audioUri = if (existingEntity.audioUri.isNullOrBlank()) audioPath else existingEntity.audioUri,
                    updatedAt = System.currentTimeMillis()
                )
                dao.updateNote(updatedEntity)
                Log.d(TAG, "Successfully auto-saved transcribed text to note #$noteId in Room DB")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to auto-save note to Room DB", e)
        }
    }

    private suspend fun handleTranscriptionFailure(noteId: Long, noteTitle: String, errorMessage: String) {
        _events.emit(TranscriptionEvent.Error(noteId, errorMessage))
        postErrorNotification(noteId, noteTitle, errorMessage)
    }

    private fun buildProgressNotification(
        noteTitle: String,
        status: String,
        percent: Int
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, AudioTranscriptionForegroundService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPendingIntent = PendingIntent.getService(
            this, 1, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_PROGRESS_ID)
            .setContentTitle("🎙️ $noteTitle")
            .setContentText(status)
            .setSubText("Звук в текст (Фоновый режим)")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Отмена",
                cancelPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)

        if (percent in 0..100) {
            builder.setProgress(100, percent, false)
        } else {
            builder.setProgress(0, 0, true)
        }

        return builder.build()
    }

    private fun updateProgressNotification(noteTitle: String, status: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val percent = parsePercentFromStatus(status)
        val notification = buildProgressNotification(noteTitle, status, percent)
        notificationManager?.notify(NOTIFICATION_PROGRESS_ID, notification)
    }

    private fun parsePercentFromStatus(status: String): Int {
        val match = Regex("(\\d{1,3})%").find(status)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: -1
    }

    private fun postCompletionNotification(noteId: Long, noteTitle: String, fullText: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_NOTE_ID", noteId)
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, (NOTIFICATION_COMPLETED_BASE_ID + (noteId % 1000)).toInt(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snippet = if (fullText.length > 200) {
            fullText.take(200) + "..."
        } else {
            fullText
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_COMPLETED_ID)
            .setContentTitle("✅ Речь расшифрована: $noteTitle")
            .setContentText(snippet)
            .setStyle(NotificationCompat.BigTextStyle().bigText("Текст добавлен в заметку:\n\n$fullText"))
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .build()

        val notificationId = (NOTIFICATION_COMPLETED_BASE_ID + (noteId % 1000)).toInt()
        notificationManager.notify(notificationId, notification)
    }

    private fun postErrorNotification(noteId: Long, noteTitle: String, errorMessage: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_NOTE_ID", noteId)
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, (NOTIFICATION_COMPLETED_BASE_ID + 100).toInt(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_COMPLETED_ID)
            .setContentTitle("⚠️ Ошибка расшифровки: $noteTitle")
            .setContentText(errorMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(errorMessage))
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify((NOTIFICATION_COMPLETED_BASE_ID + 100).toInt(), notification)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return

            // Ongoing Progress Channel (Low importance, silent)
            val progressChannel = NotificationChannel(
                CHANNEL_PROGRESS_ID,
                "Преобразование звука в текст (Прогресс)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Показывает статус распознавания речи в фоновом режиме"
                setShowBadge(false)
            }
            manager.createNotificationChannel(progressChannel)

            // Completion Channel (High importance, sound & heads-up banner)
            val completeChannel = NotificationChannel(
                CHANNEL_COMPLETED_ID,
                "Завершение расшифровки аудио",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Оповещает о готовности расшифрованного текста, когда приложение свернуто"
                enableVibration(true)
                setShowBadge(true)
            }
            manager.createNotificationChannel(completeChannel)
        }
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "NotesApp:AudioTranscriptionWakeLock"
            )?.apply {
                setReferenceCounted(false)
                acquire(30 * 60 * 1000L) // 30 minutes max safety limit
            }
        } catch (_: Exception) {}
    }

    private fun stopForegroundService() {
        isTranscribing = false
        currentStatus = ""
        currentProcessingAudioPath = null
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
        serviceJob.cancel()
        stopForegroundService()
        super.onDestroy()
    }
}
